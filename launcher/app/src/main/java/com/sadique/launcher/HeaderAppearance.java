package com.sadique.launcher;

import android.content.SharedPreferences;

/** Restore the missing header glass once, then honor the visible Off/Glass/Frosted chooser. */
final class HeaderAppearance {
    private HeaderAppearance() { }
    /** Keep header ink readable even over the opposite-colour wallpaper. */
    static int alpha(int requested) { return Math.max(224,Math.min(255,requested)); }
    static int foreground(SharedPreferences prefs) {
        return mode(prefs)==0?0xffffffff:ThemeStyle.foreground(prefs);
    }
    static void clockDefaults(SharedPreferences prefs) {
        if(!prefs.getBoolean("clock_wallpaper_v2",false))
            prefs.edit().putBoolean("clock_wallpaper_v2",true).putBoolean("clock_shadow",true).apply();
    }
    static int mode(SharedPreferences prefs) {
        if (!prefs.getBoolean("header_glass_v2",false)) {
            int legacy=prefs.getInt("header_bg_mode",-1);
            int initial=legacy==2 || prefs.getBoolean("header_blur",false) ? 2 : 1;
            prefs.edit().putInt("header_bg_mode",initial).putBoolean("header_glass_v2",true).apply();
        }
        return Math.max(0,Math.min(2,prefs.getInt("header_bg_mode",1)));
    }
}
