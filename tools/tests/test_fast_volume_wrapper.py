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
        self.fake("app_process", """import os,pathlib,sys,signal,time
root=pathlib.Path(os.environ['FAST_TEST_ROOT'])
with (root/'order.log').open('a') as out: out.write('start\\n')
if os.environ.get('FAST_TEST_FAIL')=='1': raise SystemExit('unsupported runtime')
proc=root/'proc'/str(os.getpid()); proc.mkdir(parents=True)
(proc/'cmdline').write_bytes(b'personal-launcher-volume-fast\\0')
assert os.environ.get('CLASSPATH')==str(root/'payload.apk')
ready=pathlib.Path(sys.argv[-1]); ready.write_bytes(b'1')
def end(*args):
    ready.unlink(missing_ok=True)
    raise SystemExit(0)
signal.signal(signal.SIGTERM,end)
while True: time.sleep(.05)
""")
        (self.root / "old.sh").write_text("""case "$1" in
stop) printf 'stop\\n' >> "$FAST_TEST_ROOT/order.log" ;;
status) printf 'Bridge is not running.\\n' ;;
start) printf 'fallback\\n' >> "$FAST_TEST_ROOT/order.log" ;;
esac
""")

    def fake(self, name, body):
        path = self.bin / name
        path.write_text("#!/usr/bin/env python3\n"+body)
        path.chmod(0o755)

    def run_wrapper(self, *args):
        return subprocess.run(["sh", str(SCRIPT), *args], env=self.env,
                              capture_output=True, text=True, timeout=10)

    def start(self):
        result = self.run_wrapper("start", str(self.device))
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        self.assertIn("Fast helper running", result.stdout)

    def tearDown(self):
        self.run_wrapper("stop")
        self.temp.cleanup()

    def test_ready_pid_lifecycle_and_old_bridge_order(self):
        self.start()
        self.assertEqual((self.root/'order.log').read_text().splitlines(), ['stop','start'])
        self.assertEqual(stat.S_IMODE(self.payload.stat().st_mode), 0o444)
        self.assertIn("running", self.run_wrapper("status").stdout)
        result=self.run_wrapper("stop")
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertFalse((self.root/'state/pid').exists())
        self.assertFalse((self.root/'state/ready').exists())

    def test_duplicate_start_does_not_launch_second_runtime(self):
        self.start()
        self.assertIn("already running",self.run_wrapper("start",str(self.device)).stdout)
        self.assertEqual((self.root/'order.log').read_text().splitlines().count('start'),1)

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
        self.assertFalse((self.root/'state/pid').exists())

    def test_unrelated_pid_is_never_signalled(self):
        state=self.root/'state'; state.mkdir()
        (state/'pid').write_text(str(os.getpid()))
        proc=self.root/'proc'/str(os.getpid()); proc.mkdir(parents=True)
        (proc/'cmdline').write_bytes(b'python3\\0unrelated\\0')
        self.assertEqual(self.run_wrapper("stop").returncode,0)


if __name__ == '__main__': unittest.main()
