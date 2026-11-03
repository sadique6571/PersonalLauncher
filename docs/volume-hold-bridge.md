# Temporary ADB volume hold bridge

This is an optional diagnostic for a TV where the launcher key helpers are required for Home and
Settings, but held volume stops repeating while they run. It is not an APK fix and has not yet been
validated end to end on the physical Haier TV. It uses native volume-key injection, which the user
verified changes both the queried level (16 to 17) and the screen display. Direct AudioService
`--adj` and `--set` commands returned success but left the level unchanged and are no longer used.

The bridge reads only the selected input device and reacts to volume DOWN/UP events. The original tap
remains native (one point); after a 300 ms hold delay, each repeat injects one paired Volume Up/Down
press (`input keyevent 24` or `25`). The loop waits 10 ms between completed calls. A press that lasts
longer than 300 ms can therefore receive an added repeat. Actual speed also depends on the TV's
input-command startup time: 10 ms is the requested wait, not a guarantee of one visible step every
10 ms. Release stops further repeats after any in-flight paired call completes, so a key is
not left down mid-pair. A lost release has a 30-second repeat limit. Other keys are ignored and raw input events
are not stored. If hardware repeat events appear, the added loop stops rather than doubling them.
Input is passed through an anonymous shell pipe, so the bridge does not create a named FIFO. This
avoids the `mkfifo: Permission denied` error reported by the TV in `/data/local/tmp`.
The parser accepts the padded labels emitted by Android getevent and terminal CRLF endings.

Keep the original HomeKeyService/InputKeyService setup enabled during this test. Stop the bridge before
changing that setup. This bridge does not suppress the stock or custom volume panels; duplicate OSDs
are a separate unresolved issue. It changes no accessibility setting, app permission, default launcher
or installed package and does not register itself for startup.

Install and start, reviewing the script first if desired:

```sh
adb push tools/volume-hold-bridge.sh /data/local/tmp/volume-hold-bridge.sh
adb shell sh /data/local/tmp/volume-hold-bridge.sh start /dev/input/event1
```

Use the device that actually reports volume keys in `adb shell getevent -lt`. `/dev/input/event1`
was the IR receiver in the user's first test; the current Bluetooth remote reports on
`/dev/input/event3` as `KEY_UNKNOWN` with preceding MSC_SCAN usages `000c0080` (Up) and `000c0081`
(Down), according to the requested press order. Both formats are supported. Device numbering can
change after reboot. Other unknown usages and scans from an earlier SYN_REPORT frame are ignored.

Check or stop it:

```sh
adb shell sh /data/local/tmp/volume-hold-bridge.sh status
adb shell sh /data/local/tmp/volume-hold-bridge.sh stop
```

The shell process remains running until stopped, killed by Android, or the TV reboots. It does not need
a continuously connected ADB client once started. A reboot does not start it again. If a device refuses
shell input reads or native key injection, stop rather than changing permissions or rooting it.

## Termux / host mode

If background pipe output does not arrive promptly on the TV, run the same script in Termux instead:

```sh
sh volume-hold-bridge.sh start-adb /dev/input/event1
sh volume-hold-bridge.sh status-adb
sh volume-hold-bridge.sh stop-adb
```

Stop any TV-side bridge before starting host mode. This uses `adb shell -tt getevent` to force a
terminal for live event output and uses `adb shell input keyevent 24/25` for repeat adjustments.
Keep Termux and the ADB connection running for this test; it is a temporary diagnostic, not a permanent
launcher fix. It requires only the existing adb/shell tools and stores no raw input events.

Host regression tests use fake input/audio commands and never contact a TV:

```sh
python3 -m unittest discover -s tools/tests -p 'test_volume_hold_bridge.py' -v
```

## Single-process injector

The user's TV takes about 230 ms for one `cmd input keyevent`, so changing shell sleeps cannot reach
a 10 ms step interval. The optional `ShellVolumeBridge` entry point keeps one Android runtime alive,
reads the same getevent frames, and injects native DOWN/UP pairs directly through InputManager.
It requests asynchronous injection and a 10 ms repeat wait after a 300 ms hold threshold. Single taps
shorter than that threshold receive only the firmware's original step. Actual TV timing still needs
measurement; neither the 10 ms target nor Google Home-equivalent speed is guaranteed.

The helper is compiled into the APK as a payload. Do not install or replace the working launcher to
test it: copy the APK to `/data/local/tmp/personal-launcher-volume-fast.apk` and the wrapper to
`/data/local/tmp/fast-volume-bridge.sh`. Run the wrapper through ADB:

```sh
adb shell sh /data/local/tmp/fast-volume-bridge.sh start /dev/input/event3
adb shell sh /data/local/tmp/fast-volume-bridge.sh status
adb shell sh /data/local/tmp/fast-volume-bridge.sh stop
```

The wrapper stops the old shell bridge before starting the new helper. It changes no Home/Settings
mapping, accessibility service, app permission or installed package. The APK payload is made read-only
for class loading. The process runs as the already-authorized ADB shell UID, opens no network listener,
has no autostart and ends on stop, Android termination or reboot. Hidden input-method availability is
checked at startup; unsupported firmware fails with an error instead of changing system settings.
If the new helper cannot become ready, the wrapper stops it and restores the older shell bridge
with a 300 ms hold threshold and 10 ms requested repeat wait. That fallback still has command startup
overhead. A later runtime failure is reported in the diagnostic log and requires checking the status.
Diagnostics are in `/data/local/tmp/personal-launcher-volume-fast/bridge.log` and contain no raw input.
