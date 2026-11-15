package com.sadique.launcher

import android.view.KeyEvent
import java.util.Arrays
import org.junit.Test
import org.junit.Assert.*

class SettingsKeyPolicyTest {
    private fun numberKeys(): IntArray {
        val keys = IntArray(23)
        for (i in 0 until 10) {
            keys[i] = KeyEvent.KEYCODE_0 + i
            keys[10 + i] = KeyEvent.KEYCODE_NUMPAD_0 + i
        }
        keys[20] = KeyEvent.KEYCODE_PERIOD
        keys[21] = KeyEvent.KEYCODE_NUMPAD_DOT
        keys[22] = KeyEvent.KEYCODE_NUMPAD_COMMA
        return keys
    }

    @Test fun everyDigitAndDecimalIsNumberEntry() {
        for (key in numberKeys()) assertTrue(SettingsKeyPolicy.isNumberEntryKey(key))
    }

    @Test fun numberKeysCannotOpenSettingsEvenWithSharedScan240() {
        val scans: Map<Int, String> = RemoteKeys.parseAppScancodes(Arrays.asList("scan:240:home_settings"))
        for (key in numberKeys()) {
            assertNull(SettingsKeyPolicy.safeTarget(key, scans[240]))
            assertNull(SettingsKeyPolicy.safeTarget(key, "settings"))
        }
        assertEquals("home_settings", SettingsKeyPolicy.safeTarget(KeyEvent.KEYCODE_SETTINGS, scans[240]))
    }

    @Test fun migratedAndLegacyComponentMappingsAreProtected() {
        val targets = arrayOf("com.sadique.launcher/.LauncherSettingsActivity",
                "com.sadique.launcher/.QuickSettingsActivity",
                "com.sadique.launcher/com.sadique.launcher.LauncherSettingsActivity",
                "com.sadique.launcher/com.sadique.launcher.QuickSettingsActivity")
        for (target in targets) {
            for (key in numberKeys()) assertNull(SettingsKeyPolicy.safeTarget(key, target))
        }
        val migrated = RemoteKeys.parseAppScancodes(RemoteKeys.migrateScanSettingsKeys(
                Arrays.asList("scan:240:com.sadique.launcher/.LauncherSettingsActivity")))[240]
        assertNull(SettingsKeyPolicy.safeTarget(KeyEvent.KEYCODE_1, migrated))
    }

    @Test fun holdAndFrontSettingsMappingsDoNotSwallowNumbers() {
        val holds: Map<Int, String> = RemoteKeys.parseHoldAppScancodes(Arrays.asList("hold:scan:240:home_settings"))
        val fronts: Map<Int, String> = RemoteKeys.parseFrontAppScancodes(Arrays.asList("front:scan:240:home_settings"))
        for (key in numberKeys()) {
            assertNull(SettingsKeyPolicy.safeTarget(key, holds[240]))
            assertNull(SettingsKeyPolicy.safeTarget(key, fronts[240]))
        }
    }

    @Test fun numberToAppAndOtherActionsArePreserved() {
        for (key in numberKeys()) {
            assertEquals("com.netflix.ninja", SettingsKeyPolicy.safeTarget(key, "com.netflix.ninja"))
            assertEquals("inputs", SettingsKeyPolicy.safeTarget(key, "inputs"))
            assertEquals("ignore", SettingsKeyPolicy.safeTarget(key, "ignore"))
            assertNull(SettingsKeyPolicy.safeTarget(key, null))
        }
    }

    @Test fun settingsHomeDpadAndUnknownAreNotTreatedAsNumbers() {
        val keys = intArrayOf(KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_UNKNOWN)
        for (key in keys) {
            assertFalse(SettingsKeyPolicy.isNumberEntryKey(key))
            assertEquals("home_settings", SettingsKeyPolicy.safeTarget(key, "home_settings"))
        }
    }
}
