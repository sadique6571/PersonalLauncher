package com.sadique.launcher;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

/** Inspect media routing, not the paired/connected Bluetooth list. No Bluetooth permission needed. */
final class AudioOutputIcon {
    static int resource(AudioManager audio) {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                for (AudioDeviceInfo device : audio.getAudioDevicesForAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())) {
                    int icon = resourceForType(device.getType());
                    if (icon != R.drawable.ic_volume) return icon;
                }
                return R.drawable.ic_volume;
            } catch (RuntimeException unsupported) { /* Older vendor audio stacks can lack routing queries. */ }
        }
        // Without a trusted active-route answer, use the neutral volume glyph. A connected
        // device list (or wired-plug status) must not be presented as the active media route.
        return R.drawable.ic_volume;
    }
    static int resourceForType(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_HEARING_AID: return R.drawable.ic_volume_headphones;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return R.drawable.ic_volume_bluetooth;
            case AudioDeviceInfo.TYPE_BLE_SPEAKER: return R.drawable.ic_volume_speaker;
            default: return R.drawable.ic_volume;
        }
    }
    private AudioOutputIcon() { }
}
