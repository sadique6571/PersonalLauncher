package com.sadique.launcher

import android.content.SharedPreferences

/** Stable style identifiers; legacy boolean installs retain exactly their selected style. */
object ThemeStyle {
    const val GLASS: Int = 0
    const val CLASSIC: Int = 1
    const val DUNE: Int = 2
    const val PEARL: Int = 3
    const val COBALT: Int = 4
    const val SAGE: Int = 5
    const val PINE: Int = 6
    const val AMBER: Int = 7
    const val HARMONY: Int = 8

    private val PREFIXES = arrayOf(
        "appearance.glass.", "appearance.classic.", "appearance.dune.", "appearance.pearl.",
        "appearance.cobalt.", "appearance.sage.", "appearance.pine.", "appearance.amber.", "appearance.harmony."
    )
    @JvmField
    val COUNT: Int = PREFIXES.size

    @JvmStatic
    fun clamp(style: Int): Int = Math.max(0, Math.min(COUNT - 1, style))

    @JvmStatic
    fun id(prefs: SharedPreferences): Int {
        return clamp(prefs.getInt("visual_style", if (prefs.getBoolean("tv_glass_style", true)) GLASS else CLASSIC))
    }

    @JvmStatic
    fun prefix(style: Int): String = PREFIXES[clamp(style)]

    @JvmStatic
    fun harmony(prefs: SharedPreferences): Boolean = id(prefs) == HARMONY

    @JvmStatic
    fun custom(prefs: SharedPreferences): Boolean = id(prefs) >= DUNE && !harmony(prefs)

    @JvmStatic
    fun pearl(prefs: SharedPreferences): Boolean = id(prefs) == PEARL

    @JvmStatic
    fun light(prefs: SharedPreferences): Boolean = pearl(prefs) || id(prefs) == SAGE

    @JvmStatic
    fun foreground(prefs: SharedPreferences): Int {
        return when (id(prefs)) {
            SAGE -> 0xff0d2b1a.toInt()
            PEARL -> 0xff242d30.toInt()
            DUNE -> 0xffffedd8.toInt()
            PINE -> 0xffeafff2.toInt()
            AMBER -> 0xfffff3dd.toInt()
            HARMONY -> 0xfff8fbff.toInt()
            else -> 0xfff1f5fb.toInt()
        }
    }

    @JvmStatic
    fun muted(prefs: SharedPreferences): Int {
        return when (id(prefs)) {
            SAGE -> 0xff31513c.toInt()
            PEARL -> 0xff4e6264.toInt()
            DUNE -> 0xffd3af8e.toInt()
            PINE -> 0xffa8cbb8.toInt()
            AMBER -> 0xffd8b98a.toInt()
            HARMONY -> 0xffc6daeb.toInt()
            else -> 0xffa4bcd6.toInt()
        }
    }

    @JvmStatic
    fun headerTitleColor(prefs: SharedPreferences): Int {
        return if (id(prefs) == DUNE) focusAccent(prefs) else if (id(prefs) == COBALT) 0xffd5e2ff.toInt() else foreground(prefs)
    }

    @JvmStatic
    fun accent(prefs: SharedPreferences): Int {
        val fallback = when (id(prefs)) {
            COBALT -> 0xffffd92f.toInt()
            SAGE -> 0xff195c36.toInt()
            DUNE -> 0xffedab75.toInt()
            PEARL -> 0xff006d72.toInt()
            PINE -> 0xff8fd0b2.toInt()
            AMBER -> 0xfff5b942.toInt()
            HARMONY -> 0xff8bd9ff.toInt()
            else -> 0xff40d9ff.toInt()
        }
        return when (prefs.getInt("theme_name", 0)) {
            1 -> 0xffff9a5c.toInt()
            2 -> if (light(prefs)) 0xff69439c.toInt() else 0xffb388ff.toInt()
            3 -> prefs.getInt("custom_accent", fallback)
            else -> fallback
        }
    }

    @JvmStatic
    fun baseTop(prefs: SharedPreferences): Int {
        return when (id(prefs)) {
            SAGE -> 0xffdce6be.toInt()
            COBALT -> 0xff12275c.toInt()
            PEARL -> 0xfff9f1e8.toInt()
            PINE -> 0xff17382c.toInt()
            AMBER -> 0xff2a1a0c.toInt()
            HARMONY -> 0xff294867.toInt()
            else -> 0xff26363a.toInt()
        }
    }

