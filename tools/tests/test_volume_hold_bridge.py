import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
import shutil


SCRIPT = Path(__file__).resolve().parents[1] / "volume-hold-bridge.sh"


class VolumeBridgeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.device = self.root / "remote"
        os.mkfifo(self.device)
        self.input = os.open(self.device, os.O_RDWR)
        self.audio = self.root / "audio.log"
        self.settings = self.root / "settings.log"
        self.env = dict(os.environ, PATH=str(self.bin) + ":" + os.environ["PATH"],
                        PLV_STATE_DIR=str(self.root / "state"), PLV_REPEAT_DELAY="0.15",
                        PLV_PROC_ROOT=str(self.root / "proc"),
                        PLV_REPEAT_INTERVAL="0.06", PLV_MAX_HOLD_SECONDS="2",
                        FAKE_AUDIO_LOG=str(self.audio), FAKE_SETTINGS_LOG=str(self.settings),
                        FAKE_INPUT_LOG=str(self.root / "input.log"),
                        FAKE_ADB_LOG=str(self.root / "adb.log"))
        # The managed host exposes outer /proc PIDs inside a nested PID namespace. Supply only
        # our fake process identity records; real signal/lifecycle behaviour is still exercised.
        self.fake("nohup", """import os,sys,signal,pathlib
p=pathlib.Path(os.environ['PLV_PROC_ROOT'])/str(os.getpid())
p.mkdir(parents=True,exist_ok=True)
(p/'cmdline').write_bytes(b'\\0'.join(x.encode() for x in sys.argv[1:])+b'\\0')
signal.signal(signal.SIGHUP,signal.SIG_IGN)
os.execvp(sys.argv[1],sys.argv[1:])
""")
        self.fake("getevent", """import sys,os,pathlib
p=pathlib.Path(os.environ['PLV_PROC_ROOT'])/str(os.getpid())
p.mkdir(parents=True,exist_ok=True)
(p/'cmdline').write_bytes(b'\\0'.join(x.encode() for x in ['getevent']+sys.argv[1:])+b'\\0')
with open(sys.argv[-1],newline='') as stream:
    for line in stream:
        print(line, end='', flush=True)
""")
        self.fake("cmd", "raise SystemExit('Direct AudioService adjustment is ineffective on the TV')\n")
        self.fake("input", """import os,sys,time
assert sys.argv[1] == 'keyevent' and sys.argv[2:] in [['24'],['25']],sys.argv
with open(os.environ['FAKE_INPUT_LOG'],'a') as out: out.write(' '.join(sys.argv[1:])+'\\n')
with open(os.environ['FAKE_AUDIO_LOG'], 'a') as out:
    out.write(''.join(str(time.monotonic()) + ' ' + ('raise' if key=='24' else 'lower') + '\\n' for key in sys.argv[2:]))
if os.environ.get('FAKE_AUDIO_FAIL') == '1': sys.exit(1)
""")
        self.fake("settings", """import os,sys
with open(os.environ['FAKE_SETTINGS_LOG'], 'a') as out:
    out.write(' '.join(sys.argv[1:]) + '\\n')
print(os.environ.get('FAKE_HELPERS', 'com.sadique.launcher/com.sadique.launcher.HomeKeyService'))
""")
        # The actual TV denied mkfifo in /data/local/tmp. The bridge must never call it.
        self.fake("mkfifo", "raise SystemExit('mkfifo: Permission denied')\n")
        self.fake("adb", """import os,sys,pathlib
a=sys.argv[1:]
with open(os.environ['FAKE_ADB_LOG'],'a') as out: out.write(' '.join(a)+'\\n')
if a[:4] == ['shell','-tt','getevent','-lt']:
    p=pathlib.Path(os.environ['PLV_PROC_ROOT'])/str(os.getpid())
    p.mkdir(parents=True,exist_ok=True)
    (p/'cmdline').write_bytes(b'\\0'.join(x.encode() for x in ['adb']+a)+b'\\0')
    with open(a[4],newline='') as source:
        for line in source: print(line,end='',flush=True)
elif a[:3] == ['shell','test','-r']: sys.exit(0 if os.access(a[3],os.R_OK) else 1)
elif a[:2] in [['shell','input'],['shell','settings']]:
    program=str(pathlib.Path(__file__).parent/a[1])
    os.execv(program,[program]+a[2:])
else: raise SystemExit('Unexpected adb arguments: '+repr(a))
""")
        self.run_bridge("start", str(self.device))

    def fake(self, name, body):
        path = self.bin / name
        path.write_text("#!/usr/bin/env python3\n" + body)
        path.chmod(0o755)

    def run_bridge(self, *args, check=True):
        result = subprocess.run(["sh", str(SCRIPT), *args], env=self.env,
                                text=True, capture_output=True, timeout=5)
        if check:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def event(self, key, action):
        # Android getevent pads labels to 20 columns; PTY output can also end in CRLF.
        os.write(self.input, f"[ 123.000] /dev/input/event1: EV_KEY       {key:<20} {action:<20}\r\n".encode())

    def bluetooth(self, usage, action):
        lines = (f"[ 2958.911284] /dev/input/event3: EV_MSC MSC_SCAN {usage}\n"
                 f"[ 2958.911284] /dev/input/event3: EV_KEY KEY_UNKNOWN {action:<20}\n"
                 "[ 2958.911284] /dev/input/event3: EV_SYN SYN_REPORT 00000000\n")
        os.write(self.input, lines.encode())

    def steps(self):
        return self.audio.read_text().splitlines() if self.audio.exists() else []

    def wait_for_steps(self, count=1):
        deadline = time.monotonic() + 2
        while len(self.steps()) < count and time.monotonic() < deadline:
            time.sleep(.02)
        self.assertGreaterEqual(len(self.steps()), count)

    def stop(self):
        self.run_bridge("stop")
        deadline = time.monotonic() + 2
        while (self.root / "state/lock/pid").exists() and time.monotonic() < deadline:
            time.sleep(.02)
        self.assertFalse((self.root / "state/lock/pid").exists())

    def tearDown(self):
        try:
            self.run_bridge("stop", check=False)
            time.sleep(.25)
        finally:
            os.close(self.input)
            self.temp.cleanup()

    def test_short_tap_adds_no_step_and_other_keys_are_ignored(self):
        self.event("KEY_VOLUMEUP", "DOWN")
        time.sleep(.03)
        self.event("KEY_VOLUMEUP", "UP")
        self.event("KEY_HOME", "DOWN")
        time.sleep(.35)
        self.assertEqual(self.steps(), [])

    def test_hold_repeats_and_release_stops_adjustments(self):
        self.event("KEY_VOLUMEUP", "DOWN")
        self.wait_for_steps(3)
        self.event("KEY_VOLUMEUP", "UP")
        time.sleep(.15)
        after_release = self.steps()
        time.sleep(.2)
        self.assertEqual(self.steps(), after_release)
        self.assertTrue(all(step.endswith("raise") for step in after_release))

    def test_duplicate_down_does_not_restart_initial_delay(self):
        self.event("KEY_VOLUMEUP", "DOWN")
        time.sleep(.08)
        self.event("KEY_VOLUMEUP", "DOWN")
        self.wait_for_steps()
        self.assertIn("raise", self.steps()[0])
        self.assertEqual((self.root / "state/bridge.log").read_text().count("held raise"), 1)

    def test_direction_change_and_stale_release(self):
        self.event("KEY_VOLUMEUP", "DOWN")
        self.wait_for_steps()
        self.event("KEY_VOLUMEDOWN", "DOWN")
        self.event("KEY_VOLUMEUP", "UP")
        deadline = time.monotonic() + 2
        while not any(x.endswith("lower") for x in self.steps()) and time.monotonic() < deadline:
            time.sleep(.02)
        self.assertTrue(any(x.endswith("lower") for x in self.steps()))
        self.event("KEY_VOLUMEDOWN", "UP")
        time.sleep(.15)
        count = len(self.steps())
        time.sleep(.2)
        self.assertEqual(len(self.steps()), count)

    def test_native_repeat_and_stop_cancel_added_steps(self):
        self.event("KEY_VOLUMEUP", "DOWN")
        self.wait_for_steps()
        self.event("KEY_VOLUMEUP", "REPEAT")
        time.sleep(.15)
        count = len(self.steps())
        time.sleep(.2)
        self.assertEqual(len(self.steps()), count)
        self.event("KEY_VOLUMEDOWN", "DOWN")
        self.wait_for_steps(count + 1)
        self.stop()
        count = len(self.steps())
        time.sleep(.2)
        self.assertEqual(len(self.steps()), count)

    def test_second_start_is_idempotent_and_settings_are_read_only(self):
        pid = (self.root / "state/lock/pid").read_text()
        self.assertIn("already running", self.run_bridge("start", str(self.device)).stdout)
        self.assertEqual((self.root / "state/lock/pid").read_text(), pid)
        self.assertTrue(all(line == "get secure enabled_accessibility_services"
                            for line in self.settings.read_text().splitlines()))

    def test_disabled_helpers_are_refused(self):
        self.stop()
        self.env["FAKE_HELPERS"] = "null"
        self.assertNotEqual(self.run_bridge("start", str(self.device), check=False).returncode, 0)

    def test_lost_release_stops_at_hold_limit(self):
        self.stop()
        self.env["PLV_MAX_HOLD_SECONDS"] = "1"
        self.run_bridge("start", str(self.device))
        self.event("KEY_VOLUMEUP", "DOWN")
        self.wait_for_steps()
        time.sleep(1.3)
        count = len(self.steps())
        time.sleep(.2)
        self.assertEqual(len(self.steps()), count)

    def test_key_injection_failure_stops_repeating(self):
        self.stop()
        self.env["FAKE_AUDIO_FAIL"] = "1"
        self.run_bridge("start", str(self.device))
        self.event("KEY_VOLUMEUP", "DOWN")
        self.wait_for_steps()
        time.sleep(.3)
        self.assertEqual(len(self.steps()), 1)
        self.assertEqual(len((self.root / "input.log").read_text().splitlines()), 1)
        self.assertIn("Native volume key injection failed", (self.root / "state/bridge.log").read_text())

    def test_host_transport_forces_terminal_and_uses_adb_audio(self):
        self.stop()
        self.run_bridge("start-adb", str(self.device))
        self.event("KEY_VOLUMEUP", "DOWN")
        self.wait_for_steps(2)
        self.event("KEY_VOLUMEUP", "UP")
        time.sleep(.15)
        count = len(self.steps())
        time.sleep(.2)
        self.assertEqual(len(self.steps()), count)
        calls = (self.root / "adb.log").read_text()
        self.assertIn("shell -tt getevent -lt", calls)
        self.assertIn("shell input keyevent 24", calls)
        self.assertIn("running", self.run_bridge("status-adb").stdout)
        self.run_bridge("stop-adb")

    def test_unpadded_labels_also_work(self):
        line = "[ 123.000] /dev/input/event1: EV_KEY KEY_VOLUMEUP DOWN"
        os.write(self.input, (line + "\n").encode())
        self.wait_for_steps()
        self.event("KEY_VOLUMEUP", "UP")

    def test_actual_bluetooth_volume_capture_repeats_and_releases(self):
        self.bluetooth("000c0080", "DOWN")
        self.wait_for_steps(2)
        self.bluetooth("000c0080", "UP")
        time.sleep(.15)
        raised = self.steps()
        self.assertTrue(all(x.endswith("raise") for x in raised))
        time.sleep(.15)
        self.assertEqual(self.steps(), raised)
        self.bluetooth("000c0081", "DOWN")
        self.wait_for_steps(len(raised) + 1)
        self.assertTrue(self.steps()[-1].endswith("lower"))
        self.bluetooth("000c0081", "UP")

    def test_held_repeats_use_one_key_call_in_both_directions(self):
        self.bluetooth("000c0080", "DOWN")
        self.wait_for_steps(3)
        self.bluetooth("000c0080", "UP")
        time.sleep(.15)
        self.bluetooth("000c0081", "DOWN")
        deadline = time.monotonic() + 2
        while not any(s.endswith("lower") for s in self.steps()) and time.monotonic() < deadline:
            time.sleep(.02)
        self.bluetooth("000c0081", "UP")
        time.sleep(.15)
        calls = (self.root / "input.log").read_text().splitlines()
        self.assertIn("keyevent 24", calls)
        self.assertIn("keyevent 25", calls)
        self.assertTrue(all(s in ["keyevent 24", "keyevent 25"] for s in calls))
        self.assertEqual(len(self.steps()), len(calls))

    def test_default_hold_is_300ms_and_repeat_wait_is_50ms(self):
        self.stop()
        self.env.pop("PLV_REPEAT_DELAY")
        self.env.pop("PLV_REPEAT_INTERVAL")
        self.env["REAL_TEST_SLEEP"] = shutil.which("sleep")
        self.env["FAKE_SLEEP_LOG"] = str(self.root / "sleep.log")
        self.fake("sleep", """import os,sys
with open(os.environ['FAKE_SLEEP_LOG'],'a') as out:
    out.write(str(os.getppid())+' '+sys.argv[1]+'\\n')
os.execv(os.environ['REAL_TEST_SLEEP'],['sleep']+sys.argv[1:])
""")
        self.run_bridge("start", str(self.device))
        self.bluetooth("000c0080", "DOWN")
        self.wait_for_steps(2)
        self.bluetooth("000c0080", "UP")
        time.sleep(.2)
        waits = {}
        for line in (self.root / "sleep.log").read_text().splitlines():
            pid, delay = line.split()
            waits.setdefault(pid, []).append(delay)
        repeaters = [values for values in waits.values() if "0.05" in values]
        self.assertEqual(len(repeaters), 1)
        self.assertEqual(repeaters[0][:2], ["0.3", "0.05"])

    def test_unknown_non_volume_keys_and_stale_scan_are_ignored(self):
        self.bluetooth("000c009f", "DOWN")
        self.bluetooth("000c009f", "UP")
        # A scan without a key must not carry beyond its SYN_REPORT frame.
        os.write(self.input, b"EV_MSC MSC_SCAN 000c0080\nEV_SYN SYN_REPORT 00000000\n")
        self.event("KEY_UNKNOWN", "DOWN")
        time.sleep(.3)
        self.assertEqual(self.steps(), [])

    def test_bluetooth_stale_release_does_not_stop_new_direction(self):
        self.bluetooth("000c0080", "DOWN")
        self.wait_for_steps()
        self.bluetooth("000c0081", "DOWN")
        self.bluetooth("000c0080", "UP")
        deadline = time.monotonic() + 2
        while not self.steps()[-1].endswith("lower") and time.monotonic() < deadline:
            time.sleep(.02)
        self.assertTrue(self.steps()[-1].endswith("lower"))
        self.bluetooth("000c0081", "UP")

    def test_unknown_release_without_scan_stops_volume_safely(self):
        self.bluetooth("000c0080", "DOWN")
        self.wait_for_steps()
        self.event("KEY_UNKNOWN", "UP")
        time.sleep(.15)
        count = len(self.steps())
        time.sleep(.2)
        self.assertEqual(len(self.steps()), count)

    def test_other_unknown_release_does_not_cancel_volume_hold(self):
        self.bluetooth("000c0080", "DOWN")
        self.wait_for_steps()
        count = len(self.steps())
        self.bluetooth("000c009f", "UP")
        self.wait_for_steps(count + 2)
        self.bluetooth("000c0080", "UP")

    def test_stale_pid_never_kills_an_unrelated_process(self):
        self.stop()
        lock = self.root / "state/lock"
        lock.mkdir()
        (lock / "pid").write_text(str(os.getpid()))
        proc = self.root / "proc" / str(os.getpid())
        proc.mkdir(exist_ok=True)
        (proc / "cmdline").write_bytes(b"python3\0unrelated-test-process\0")
        self.assertIn("not running", self.run_bridge("stop").stdout)


if __name__ == "__main__":
    unittest.main()
