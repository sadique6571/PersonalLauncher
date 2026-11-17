# Temporary ADB volume hold bridge

This is an optional diagnostic for a TV where the launcher key helpers are required for Home and
Settings, but held volume stops repeating while they run. It is not an APK fix and has not yet been
validated end to end on the physical Haier TV. It uses native volume-key injection, which the user
verified changes both the queried level (16 to 17) and the screen display. Direct AudioService
`--adj` and `--set` commands returned success but left the level unchanged and are no longer used.

The bridge reads only the selected input device and reacts to volume DOWN/UP events. The original tap
remains native (one point); after a 300 ms hold delay, each repeat injects one paired Volume Up/Down
press (`input keyevent 24` or `25`). The loop waits 50 ms between completed calls. A press that lasts
longer than 300 ms can therefore receive an added repeat. Actual speed also depends on the TV's
input-command startup time: 50 ms is the requested wait, not a guarantee of one visible step every
50 ms. Release stops further repeats after any in-flight paired call completes, so a key is
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
a 50 ms step interval. The optional `ShellVolumeBridge` entry point keeps one Android runtime alive,
reads the same getevent frames, and injects native DOWN/UP pairs directly through InputManager.
It requests asynchronous injection and a 50 ms repeat wait after a 300 ms hold threshold. Single taps
shorter than that threshold receive only the firmware's original step. Actual TV timing still needs
measurement; neither an exact 50 ms cadence nor Google Home-equivalent speed is guaranteed.
The user confirmed the initial 10 ms persistent version was too fast; 50 ms is now both the default
and minimum. The repeat wait is configurable between 50 and 1000 ms at startup.

The helper is compiled into the APK as a payload. Do not install or replace the working launcher to
test it: copy the APK to `/data/local/tmp/personal-launcher-volume-fast.apk` and the wrapper to
`/data/local/tmp/fast-volume-bridge.sh`. Run the wrapper through ADB:

```sh
adb shell sh /data/local/tmp/fast-volume-bridge.sh start /dev/input/event3 50
adb shell sh /data/local/tmp/fast-volume-bridge.sh status
adb shell sh /data/local/tmp/fast-volume-bridge.sh stop
```

The wrapper stops the old shell bridge before starting the new helper. It changes no Home/Settings
mapping, accessibility service, app permission or installed package. The APK payload is made read-only
for class loading. The process runs as the already-authorized ADB shell UID, opens no network listener,
has no autostart and ends on stop, Android termination or reboot. Hidden input-method availability is
checked at startup; unsupported firmware fails with an error instead of changing system settings.
If the new helper cannot become ready, the wrapper stops it and restores the older shell bridge
with a 300 ms hold threshold and the selected repeat wait. That fallback still has command startup
overhead. A later runtime failure is reported in the diagnostic log and requires checking the status.
Diagnostics are in `/data/local/tmp/personal-launcher-volume-fast/bridge.log` and contain no raw input.

Once the configurable payload is copied, a timing change needs only a restart, not another APK build:

```sh
adb shell 'sh /data/local/tmp/fast-volume-bridge.sh stop && sh /data/local/tmp/fast-volume-bridge.sh start /dev/input/event3 100'
```

Values below 50 ms are refused before stopping the old bridge. Changing a running helper's interval
also requires an explicit stop/start; status reports the selected wait rather than silently ignoring it.

## Use only the launcher volume panel (optional)

The normal `system` mode is unchanged. On firmware where `adb shell` has
`android.permission.STATUS_BAR_SERVICE`, the `launcher` mode relays the existing
`IVolumeController` instead of disabling SystemUI. It removes only `FLAG_SHOW_UI`
from `STREAM_MUSIC` volume-change callbacks. Other flags, streams and callbacks
(including safe-volume warnings) go to the saved stock controller unchanged.
The installed launcher's enabled accessibility helpers must already show its
right-side slider from actual volume broadcasts. This helper does not install
or start the launcher and does not alter Home, Settings or accessibility settings.

With the updated APK **copied as the classpath payload, not installed**, run:

