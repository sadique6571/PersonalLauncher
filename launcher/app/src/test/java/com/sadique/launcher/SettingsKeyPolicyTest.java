package com.sadique.launcher;

import android.view.KeyEvent;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsKeyPolicyTest {
    private static int[] numberKeys() {
        int[] keys = new int[23];
        for (int i = 0; i < 10; i++) {
            keys[i] = KeyEvent.KEYCODE_0 + i;
            keys[10 + i] = KeyEvent.KEYCODE_NUMPAD_0 + i;
        }
        keys[20] = KeyEvent.KEYCODE_PERIOD;
        keys[21] = KeyEvent.KEYCODE_NUMPAD_DOT;
        keys[22] = KeyEvent.KEYCODE_NUMPAD_COMMA;
        return keys;
    }

    @Test public void everyDigitAndDecimalIsNumberEntry() {
        for (int key : numberKeys()) assertTrue(SettingsKeyPolicy.isNumberEntryKey(key));
    }

    @Test public void numberKeysCannotOpenSettingsEvenWithSharedScan240() {
        Map<Integer, String> scans = RemoteKeys.parseAppScancodes(Arrays.asList("scan:240:home_settings"));
        for (int key : numberKeys()) {
            assertNull(SettingsKeyPolicy.safeTarget(key, scans.get(240)));
            assertNull(SettingsKeyPolicy.safeTarget(key, "settings"));
        }
        assertEquals("home_settings", SettingsKeyPolicy.safeTarget(KeyEvent.KEYCODE_SETTINGS, scans.get(240)));
    }

    @Test public void migratedAndLegacyComponentMappingsAreProtected() {
        String[] targets = {"com.sadique.launcher/.LauncherSettingsActivity",
                "com.sadique.launcher/.QuickSettingsActivity",
                "com.sadique.launcher/com.sadique.launcher.LauncherSettingsActivity",
                "com.sadique.launcher/com.sadique.launcher.QuickSettingsActivity"};
        for (String target : targets) {
            for (int key : numberKeys()) assertNull(SettingsKeyPolicy.safeTarget(key, target));
        }
        String migrated = RemoteKeys.parseAppScancodes(RemoteKeys.migrateScanSettingsKeys(
                Arrays.asList("scan:240:com.sadique.launcher/.LauncherSettingsActivity"))).get(240);
        assertNull(SettingsKeyPolicy.safeTarget(KeyEvent.KEYCODE_1, migrated));
    }

    @Test public void holdAndFrontSettingsMappingsDoNotSwallowNumbers() {
        Map<Integer, String> holds = RemoteKeys.parseHoldAppScancodes(Arrays.asList("hold:scan:240:home_settings"));
        Map<Integer, String> fronts = RemoteKeys.parseFrontAppScancodes(Arrays.asList("front:scan:240:home_settings"));
        for (int key : numberKeys()) {
            assertNull(SettingsKeyPolicy.safeTarget(key, holds.get(240)));
            assertNull(SettingsKeyPolicy.safeTarget(key, fronts.get(240)));
        }
    }

    @Test public void numberToAppAndOtherActionsArePreserved() {
        for (int key : numberKeys()) {
            assertEquals("com.netflix.ninja", SettingsKeyPolicy.safeTarget(key, "com.netflix.ninja"));
            assertEquals("inputs", SettingsKeyPolicy.safeTarget(key, "inputs"));
            assertEquals("ignore", SettingsKeyPolicy.safeTarget(key, "ignore"));
            assertNull(SettingsKeyPolicy.safeTarget(key, null));
        }
    }

    @Test public void settingsHomeDpadAndUnknownAreNotTreatedAsNumbers() {
        int[] keys = {KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_UNKNOWN};
        for (int key : keys) {
            assertFalse(SettingsKeyPolicy.isNumberEntryKey(key));
            assertEquals("home_settings", SettingsKeyPolicy.safeTarget(key, "home_settings"));
        }
    }
}
