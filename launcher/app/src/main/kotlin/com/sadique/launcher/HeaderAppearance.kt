package com.sadique.launcher

import android.content.SharedPreferences

/** Restore the missing header glass once, then honor the visible Off/Glass/Frosted chooser. */
object HeaderAppearance {

    /** Keep header ink readable even over the opposite-colour wallpaper. */
    @JvmStatic
    fun alpha(requested: Int): Int = Math.max(224, Math.min(255, requested))

    @JvmStatic
    fun foreground(prefs: SharedPreferences): Int {
        return if (mode(prefs) == 0) 0xffffffff.toInt() else ThemeStyle.foreground(prefs)
    }

    @JvmStatic
    fun clockDefaults(prefs: SharedPreferences) {
        if (!prefs.getBoolean("clock_wallpaper_v2", false)) {
            prefs.edit().putBoolean("clock_wallpaper_v2", true).putBoolean("clock_shadow", true).apply()
        }
    }

    @JvmStatic
    fun mode(prefs: SharedPreferences): Int {
        if (!prefs.getBoolean("header_glass_v2", false)) {
            val legacy = prefs.getInt("header_bg_mode", -1)
            val initial = if (legacy == 2 || prefs.getBoolean("header_blur", false)) 2 else 1
            prefs.edit().putInt("header_bg_mode", initial).putBoolean("header_glass_v2", true).apply()
        }
        return Math.max(0, Math.min(2, prefs.getInt("header_bg_mode", 1)))
    }
}
