package com.sadique.launcher

import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable

/** Configurable TV glass surfaces. Optional window blur is managed by GlassSettings. */
object TvGlassStyle {

    @JvmStatic
    fun enabled(prefs: SharedPreferences): Boolean = ThemeStyle.id(prefs) != ThemeStyle.CLASSIC

    @JvmStatic
    fun finish(prefs: SharedPreferences): Int = Math.max(0, Math.min(2, prefs.getInt("glass_finish", 0)))

    @JvmStatic
    fun intensity(prefs: SharedPreferences): Int = Math.max(0, Math.min(100, prefs.getInt("glass_alpha", 70)))

    @JvmStatic
    fun edge(prefs: SharedPreferences): Int {
        return intArrayOf(0x00ffffff, 0x55ffffff, 0x99ffffff.toInt())[Math.max(0, Math.min(2, prefs.getInt("glass_edge", 1)))]
    }

    @JvmStatic
    fun tinted(base: Int, prefs: SharedPreferences): Int {
        val theme = prefs.getInt("theme_name", 0)
        val accent = if (theme == 1) 0xffff9a5c.toInt() else if (theme == 2) 0xffb388ff.toInt() else if (theme == 3) prefs.getInt("custom_accent", 0xff40d9ff.toInt()) else 0xff40d9ff.toInt()
        val mix = if (theme == 3 && (accent and 0x00ffffff) == 0) 0.55f else 0.12f
        return android.graphics.Color.rgb(
            Math.round(android.graphics.Color.red(base) * (1 - mix) + android.graphics.Color.red(accent) * mix),
            Math.round(android.graphics.Color.green(base) * (1 - mix) + android.graphics.Color.green(accent) * mix),
            Math.round(android.graphics.Color.blue(base) * (1 - mix) + android.graphics.Color.blue(accent) * mix)
        )
    }

    @JvmStatic
    fun readable(color: Int): Int {
        return if (android.graphics.Color.luminance(color) < 0.15) 0xffdce5f2.toInt() else color
    }


    @JvmStatic
    fun liquid(radius: Int, strokeWidth: Int, prefs: SharedPreferences): GradientDrawable {
        return surface(radius, strokeWidth, false, prefs, true, false)
    }

    @JvmStatic
    fun drawer(radius: Int, strokeWidth: Int, prefs: SharedPreferences): GradientDrawable {
        return surface(radius, strokeWidth, false, prefs, true, true)
    }

    @JvmStatic
    fun drawerCard(radius: Int, strokeWidth: Int, focused: Boolean, prefs: SharedPreferences): GradientDrawable {
        if (ThemeStyle.custom(prefs)) return DemoCardSurface(radius, strokeWidth.toFloat(), focused, prefs)
        return surface(radius, strokeWidth, focused, prefs, true, true)
    }

    @JvmStatic
    fun header(radius: Int, strokeWidth: Int, prefs: SharedPreferences, frosted: Boolean): GradientDrawable {
        if (ThemeStyle.custom(prefs)) return DemoHeaderSurface(prefs, frosted, radius, strokeWidth)
        if (ThemeStyle.harmony(prefs)) {
            val h = surface(radius, strokeWidth, false, prefs, true, false, if (frosted) 2 else finish(prefs))
            val alpha = HeaderAppearance.alpha(PanelTransparency.alpha(190, prefs, false))
            h.colors = intArrayOf((alpha shl 24) or 0x294564, (alpha shl 24) or 0x10243f)
            return h
        }
        val finishVal = if (frosted) 2 else finish(prefs)
        val intensityVal = intensity(prefs)
        if (enabled(prefs)) {
            val header = surface(radius, strokeWidth, false, prefs, true, false, finishVal)
            val alpha = HeaderAppearance.alpha(PanelTransparency.alpha(GlassTransparency.tintAlpha(finishVal, intensityVal), prefs, false))
            header.colors = intArrayOf(
                (alpha shl 24) or (tinted(0xff132539.toInt(), prefs) and 0xffffff),
                (alpha shl 24) or (tinted(0xff09182a.toInt(), prefs) and 0xffffff)
            )
            return header
        }
        val shape = GlassSurface(strokeWidth.toFloat(), edge(prefs))
        val alpha = HeaderAppearance.alpha(PanelTransparency.alpha(GlassTransparency.tintAlpha(finishVal, intensityVal), prefs, false))
        shape.colors = intArrayOf(
            (PanelTransparency.alpha(alpha, prefs, false) shl 24) or (tinted(0xff132539.toInt(), prefs) and 0xffffff),
            (alpha shl 24) or (tinted(0xff09182a.toInt(), prefs) and 0xffffff)
        )
        shape.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        shape.backdropOpacity(GlassTransparency.backdropAlpha(finishVal, intensityVal))
        shape.cornerRadius = radius.toFloat()
        return shape
    }

    @JvmOverloads
    @JvmStatic
    fun surface(
        radius: Int,
        strokeWidth: Int,
        focused: Boolean,
        prefs: SharedPreferences,
        liquid: Boolean = false,
        drawer: Boolean = false,
        finish: Int = finish(prefs)
    ): GradientDrawable {
        if (ThemeStyle.custom(prefs)) return MaterialSurface(radius, strokeWidth, focused, prefs, drawer, finish)
        if (ThemeStyle.harmony(prefs)) {
            val strength = intensity(prefs)
            val raw = (if (finish == 1) 34 else if (finish == 2) 102 else 65) + Math.round(strength * 1.30f)
            val alpha = PanelTransparency.alpha(Math.min(235, raw + (if (focused) 25 else 0)), prefs, drawer)
            val card = GlassSurface(strokeWidth.toFloat(), if (focused) 0xe5f6ffff.toInt() else edge(prefs))
            card.orientation = GradientDrawable.Orientation.TOP_BOTTOM
            card.colors = intArrayOf(
                (alpha shl 24) or 0x2d526e,
                (Math.max(0, alpha - 12) shl 24) or 0x152b49,
                (Math.max(0, alpha - 6) shl 24) or 0x0c2039
            )
            card.cornerRadius = radius.toFloat()
            if (liquid) card.backdropOpacity(GlassTransparency.backdropAlpha(finish, strength))
            return card
        }
        val strength = intensity(prefs) / 100f
        val base = if (finish == 1) 24 else if (finish == 2) 96 else 48
        var alpha = base + Math.round((245 - base) * strength * strength)
        if (liquid) alpha = GlassTransparency.tintAlpha(finish, intensity(prefs))
        if (focused) alpha = Math.min(245, alpha + 30)
        val top = (PanelTransparency.alpha(alpha, prefs, drawer) shl 24) or (tinted(if (focused) 0xffaaa7b5.toInt() else 0xff797580.toInt(), prefs) and 0xffffff)
        val middle = (PanelTransparency.alpha(Math.max(0, alpha - 28), prefs, drawer) shl 24) or (tinted(0xff494451.toInt(), prefs) and 0xffffff)
        val bottom = (PanelTransparency.alpha(Math.max(0, alpha - 16), prefs, drawer) shl 24) or (tinted(0xff393541.toInt(), prefs) and 0xffffff)
        val edgeVal = edge(prefs)
        val shape = GlassSurface(strokeWidth.toFloat(), if (focused) 0xccffffff.toInt() else edgeVal)
        if (liquid) shape.backdropOpacity(GlassTransparency.backdropAlpha(finish, intensity(prefs)))
        shape.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        shape.colors = intArrayOf(top, middle, bottom)
        shape.cornerRadius = radius.toFloat()
        return shape
    }
}
