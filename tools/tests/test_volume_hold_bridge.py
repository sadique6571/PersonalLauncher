import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest


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
                        FAKE_AUDIO_LOG=str(self.audio), FAKE_SETTINGS_LOG=str(self.settings))
        # The managed host exposes outer /proc PIDs inside a nested PID namespace. Supply only
        # our fake process identity records; real signal/lifecycle behaviour is still exercised.
        self.fake("nohup", """import os,sys,signal,pathlib
p=pathlib.Path(os.environ['PLV_PROC_ROOT'])/str(os.getpid())
p.mkdir(parents=True,exist_ok=True)
(p/'cmdline').write_bytes(b'\\0'.join(x.encode() for x in sys.argv[1:])+b'\\0')
signal.signal(signal.SIGHUP,signal.SIG_IGN)
os.execvp(sys.argv[1],sys.argv[1:])
""")
        self.fake("getevent", """import sys
with open(sys.argv[-1]) as stream:
    for line in stream:
        print(line, end='', flush=True)
""")
        self.fake("cmd", """import os,sys,time
with open(os.environ['FAKE_AUDIO_LOG'], 'a') as out:
    out.write(str(time.monotonic()) + ' ' + sys.argv[-1] + '\\n')
if os.environ.get('FAKE_AUDIO_FAIL') == '1': sys.exit(1)
""")
        self.fake("settings", """import os,sys
with open(os.environ['FAKE_SETTINGS_LOG'], 'a') as out:
    out.write(' '.join(sys.argv[1:]) + '\\n')
print(os.environ.get('FAKE_HELPERS', 'com.sadique.launcher/com.sadique.launcher.HomeKeyService'))
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
        os.write(self.input, f"[ 123.000] /dev/input/event1: EV_KEY {key} {action}\n".encode())

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

    def test_audio_service_failure_stops_repeating(self):
        self.stop()
        self.env["FAKE_AUDIO_FAIL"] = "1"
        self.run_bridge("start", str(self.device))
        self.event("KEY_VOLUMEUP", "DOWN")
        self.wait_for_steps()
        time.sleep(.3)
        self.assertEqual(len(self.steps()), 1)
        self.assertIn("AudioService adjustment failed", (self.root / "state/bridge.log").read_text())

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
