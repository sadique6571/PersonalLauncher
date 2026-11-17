# Volume hold helper

Use the optional volume helper on a TV where holding Volume Up or Down stops repeating while the launcher key helpers are active. Leave the working Home and Settings key setup in place. The helper supports firmware that lets the authorized ADB shell read the remote's input events and inject volume key presses.

## Fast helper

Download the [current release APK](https://github.com/sadique6571/PersonalLauncher/releases/latest/download/PersonalLauncher.apk) as `PersonalLauncher.apk`. From the repository root, copy it to the TV as a payload for the helper and copy the wrapper from the same release source. The copy in `/data/local/tmp` is used to run the helper. It does not install the app.

```sh
adb push PersonalLauncher.apk /data/local/tmp/personal-launcher-volume-fast.apk
adb push tools/fast-volume-bridge.sh /data/local/tmp/fast-volume-bridge.sh
adb shell sh /data/local/tmp/fast-volume-bridge.sh start auto 50 system
```

`auto` monitors eligible input devices. To select a device manually, press the volume keys while running `adb shell getevent -lt` and replace `auto` with the relevant `/dev/input/eventN` path. Device numbers can change after reboot. The helper recognizes labeled volume keys and supported Bluetooth volume scan codes.

A normal tap uses the TV's own volume handling. After a 300 ms hold the helper injects one paired volume press per repeat. The repeat wait is configurable from 50 to 1000 ms. Actual speed depends on how quickly the TV handles those presses. Release stops future repeats after any press already in flight.

```sh
adb shell sh /data/local/tmp/fast-volume-bridge.sh status
adb shell sh /data/local/tmp/fast-volume-bridge.sh stop
```

Stop the helper before changing its repeat wait or panel mode. The manually started helper ends on reboot unless automatic startup has been configured separately.

## Volume display

`system` keeps the TV's normal volume controller. To try the launcher's volume panel on supported firmware, stop the helper and use `start auto 50 launcher`. That mode requires an installed launcher exposing its volume panel through an active accessibility helper, plus firmware support for the shell to register a controller. Use matching launcher and payload builds.

The controller forwards non-media events and other flags to the saved controller. It can suppress the stock media popup while the launcher panel is available. If launcher-panel startup fails, the wrapper retries in system mode. If the optional shell-only bridge is installed and fast startup also fails, the wrapper can use the slower bridge with a readable input device.

Use the wrapper's **stop** command to let the helper restore the previous controller. If cleanup fails, reboot the TV before trying launcher-panel mode again. Diagnostics are in `/data/local/tmp/personal-launcher-volume-fast/bridge.log` and `launcher-error.log`. The launcher also logs panel updates under `PersonalVolume`.

## Optional automatic startup

On Android 11 or later, the **Home & remote setup → Local pairing and volume auto-start** panel shows setup and status. The **Set as default** prompt on Home opens **Home & remote setup** when the launcher offers to become the default Home. Pairing is done on this TV using wireless debugging. From an authorized ADB session, grant the launcher the settings permission if it has not already been granted.

```sh
adb shell pm grant com.sadique.launcher android.permission.WRITE_SECURE_SETTINGS
```

With the TV's **Pair device with pairing code** dialog open, use an already-authorized ADB connection to the TV. Replace the placeholders with the dialog's *pairing* port and six-digit code:

```sh
adb shell content call --uri content://com.sadique.launcher.theme --method volume_auto_setup_v1 --arg PAIR_PORT:SIX_DIGIT_CODE
adb shell content call --uri content://com.sadique.launcher.theme --method volume_auto_status_v1
```

Successful pairing enables automatic volume startup. The launcher uses the `WRITE_SECURE_SETTINGS` grant to request wireless debugging when it needs to restore the helper. The TV can still require its network-trust confirmation. The launcher discovers the changing connection port and starts the helper locally with `auto 50 launcher`. The pairing code is used for setup and the reusable ADB identity is stored encrypted on the TV.

**Disable auto-start** skips future automatic starts and leaves the running helper in place. To stop a running helper too, use the wrapper's stop command. Reboot once and check status on the target TV to verify automatic startup on that firmware.

## Shell-only fallback

For diagnostics, copy the [shell bridge](../tools/volume-hold-bridge.sh) and start it with a readable volume input device in place of `eventN`. This version invokes Android's `input keyevent` for every repeat and can be slower than the fast helper.

```sh
adb push tools/volume-hold-bridge.sh /data/local/tmp/volume-hold-bridge.sh
adb shell sh /data/local/tmp/volume-hold-bridge.sh start /dev/input/eventN
adb shell sh /data/local/tmp/volume-hold-bridge.sh status
adb shell sh /data/local/tmp/volume-hold-bridge.sh stop
```

If TV-side event output stalls, stop the TV-side bridge and use the same script on the ADB host with `start-adb /dev/input/eventN`, `status-adb`, and `stop-adb`. Keep that host and the ADB connection running while using host mode.
