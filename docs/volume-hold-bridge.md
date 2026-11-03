# Temporary ADB volume hold bridge

This is an optional diagnostic for a TV where the launcher key helpers are required for Home and
Settings, but held volume stops repeating while they run. It is not an APK fix and has not yet been
validated on the physical Haier TV. It uses the same shell AudioService command verified on that TV.

The bridge reads only the selected input device and reacts to volume DOWN/UP events. The original tap
remains native; a held press adds one AudioService adjustment every 150 ms after a 400 ms delay. Release
stops the loop. A lost release has a 30-second repeat limit. Other keys are ignored and raw input events
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
shell input reads or the AudioService command, stop rather than changing permissions or rooting it.

## Termux / host mode

If background pipe output does not arrive promptly on the TV, run the same script in Termux instead:

```sh
sh volume-hold-bridge.sh start-adb /dev/input/event1
sh volume-hold-bridge.sh status-adb
sh volume-hold-bridge.sh stop-adb
```

Stop any TV-side bridge before starting host mode. This uses `adb shell -tt getevent` to force a
terminal for live event output and uses `adb shell cmd media_session volume` for repeat adjustments.
Keep Termux and the ADB connection running for this test; it is a temporary diagnostic, not a permanent
launcher fix. It requires only the existing adb/shell tools and stores no raw input events.

Host regression tests use fake input/audio commands and never contact a TV:

```sh
python3 -m unittest discover -s tools/tests -p 'test_volume_hold_bridge.py' -v
```
