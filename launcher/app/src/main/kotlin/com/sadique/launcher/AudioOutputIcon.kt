package com.sadique.launcher

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Inspect media routing, not the paired/connected Bluetooth list. No Bluetooth permission needed. */
object AudioOutputIcon {

    @JvmStatic
    fun resource(audio: AudioManager): Int {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
                val devices = audio.getAudioDevicesForAttributes(attributes)
                for (device in devices) {
                    val icon = resourceForType(device.type)
                    if (icon != R.drawable.ic_volume) return icon
                }
                return R.drawable.ic_volume
            } catch (_: RuntimeException) {
                /* Older vendor audio stacks can lack routing queries. */
            }
        }
        return R.drawable.ic_volume
    }

    @JvmStatic
    fun resourceForType(type: Int): Int {
        return when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID -> R.drawable.ic_volume_headphones
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> R.drawable.ic_volume_bluetooth
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> R.drawable.ic_volume_speaker
            else -> R.drawable.ic_volume
        }
    }
}