```sh
adb shell 'sh /data/local/tmp/fast-volume-bridge.sh stop && sh /data/local/tmp/fast-volume-bridge.sh start /dev/input/event3 50 launcher'
```

A missing controller getter, absent controller, denied permission or unsupported
protocol causes startup to fail rather than silently disabling another component.
The wrapper prints the failure and restarts fast repeats in `system` mode; the
working 50ms injector is retained without panel suppression. If even system-mode
startup fails, the slower shell fallback is used. `status` reports the selected panel mode. The wrapper requires an
explicit stop before changing panel mode.

To restore the stock panel while keeping the 50ms volume helper:

```sh
adb shell 'sh /data/local/tmp/fast-volume-bridge.sh stop && sh /data/local/tmp/fast-volume-bridge.sh start /dev/input/event3 50 system'
```

Normal stop/shutdown restores the saved stock controller, unless a newer controller
has registered in the meantime. A SystemUI restart can restore its popup while the
helper runs; restart the helper to adopt the new stock controller. A forced kill
(e.g. `kill -9`) cannot run Java cleanup: reboot the TV to restore stock registration
if needed. The option is session-only and is not configured to restart at boot.
The 50ms repeat, one-point steps and 300ms hold threshold are unchanged.

### Direct Binder updates (6.0 and later)

The 5.9 local-socket channel was denied by firmware on the tested Haier TV:
`LocalSocketImpl.connectLocal: Permission denied`. Do not repeat APK installs or
accessibility toggles to diagnose that transport denial. The existing protected
provider remained reachable with `adb shell content call` (an unknown method
returning `null` verifies provider acquisition, not panel readiness).

Version 6.0 replaces that socket with direct Binder IPC. The shell helper acquires
`com.sadique.launcher.theme` through the same external-provider API used by Android's
`content` command. A versioned `volume_panel_v1` call returns a panel Binder only
while an accessibility helper owns the overlay. Acquisition and every panel
request explicitly enforce shell UID 2000. No manifest permission or exported
component is added, and there is no socket listener. The external provider handle
is released on channel close. If the app dies or its accessibility helper becomes
unavailable, subsequent volume callbacks retain the stock popup flag.

Both the installed launcher APK and the copied helper payload must update to 6.0.
The existing 50ms minimum, one-point step, 300ms hold threshold, Home and Settings
configuration are unchanged. Physical DOWN signals before the hold timer;
AudioService callbacks signal subsequent changes. Pending updates are coalesced
and stale signals dropped. The actual current volume is read and the hidden
window is reused. Queued volume broadcasts remain a fallback.

For on-TV timing evidence:

```sh
adb shell 'logcat -d -v time -s PersonalVolume | tail -n 80'
```

`direct panel UI applied, age=Nms` measures the newest signal's age when UI state
is applied, not a display-frame measurement or the time since the first held-key
press. Real Netflix/app response still requires a physical remote test.

### Stop without losing the stock controller

A SIGTERM sent to Android `app_process` cannot be relied on to run Java shutdown
hooks. The new wrapper writes a `stop-request` marker when a helper advertises
`graceful-stop`. The helper exits its input-reader loop, cancels repeats, restores
the saved stock controller, releases its provider handle and then exits. The
wrapper waits for completion and does not force-kill a stuck cleanup. Restoration
failures are logged and reported as stop failures.

For migration, an old system-mode helper can still be terminated normally. An old
launcher-mode helper without the stop protocol is refused; reboot once rather
than killing the active controller. A forced kill or power loss still cannot run
cleanup; reboot restores stock SystemUI registration. A failed launcher startup
is preserved in `launcher-error.log` even when the fast system-mode fallback
starts and replaces `bridge.log`.

### Combined 6.0 UI and settings fixes

The overlay is a 44dp wide vertical filled track with no surrounding card or thumb.
Fill rises from the bottom, shrinks on volume down, and is empty while media is
muted. The saved media index is not overwritten by muting. Native mute DOWN only
signals the panel: the helper never injects repeated mute toggles. The mute-change
broadcast is also observed, including changes without a volume-index change.

