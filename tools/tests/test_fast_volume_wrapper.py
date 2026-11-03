import os
from pathlib import Path
import stat
import subprocess
import tempfile
import time
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "fast-volume-bridge.sh"


class FastVolumeWrapperTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.device = self.root / "remote"
        self.device.touch()
        self.payload = self.root / "payload.apk"
        self.payload.touch()
        self.env = dict(os.environ, PATH=str(self.bin)+":"+os.environ["PATH"],
                        PLV_FAST_STATE_DIR=str(self.root / "state"),
                        PLV_FAST_PAYLOAD=str(self.payload), PLV_FAST_PROC_ROOT=str(self.root / "proc"),
                        PLV_OLD_BRIDGE=str(self.root / "old.sh"), FAST_TEST_ROOT=str(self.root))
        self.fake("id", "import os; print(os.environ.get('FAST_TEST_UID','2000'))\n")
        self.fake("settings", "raise SystemExit('Settings must not be changed')\n")
        self.fake("ps", """import os,pathlib,sys
assert sys.argv[1]=='-p' and sys.argv[3:]==['-o','ARGS=']
entry=pathlib.Path(os.environ['PLV_FAST_PROC_ROOT'])/sys.argv[2]/'ps-args'
if not entry.exists(): raise SystemExit(1)
print(os.environ.get('FAST_TEST_PS_PREFIX','')+entry.read_text())
""")
        self.fake("app_process", """import os,pathlib,sys,signal,time
root=pathlib.Path(os.environ['FAST_TEST_ROOT'])
with (root/'order.log').open('a') as out: out.write('start\\n')
if os.environ.get('FAST_TEST_FAIL')=='1': raise SystemExit('unsupported runtime')
proc=root/'proc'/str(os.getpid()); proc.mkdir(parents=True)
(proc/'cmdline').write_bytes(b'personal-launcher-volume-fast\\0')
(proc/'ps-args').write_text('personal-launcher-volume-fast')
assert os.environ.get('CLASSPATH')==str(root/'payload.apk')
main=sys.argv.index('com.sadique.launcher.ShellVolumeBridge')
ready=pathlib.Path(sys.argv[main+2])
repeat=int(sys.argv[main+3]); assert 50<=repeat<=1000
(root/'requested-repeat').write_text(str(repeat))
(root/'requested-device').write_text(sys.argv[main+1])
(root/'requested-panel').write_text(sys.argv[main+4])
if os.environ.get('FAST_TEST_PANEL_FAIL')=='1' and sys.argv[main+4]=='launcher': raise SystemExit('controller unavailable')
graceful=ready.parent/'graceful-stop'
if os.environ.get('FAST_TEST_LEGACY')!='1': graceful.write_bytes(b'1')
ready.write_bytes(b'1')
def end(*args):
    if args: (root/'sigterm').write_text('yes')
    ready.unlink(missing_ok=True)
    raise SystemExit(0)
signal.signal(signal.SIGTERM,end)
while True:
    if (ready.parent/'stop-request').exists() and os.environ.get('FAST_TEST_IGNORE_STOP')!='1':
        if os.environ.get('FAST_TEST_RESTORE_FAIL')=='1':
            (ready.parent/'restore-failed').write_text('failed')
        else:
            (root/'controller-restored').write_text('yes')
        graceful.unlink(missing_ok=True)
        end()
    time.sleep(.05)
""")
        (self.root / "old.sh").write_text("""case "$1" in
stop) printf 'stop\\n' >> "$FAST_TEST_ROOT/order.log" ;;
status) printf 'Bridge is not running.\\n' ;;
start) printf 'fallback\\n' >> "$FAST_TEST_ROOT/order.log"; printf '%s\\n' "$PLV_REPEAT_INTERVAL" > "$FAST_TEST_ROOT/fallback-wait" ;;
esac
""")

    def fake(self, name, body):
        path = self.bin / name
        path.write_text("#!/usr/bin/env python3\n"+body)
        path.chmod(0o755)

    def run_wrapper(self, *args):
        return subprocess.run(["sh", str(SCRIPT), *args], env=self.env,
                              capture_output=True, text=True, timeout=16)

    def start(self, repeat=None):
        args=["start",str(self.device)]+([] if repeat is None else [str(repeat)])
        result = self.run_wrapper(*args)
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        self.assertIn("Fast helper running", result.stdout)

    def tearDown(self):
        result=self.run_wrapper("stop")
        if result.returncode != 0:
            # Test stub only: production wrapper deliberately refuses force-kill during cleanup.
            pid_path=self.root/'state/pid'
            if pid_path.exists():
                import signal
                try: os.kill(int(pid_path.read_text()),signal.SIGTERM)
                except ProcessLookupError: pass
                deadline=time.monotonic()+2
                while time.monotonic()<deadline:
                    if (self.root/'sigterm').exists() and not (self.root/'state/ready').exists(): break
                    time.sleep(.01)
        self.temp.cleanup()

    def test_ready_pid_lifecycle_and_old_bridge_order(self):
        self.start()
        self.assertEqual((self.root/'order.log').read_text().splitlines(), ['stop','start'])
        self.assertEqual(stat.S_IMODE(self.payload.stat().st_mode), 0o444)
        self.assertEqual((self.root/'requested-repeat').read_text(),'50')
        self.assertIn("running", self.run_wrapper("status").stdout)
        result=self.run_wrapper("stop")
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertFalse((self.root/'state/pid').exists())
        self.assertFalse((self.root/'state/ready').exists())
        self.assertEqual((self.root/'controller-restored').read_text(),'yes')
        self.assertFalse((self.root/'sigterm').exists())

    def test_duplicate_start_does_not_launch_second_runtime(self):
        self.start()
        self.assertIn("already running",self.run_wrapper("start",str(self.device)).stdout)
        self.assertEqual((self.root/'order.log').read_text().splitlines().count('start'),1)

    def test_unreadable_cmdline_stream_does_not_stall_status_or_stop(self):
        self.start()
        pid=(self.root/'state/pid').read_text().strip()
        cmdline=self.root/'proc'/pid/'cmdline'
        cmdline.unlink()
        os.mkfifo(cmdline)
        self.assertIn('running',self.run_wrapper('status').stdout)
        result=self.run_wrapper('stop')
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertEqual((self.root/'controller-restored').read_text(),'yes')
        self.assertFalse((self.root/'sigterm').exists())

    def test_android_blank_ps_header_does_not_hide_running_helper(self):
        self.env['FAST_TEST_PS_PREFIX']='\n  \t'
        self.start()
        self.assertIn('running',self.run_wrapper('status').stdout)
        self.assertIn('already running',self.run_wrapper('start',str(self.device)).stdout)
        self.assertEqual((self.root/'order.log').read_text().splitlines().count('start'),1)
        result=self.run_wrapper('stop')
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertEqual((self.root/'controller-restored').read_text(),'yes')

    def test_non_shell_start_is_refused(self):
        self.env['FAST_TEST_UID']='10000'
        result=self.run_wrapper("start",str(self.device))
        self.assertNotEqual(result.returncode,0)
        self.assertFalse((self.root/'order.log').exists())

    def test_missing_payload_is_refused(self):
        self.payload.unlink()
        self.assertNotEqual(self.run_wrapper("start",str(self.device)).returncode,0)
        self.assertFalse((self.root/'order.log').exists())

    def test_failed_start_restores_shell_fallback(self):
        self.env['FAST_TEST_FAIL']='1'
        result=self.run_wrapper('start',str(self.device))
        self.assertNotEqual(result.returncode,0)
        self.assertIn('Restoring shell fallback',result.stdout)
        self.assertEqual((self.root/'order.log').read_text().splitlines(),['stop','start','fallback'])
        self.assertEqual((self.root/'fallback-wait').read_text().strip(),'0.050')
        self.assertFalse((self.root/'state/pid').exists())

    def test_unrelated_pid_is_never_signalled(self):
        state=self.root/'state'; state.mkdir()
        (state/'pid').write_text(str(os.getpid()))
        proc=self.root/'proc'/str(os.getpid()); proc.mkdir(parents=True)
        (proc/'cmdline').write_bytes(b'python3\\0unrelated\\0')
        (proc/'ps-args').write_text('python3 unrelated')
        self.assertEqual(self.run_wrapper("stop").returncode,0)

    def test_custom_repeat_is_passed_to_runtime_and_status(self):
        self.start(100)
        self.assertEqual((self.root/'requested-repeat').read_text(),'100')
        self.assertIn('100ms',self.run_wrapper('status').stdout)

    def test_below_minimum_repeat_is_refused_before_old_bridge_changes(self):
        self.assertNotEqual(self.run_wrapper('start',str(self.device),'49').returncode,0)
        self.assertFalse((self.root/'order.log').exists())

    def test_changing_running_repeat_requires_explicit_restart(self):
        self.start()
        self.assertNotEqual(self.run_wrapper('start',str(self.device),'100').returncode,0)
        self.assertIn('50ms',self.run_wrapper('status').stdout)
        self.assertEqual((self.root/'order.log').read_text().splitlines().count('start'),1)

    def test_launcher_panel_option_reaches_runtime_and_status(self):
        result=self.run_wrapper('start',str(self.device),'50','launcher')
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertEqual((self.root/'requested-panel').read_text(),'launcher')
        self.assertIn('launcher',self.run_wrapper('status').stdout)
        self.assertNotEqual(self.run_wrapper('start',str(self.device),'50','system').returncode,0)

    def test_failed_launcher_mode_keeps_fast_repeats_with_stock_panel(self):
        self.env['FAST_TEST_PANEL_FAIL']='1'
        result=self.run_wrapper('start',str(self.device),'50','launcher')
        self.assertNotEqual(result.returncode,0)
        self.assertIn('Keeping fast volume repeats',result.stdout)
        self.assertEqual((self.root/'requested-panel').read_text(),'system')
        self.assertIn('Panel: system',self.run_wrapper('status').stdout)
        self.assertIn('controller unavailable',(self.root/'state/launcher-error.log').read_text())

    def test_invalid_panel_refused_before_stopping_running_helper(self):
        self.assertNotEqual(self.run_wrapper('start',str(self.device),'50','bad').returncode,0)
        self.assertFalse((self.root/'order.log').exists())

    def test_legacy_system_helper_can_still_stop(self):
        self.env['FAST_TEST_LEGACY']='1'
        self.start()
        result=self.run_wrapper('stop')
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertTrue((self.root/'sigterm').exists())

    def test_legacy_launcher_helper_is_not_killed(self):
        self.env['FAST_TEST_LEGACY']='1'
        self.assertEqual(self.run_wrapper('start',str(self.device),'50','launcher').returncode,0)
        result=self.run_wrapper('stop')
        self.assertNotEqual(result.returncode,0)
        self.assertIn('Reboot once',result.stdout)
        self.assertFalse((self.root/'sigterm').exists())

    def test_stalled_cleanup_is_not_force_killed(self):
        self.env['FAST_TEST_IGNORE_STOP']='1'
        self.start()
        result=self.run_wrapper('stop')
        self.assertNotEqual(result.returncode,0)
        self.assertIn('not force-killed',result.stdout)
        self.assertTrue((self.root/'state/stop-request').exists())
        self.assertFalse((self.root/'sigterm').exists())

    def test_failed_controller_restore_is_reported(self):
        self.env['FAST_TEST_RESTORE_FAIL']='1'
        self.start()
        result=self.run_wrapper('stop')
        self.assertNotEqual(result.returncode,0)
        self.assertIn('restoration failed',result.stdout)
        self.assertTrue((self.root/'state/restore-failed').exists())

    def test_auto_device_mode_does_not_require_a_fixed_event_node(self):
        result=self.run_wrapper('start','auto','50','launcher')
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertEqual((self.root/'requested-device').read_text(),'auto')


if __name__ == '__main__': unittest.main()
