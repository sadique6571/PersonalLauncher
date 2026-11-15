package com.sadique.launcher

import android.content.SharedPreferences

/** One-time Home preference defaults and migrations. Pure move from PersonalHomeActivity. */
internal object HomeThemeDefaults {

    /** Glass/Harmony first-run defaults; previously saved styles are untouched. */
    fun glassDefaults(prefs: SharedPreferences) {
        if (ThemeStyle.id(prefs) == ThemeStyle.GLASS && !prefs.getBoolean("glass_os_defaults_v1", false))
            prefs.edit().putBoolean("glass_os_defaults_v1", true).putString("wallpaper_mode", "solid")
                .putInt("wallpaper_source", 0).putInt("header_bg_mode", 0).putBoolean("header_glass_v2", true).putInt("clock_size", 82)
                .putBoolean("focus_glow", true).putInt("ambient_strength", 100).apply()
        if (ThemeStyle.id(prefs) == ThemeStyle.GLASS && !prefs.getBoolean("glass_resume_v3", false))
            prefs.edit().putBoolean("glass_resume_v3", true).putBoolean("continue_watching", true).apply()
        if (ThemeStyle.id(prefs) == ThemeStyle.GLASS && !prefs.getBoolean("glass_reference_v2", false))
            prefs.edit().putBoolean("glass_reference_v2", true).putInt("header_bg_mode", 1).putBoolean("header_glass_v2", true).apply()
        // Dedicated opt-in profile: no change to previously saved styles or TV services.
        if (ThemeStyle.harmony(prefs) && !prefs.getBoolean("harmony_defaults_v1", false))
            prefs.edit().putBoolean("harmony_defaults_v1", true)
                .putString("wallpaper_mode", "mountains").putInt("wallpaper_source", 1)
                .putInt("background_dim", 16).putInt("header_bg_mode", 0).putBoolean("header_glass_v2", true)
                .putInt("clock_size", 88).putInt("clock_align", 0).putInt("clock_style", 0)
                .putBoolean("weather_widget", true).putBoolean("clock_seconds", false)
                .putBoolean("clock_shadow", true).putBoolean("focus_glow", false)
                .putInt("tv_columns", 4).putInt("tile_corners", 2).putInt("tile_spacing", 1)
                .putInt("glass_alpha", 58).putInt("glass_finish", 0).putInt("glass_edge", 1)
                .putBoolean("panel_transparency", true).apply()
    }

    /** One-time preference migrations applied at Home startup. */
    fun migrate(prefs: SharedPreferences) {
        if (!prefs.getBoolean("bing_daily_home_v67", false)) {
            prefs.edit().putBoolean("bing_daily_home_v67", true)
                    .putInt("wallpaper_source", 1).putString("wallpaper_mode", "mountains").apply()
        }
        if (!prefs.getBoolean("reference_layout_v42", false)) {
            prefs.edit().putBoolean("reference_layout_v42", true).putString("theme_mode", "dark")
                    .apply()
        }
        if (!prefs.getBoolean("reference_layout_v43", false)) {
            prefs.edit().putBoolean("reference_layout_v43", true).putInt("background_dim", 0).apply()
        }
    }
}
