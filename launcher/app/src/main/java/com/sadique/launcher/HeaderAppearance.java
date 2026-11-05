package com.sadique.launcher;

import android.content.SharedPreferences;

/** Restore the missing header glass once, then honor the visible Off/Glass/Frosted chooser. */
final class HeaderAppearance {
    private HeaderAppearance() { }
    static int mode(SharedPreferences prefs) {
        if (!prefs.getBoolean("header_glass_v2",false)) {
            int legacy=prefs.getInt("header_bg_mode",-1);
            int initial=legacy==2 || prefs.getBoolean("header_blur",false) ? 2 : 1;
            prefs.edit().putInt("header_bg_mode",initial).putBoolean("header_glass_v2",true).apply();
        }
        return Math.max(0,Math.min(2,prefs.getInt("header_bg_mode",1)));
    }
}