    @JvmStatic
    fun materialColor(prefs: SharedPreferences, base: Int, focused: Boolean): Int {
        return if (prefs.getInt("theme_name", 0) == 0) base else MaterialAppearance.tint(base, accent(prefs), focused)
    }

    @JvmStatic
    fun baseBottom(prefs: SharedPreferences): Int {
        return when (id(prefs)) {
            SAGE -> 0xffc5d4ac.toInt()
            COBALT -> 0xff07112d.toInt()
            PEARL -> 0xffe9e4dc.toInt()
            PINE -> 0xff08150f.toInt()
            AMBER -> 0xff120a04.toInt()
            HARMONY -> 0xff0a1b37.toInt()
            else -> 0xff111e24.toInt()
        }
    }

    @JvmStatic
    @JvmOverloads
    fun demoDefaults(prefs: SharedPreferences, tv: Boolean = false) {
        if (!custom(prefs) || prefs.getBoolean("demo_defaults_v1", false)) return
        val e = prefs.edit().putBoolean("demo_defaults_v1", true).putInt("header_bg_mode", 1)
            .putInt("tv_columns", 4).putInt("phone_columns", 2).putInt("avatar_shape", 1).putInt("clock_size", if (tv) 84 else 60)
        if (!prefs.contains("tile_corners")) e.putInt("tile_corners", if (id(prefs) == DUNE || id(prefs) == AMBER) 0 else 1)
        if (!prefs.contains("continue_watching")) e.putBoolean("continue_watching", false)
        if (!prefs.contains("focus_glow")) e.putBoolean("focus_glow", false)
        e.apply()
    }

    @JvmStatic
    fun focusAccent(prefs: SharedPreferences): Int {
        val color = accent(prefs)
        val background = if (light(prefs)) 0xffe9e4dc.toInt() else 0xff111e24.toInt()
        val base = luminance(background)
        for (step in 0..20) {
            val adjusted = MaterialAppearance.mix(color, foreground(prefs), step / 20f)
            val value = luminance(adjusted)
            if ((Math.max(value, base) + 0.05) / (Math.min(value, base) + 0.05) >= 3) return adjusted
        }
        return foreground(prefs)
    }

    @JvmStatic
    fun fixedTypography(prefs: SharedPreferences): Boolean = id(prefs) == GLASS || harmony(prefs)

    @JvmStatic
    fun fontFamily(prefs: SharedPreferences): String {
        return if (fixedTypography(prefs)) "sans-serif" else arrayOf("sans-serif", "serif", "monospace", "sans-serif-light")[Math.max(0, Math.min(3, prefs.getInt("app_font", 0)))]
    }

    @JvmStatic
    fun clockFontFamily(prefs: SharedPreferences): String {
        return if (fixedTypography(prefs)) "sans-serif" else arrayOf(if (id(prefs) == DUNE) "sans-serif-light" else if (id(prefs) == PINE) "serif" else "sans-serif", "serif", "monospace")[Math.max(0, Math.min(2, prefs.getInt("clock_font", 0)))]
    }

    @JvmStatic
    fun clockBold(prefs: SharedPreferences): Boolean = if (id(prefs) == DUNE || id(prefs) == PINE) prefs.getInt("clock_font", 0) != 0 else true

    @JvmStatic
    fun clockAlignment(prefs: SharedPreferences): Int = if (fixedTypography(prefs)) 1 else Math.max(0, Math.min(2, prefs.getInt("clock_align", 1)))

    @JvmStatic
    fun luminance(color: Int): Double {
        val rgb = doubleArrayOf(((color shr 16) and 255) / 255.0, ((color shr 8) and 255) / 255.0, (color and 255) / 255.0)
        for (i in 0..2) rgb[i] = if (rgb[i] <= 0.04045) rgb[i] / 12.92 else Math.pow((rgb[i] + 0.055) / 1.055, 2.4)
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
    }

    @JvmStatic
    @JvmOverloads
    fun wallpaperClockInk(background: Int, darkInk: Int = 0xff11191c.toInt()): Int {
        return if (((background shr 16) and 255) >= 245 && ((background shr 8) and 255) >= 245 && (background and 255) >= 245) {
            darkInk
        } else {
            0xffffffff.toInt()
        }
    }

    @JvmStatic
    fun contentColor(prefs: SharedPreferences, color: Int): Int {
        if (!light(prefs)) return color
        return if (luminance(color) > 0.4) {
            if ((color ushr 24) < 240) muted(prefs) else foreground(prefs)
        } else {
            color
        }
    }
}
