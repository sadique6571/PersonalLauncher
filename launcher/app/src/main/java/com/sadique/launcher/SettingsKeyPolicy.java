package com.sadique.launcher;

import android.view.KeyEvent;

/** Number entry must not become a settings shortcut when a remote shares scan codes. */
final class SettingsKeyPolicy {
    private SettingsKeyPolicy() { }

    static boolean isNumberEntryKey(int keyCode) {
        return (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
                || (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9)
                || keyCode == KeyEvent.KEYCODE_PERIOD
                || keyCode == KeyEvent.KEYCODE_NUMPAD_DOT
                || keyCode == KeyEvent.KEYCODE_NUMPAD_COMMA;
    }

    /** Filter only settings targets; intentional number-to-app shortcuts continue to work. */
    static String safeTarget(int keyCode, String target) {
        if (!isNumberEntryKey(keyCode) || target == null) return target;
        if (target.equals("home_settings") || target.equals("settings")
                || target.equals("com.sadique.launcher/.LauncherSettingsActivity")
                || target.equals("com.sadique.launcher/.QuickSettingsActivity")
                || target.equals("com.sadique.launcher/com.sadique.launcher.LauncherSettingsActivity")
                || target.equals("com.sadique.launcher/com.sadique.launcher.QuickSettingsActivity")) return null;
        return target;
    }
}
