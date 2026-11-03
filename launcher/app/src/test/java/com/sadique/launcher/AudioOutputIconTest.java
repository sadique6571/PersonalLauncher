package com.sadique.launcher;

import android.media.AudioDeviceInfo;
import org.junit.Test;
import static org.junit.Assert.*;

public class AudioOutputIconTest {
    @Test public void reportedHeadphonesAndSpeakersHaveDifferentIcons() {
        assertEquals(R.drawable.ic_volume_headphones, AudioOutputIcon.resourceForType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES));
        assertEquals(R.drawable.ic_volume_headphones, AudioOutputIcon.resourceForType(AudioDeviceInfo.TYPE_BLE_HEADSET));
        assertEquals(R.drawable.ic_volume_speaker, AudioOutputIcon.resourceForType(AudioDeviceInfo.TYPE_BLE_SPEAKER));
        assertNotEquals(AudioOutputIcon.resourceForType(AudioDeviceInfo.TYPE_BLE_HEADSET), AudioOutputIcon.resourceForType(AudioDeviceInfo.TYPE_BLE_SPEAKER));
    }
    @Test public void unknownA2dpCategoryUsesBluetoothIconRatherThanInventedHeadphoneType() {
        assertEquals(R.drawable.ic_volume_bluetooth, AudioOutputIcon.resourceForType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));
        assertEquals(R.drawable.ic_volume, AudioOutputIcon.resourceForType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
    }
}