On API 33+, icons come from `getAudioDevicesForAttributes(USAGE_MEDIA)`, not the
paired-device list. Reported wired/USB/BLE headsets have a headphone glyph;
reported BLE speakers have a speaker glyph. A2DP does not identify whether the
remote device is a speaker or headphones, so it uses a generic Bluetooth-audio
glyph without requesting Bluetooth permissions. Unsupported route queries use a
neutral volume glyph. Output icons are cached between updates and device changes
invalidate the route cache.

Search and bell have their own rounded toolbar background. Tile preference
changes explicitly rebind the app grid even when its items are unchanged. Chooser
checks read the saved setting, and their default values match the values used by
the Home views. Theme/font/glass changes also retheme saved settings pages while
retaining focus and scroll position.

The Bluetooth tile launches the verified accessories slice without treating a
failed provider metadata lookup as a missing page. The TV SliceActivity is
protected by WRITE_SECURE_SETTINGS; the launcher already declares this development
permission, which the installation step grants with:

```sh
adb shell pm grant com.sadique.launcher android.permission.WRITE_SECURE_SETTINGS
```

No new permission is added to the launcher. The specific firmware page, mute key,
active-route icons and Netflix response must be verified with the real remote.

### Settings tile layout (6.1)

Settings cards now place the icon beside the label with a 64dp minimum height
and 16dp rounded corners. Quick settings and tools both use two cards per row;
Categories is grouped with Screensaver under tools rather than duplicated.
Theme refresh preserves the card corners and settings focus/scroll behavior.

The Haier user confirmed direct panel updates in 2–7ms, three injected taps
changing media volume from 25 to 28, and held volume working in Netflix after a
controlled helper restart. The precise cause of the earlier lost hold state is
not established. This layout release preserves the existing 50ms injector and
Binder/cleanup behavior; it does not claim to fix that unconfirmed root cause.
The supplied log capture contained no launcher crash/ANR, which is not a claim
that every possible settings stall has been excluded.

### Opt-in reboot recovery (6.2)

`Launcher settings → System → Volume auto-start` shows status and enable/disable
controls. The option is off until one-time local pairing succeeds. It uses the
existing WRITE_SECURE_SETTINGS grant to request wireless debugging ON after boot;
it never runs an off/on toggle. The TV may still require its system network-trust
confirmation. Only a successful real reboot test establishes support on that TV.

One-time setup keeps the TV's **Pair device with pairing code** dialog open. From
an already-authorized ADB client, run the following with the dialog's pairing port
and six-digit code (not its normal connection port):

```sh
adb shell content call --uri content://com.sadique.launcher.theme --method volume_auto_setup_v1 --arg PAIR_PORT:SIX_DIGIT_CODE
```

The provider checks shell UID, starts pairing on a worker, and returns immediately.
The code is not saved or logged. Successful pairing enables auto-start, discovers
this TV's changing connection port via mDNS and starts the helper through loopback.
It does not open a web server, scan port ranges, switch to a fixed TCP port, or
connect to another device. A short-lived loopback-only relay bounds the pairing
library's socket lifetime. The dedicated ADB RSA identity is AES-GCM encrypted by
Android Keystore in no-backup app storage; settings exports exclude it.

Startup is attempted from boot/Home/accessibility connection hooks, with bounded
network/discovery/connection waits and limited retries. It checks the reviewed
wrapper syntax, stops the existing helper gracefully, copies the installed APK
as a read-only payload, and starts `auto 50 launcher`. Automatic input mode keeps
separate getevent frame state per device and handles device removal/reconnection;
manual `/dev/input/eventN` mode remains available. A failed setup retains manual
ADB startup as the fallback. Disabling auto-start skips future automatic attempts
without turning wireless debugging off or stopping the current helper.

Dependencies and rationale are tracked in issue #16: pinned libadb-android,
Conscrypt's public TLS APIs and Bouncy Castle certificate APIs. No hidden-API
bypass is added. Real pairing, changing ports, a full reboot, Netflix volume and
manual stop must be checked on the TV before claiming permanent operation.
