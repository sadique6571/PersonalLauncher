package com.sadique.launcher;

import android.content.SharedPreferences;

/** Disabling panel transparency never changes the drawer's stored glass settings. */
final class PanelTransparency {
    private PanelTransparency() { }
    static boolean enabled(SharedPreferences prefs) { return prefs.getBoolean("panel_transparency",true); }
    static int alpha(int glassAlpha,SharedPreferences prefs,boolean drawer) {
        return drawer || enabled(prefs) ? glassAlpha : 255;
    }
}
