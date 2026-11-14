package com.sadique.launcher

import android.view.KeyEvent

/** Number entry must not become a settings shortcut when a remote shares scan codes. */
object SettingsKeyPolicy {

    @JvmStatic
    fun isNumberEntryKey(keyCode: Int): Boolean {
        return (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) ||
                (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) ||
                keyCode == KeyEvent.KEYCODE_PERIOD ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_DOT ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_COMMA
    }

    /** Filter only settings targets; intentional number-to-app shortcuts continue to work. */
    @JvmStatic
    fun safeTarget(keyCode: Int, target: String?): String? {
        if (!isNumberEntryKey(keyCode) || target == null) return target
        if (target == "home_settings" || target == "settings" ||
            target == "com.sadique.launcher/.LauncherSettingsActivity" ||
            target == "com.sadique.launcher/.QuickSettingsActivity" ||
            target == "com.sadique.launcher/com.sadique.launcher.LauncherSettingsActivity" ||
            target == "com.sadique.launcher/com.sadique.launcher.QuickSettingsActivity"
        ) return null
        return target
    }
}
