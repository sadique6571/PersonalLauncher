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
            "Custom accent color" -> if (prefs.getInt("theme_name", 0) != 3) -1 else indexOf(intArrayOf(0xff40d9ff.toInt(), 0xffff9a5c.toInt(), 0xffb388ff.toInt(), 0xff69f0ae.toInt(), 0xffff5252.toInt(), 0xffffea00.toInt(), 0xffff80ab.toInt(), 0xffffffff.toInt(),
                    0xff000000.toInt(), 0xff343b46.toInt(), 0xffcbd3df.toInt(), 0xff508dff.toInt(), 0xff44c6bc.toInt(), 0xffe8c66a.toInt(), 0xffd890a5.toInt(), 0xff7986ed.toInt()), prefs.getInt("custom_accent", 0))
            "Panel transparency" -> if (PanelTransparency.enabled(prefs)) 0 else 1
            "Glass finish" -> TvGlassStyle.finish(prefs)
            "Glass edge" -> Math.max(0, Math.min(2, prefs.getInt("glass_edge", 1)))
            "Visual style" -> ThemeStyle.id(prefs)
            "Wallpaper darkness" -> Math.min(2, prefs.getInt("background_dim", 0) / 20)
            "Wallpaper focus glow" -> if (prefs.getBoolean("focus_glow", true)) 0 else 1
            "Header background" -> if (glassOs) Math.min(1, headerBgMode()) else headerBgMode()
            "App columns" -> {
                val v = prefs.getInt(if (tv) "tv_columns" else "phone_columns", 0)
                if (v == 0) 0 else v - 1
            }
            "Tile size" -> if (prefs.getBoolean("large_tiles", false)) 1 else 0
            "Tile spacing" -> prefs.getInt("tile_spacing", 1)
            "Tile corners" -> prefs.getInt("tile_corners", 1)
            "Logo size" -> Math.max(0, Math.min(2, prefs.getInt("logo_size", 1)))
            "App label size" -> indexOf(appLabelSizes(), prefs.getInt("app_label_size", if (glassOs) 16 else if (tv) 17 else 14))
            "Focused app title size" -> indexOf(intArrayOf(24, 32, 40, 48), prefs.getInt("focus_title_size", if (tv) 32 else 26))
            "Focus border" -> prefs.getInt("focus_border", 1)
            "Clock size" -> indexOf(if (ThemeStyle.custom(prefs)) DemoGeometry.clockSizes(tv) else if (tv) intArrayOf(42, 52, 62) else intArrayOf(36, 44, 52), prefs.getInt("clock_size", if (tv) 52 else 44))
            "Clock style" -> prefs.getInt("clock_style", 0)
            "Watch face" -> prefs.getInt("harmony_clock_face", 0)
            "Metal finish" -> prefs.getInt("harmony_clock_finish", 0)
            "Clock accent" -> prefs.getInt("harmony_clock_accent", 0)
            "Second hand" -> if (prefs.getBoolean("harmony_clock_sweep", true)) 0 else 1
            "Harmony clock size" -> prefs.getInt("harmony_clock_size", 1)
            "Daily thoughts" -> if (prefs.getBoolean("harmony_daily_quote", true)) 0 else 1
            "Clock font" -> prefs.getInt("clock_font", 0)
            "Home weather widget" -> if (prefs.getBoolean("weather_widget", ThemeStyle.id(prefs) == ThemeStyle.GLASS)) 0 else 1
            "Widget animation" -> if (prefs.getBoolean("weather_widget_animation", true)) 0 else 1
            "Hijri day adjustment" -> Math.max(0, Math.min(4, prefs.getInt("hijri_adjustment", 0) + 2))
            "Time format" -> if (prefs.getBoolean("time_24h", false)) 1 else 0
            "Clock alignment" -> prefs.getInt("clock_align", 1)
            "Clock seconds" -> if (prefs.getBoolean("clock_seconds", false)) 1 else 0
            "Clock glow" -> if (prefs.getBoolean("clock_glow", false)) 1 else 0
            "Clock shadow" -> if (prefs.getBoolean("clock_shadow", true)) 1 else 0
            "Hide clock" -> if (prefs.getBoolean("hide_clock", false)) 1 else 0
            "Hide greeting" -> if (prefs.getBoolean("hide_greeting", false)) 1 else 0
            "Accent preset" -> prefs.getInt("theme_name", 0)
            "Launcher font" -> prefs.getInt("app_font", 0)
            "Glass intensity" -> indexOf(intArrayOf(30, 70, 100), prefs.getInt("glass_alpha", 70))
            "Wallpaper source" -> {
                val favorite = hasFavoriteWallpaper()
                if (gradientWallpaper()) if (favorite) 3 else 2
                else if ("custom" == prefs.getString("wallpaper_mode", "mountains")) -1
                else {
                    val source = prefs.getInt("wallpaper_source", 0)
                    if (source == 2 && !favorite) 0 else source
                }
            }
            "Bottom shade" -> if (prefs.getBoolean("bottom_shade", true)) 1 else 0
            "Continue watching" -> if (prefs.getBoolean("continue_watching", true)) 0 else 1
            "Avatar shape" -> prefs.getInt("avatar_shape", 0)
            "Performance" -> prefs.getInt("perf_mode", 0)
            "Home wallpaper" -> if ("custom" == prefs.getString("wallpaper_mode", "mountains")) 1 else -1
            "Temperature unit" -> if (prefs.getBoolean("weather_fahrenheit", false)) 1 else 0
            "Start screensaver after idle" -> indexOf(intArrayOf(0, 2, 5, 10, 15), prefs.getInt("idle_screensaver_minutes", 0))
            else -> -1
        }
    }

    private fun indexOf(values: IntArray, value: Int): Int {
        for (i in values.indices) if (values[i] == value) return i
        return -1
    }
}
