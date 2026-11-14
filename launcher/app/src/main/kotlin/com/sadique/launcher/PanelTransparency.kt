package com.sadique.launcher

import android.content.SharedPreferences

/** Disabling panel transparency never changes the drawer's stored glass settings. */
object PanelTransparency {
    @JvmStatic
    fun enabled(prefs: SharedPreferences): Boolean = prefs.getBoolean("panel_transparency", true)

    @JvmStatic
    fun alpha(glassAlpha: Int, prefs: SharedPreferences, drawer: Boolean): Int {
        return if (drawer || enabled(prefs)) glassAlpha else 255
    }
}
