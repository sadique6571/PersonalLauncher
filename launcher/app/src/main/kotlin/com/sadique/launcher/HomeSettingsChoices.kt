package com.sadique.launcher

import android.content.SharedPreferences

internal object HomeSettingsChoices {
    /** Index of the currently applied option for a settings chooser (-1 when none applies). (User 2026-11-01.) */
    fun currentIndex(
        title: String,
        prefs: SharedPreferences,
        tv: Boolean,
        glassOs: Boolean,
        headerBgMode: () -> Int,
        appLabelSizes: () -> IntArray,
        hasFavoriteWallpaper: () -> Boolean,
        gradientWallpaper: () -> Boolean,
    ): Int {
        return when (title) {
            "s_custom_accent", "Custom accent color" -> if (prefs.getInt("theme_name", 0) != 3) -1 else indexOf(intArrayOf(0xff40d9ff.toInt(), 0xffff9a5c.toInt(), 0xffb388ff.toInt(), 0xff69f0ae.toInt(), 0xffff5252.toInt(), 0xffffea00.toInt(), 0xffff80ab.toInt(), 0xffffffff.toInt(),
                    0xff000000.toInt(), 0xff343b46.toInt(), 0xffcbd3df.toInt(), 0xff508dff.toInt(), 0xff44c6bc.toInt(), 0xffe8c66a.toInt(), 0xffd890a5.toInt(), 0xff7986ed.toInt()), prefs.getInt("custom_accent", 0))
            "s_panel_transparency", "Panel transparency" -> if (PanelTransparency.enabled(prefs)) 0 else 1
            "s_glass_finish", "Glass finish" -> TvGlassStyle.finish(prefs)
            "s_glass_edge", "Glass edge" -> Math.max(0, Math.min(2, prefs.getInt("glass_edge", 1)))
            "s_visual_style", "Visual style" -> ThemeStyle.id(prefs)
            "s_wallpaper_darkness", "Wallpaper darkness" -> Math.min(2, prefs.getInt("background_dim", 0) / 20)
            "s_focus_glow", "Wallpaper focus glow" -> if (prefs.getBoolean("focus_glow", true)) 0 else 1
            "s_header_bg", "Header background" -> if (glassOs) Math.min(1, headerBgMode()) else headerBgMode()
            "s_app_columns", "App columns" -> {
                val v = prefs.getInt(if (tv) "tv_columns" else "phone_columns", 0)
                if (v == 0) 0 else v - 1
            }
            "s_tile_size", "Tile size" -> if (prefs.getBoolean("large_tiles", false)) 1 else 0
            "s_tile_spacing", "Tile spacing" -> prefs.getInt("tile_spacing", 1)
            "s_tile_corners", "Tile corners" -> prefs.getInt("tile_corners", 1)
            "s_logo_size", "Logo size" -> Math.max(0, Math.min(2, prefs.getInt("logo_size", 1)))
            "s_app_label_size", "App label size" -> indexOf(appLabelSizes(), prefs.getInt("app_label_size", if (glassOs) 16 else if (tv) 17 else 14))
            "s_focus_title_size", "Focused app title size" -> indexOf(intArrayOf(24, 32, 40, 48), prefs.getInt("focus_title_size", if (tv) 32 else 26))
            "s_focus_border", "Focus border" -> prefs.getInt("focus_border", 1)
            "s_clock_size", "Clock size" -> indexOf(if (ThemeStyle.custom(prefs)) DemoGeometry.clockSizes(tv) else if (tv) intArrayOf(42, 52, 62) else intArrayOf(36, 44, 52), prefs.getInt("clock_size", if (tv) 52 else 44))
            "s_clock_style", "Clock style" -> prefs.getInt("clock_style", 0)
            "s_watch_face", "Watch face" -> prefs.getInt("harmony_clock_face", 0)
            "s_metal_finish", "Metal finish" -> prefs.getInt("harmony_clock_finish", 0)
            "s_clock_accent", "Clock accent" -> prefs.getInt("harmony_clock_accent", 0)
            "s_second_hand", "Second hand" -> if (prefs.getBoolean("harmony_clock_sweep", true)) 0 else 1
            "s_harmony_clock_size", "Harmony clock size" -> prefs.getInt("harmony_clock_size", 1)
            "s_daily_thoughts", "Daily thoughts" -> if (prefs.getBoolean("harmony_daily_quote", true)) 0 else 1
            "s_clock_font", "Clock font" -> prefs.getInt("clock_font", 0)
            "s_weather_widget", "Home weather widget" -> if (prefs.getBoolean("weather_widget", ThemeStyle.id(prefs) == ThemeStyle.GLASS)) 0 else 1
            "s_widget_animation", "Widget animation" -> if (prefs.getBoolean("weather_widget_animation", true)) 0 else 1
            "s_hijri_adjust", "Hijri day adjustment" -> Math.max(0, Math.min(4, prefs.getInt("hijri_adjustment", 0) + 2))
            "s_time_format", "Time format" -> if (prefs.getBoolean("time_24h", false)) 1 else 0
            "s_clock_align", "Clock alignment" -> prefs.getInt("clock_align", 1)
            "s_clock_seconds", "Clock seconds" -> if (prefs.getBoolean("clock_seconds", false)) 1 else 0
            "s_clock_glow", "Clock glow" -> if (prefs.getBoolean("clock_glow", false)) 1 else 0
            "s_clock_shadow", "Clock shadow" -> if (prefs.getBoolean("clock_shadow", true)) 1 else 0
            "s_hide_clock", "Hide clock" -> if (prefs.getBoolean("hide_clock", false)) 1 else 0
            "s_hide_greeting", "Hide greeting" -> if (prefs.getBoolean("hide_greeting", false)) 1 else 0
            "s_accent_preset", "Accent preset" -> prefs.getInt("theme_name", 0)
            "s_launcher_font", "Launcher font" -> prefs.getInt("app_font", 0)
            "s_glass_intensity", "Glass intensity" -> indexOf(intArrayOf(30, 70, 100), prefs.getInt("glass_alpha", 70))
            "s_wallpaper_source", "Wallpaper source" -> {
                val favorite = hasFavoriteWallpaper()
                if (gradientWallpaper()) if (favorite) 3 else 2
                else if ("custom" == prefs.getString("wallpaper_mode", "mountains")) -1
                else {
                    val source = prefs.getInt("wallpaper_source", 0)
                    if (source == 2 && !favorite) 0 else source
                }
            }
            "s_bottom_shade", "Bottom shade" -> if (prefs.getBoolean("bottom_shade", true)) 1 else 0
            "s_continue_watching", "Continue watching" -> if (prefs.getBoolean("continue_watching", true)) 0 else 1
            "s_avatar_shape", "Avatar shape" -> prefs.getInt("avatar_shape", 0)
            "s_performance", "Performance" -> prefs.getInt("perf_mode", 0)
            "s_home_wallpaper", "Home wallpaper" -> if ("custom" == prefs.getString("wallpaper_mode", "mountains")) 1 else -1
            "s_temp_unit", "Temperature unit" -> if (prefs.getBoolean("weather_fahrenheit", false)) 1 else 0
            "s_idle_start", "Start screensaver after idle" -> indexOf(intArrayOf(0, 2, 5, 10, 15), prefs.getInt("idle_screensaver_minutes", 0))
            else -> -1
        }
    }

    private fun indexOf(values: IntArray, value: Int): Int {
        for (i in values.indices) if (values[i] == value) return i
        return -1
    }
}
