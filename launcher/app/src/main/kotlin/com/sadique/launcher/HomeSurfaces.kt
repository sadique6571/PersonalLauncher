package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.TextView
import java.util.Calendar

/** Tile/header surfaces, text views and theme accents for Home. Pure move from PersonalHomeActivity. */
internal class HomeSurfaces(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val dp: (Int) -> Int,
    private val glassOs: () -> Boolean,
) {
    /** Midnight Cyan default accent; matches PersonalHomeActivity's companion CYAN. */
    private val cyan = 0xff40d9ff.toInt()

    /** Current theme accent color: Midnight Cyan, Sunset Orange, Deep Purple, or Custom. */
    fun accentColor(): Int {
        if (ThemeStyle.custom(prefs) || ThemeStyle.harmony(prefs)) return GlassSettings.themeAccent(activity)
        return when (prefs.getInt("theme_name", 0)) {
            1 -> 0xffff9a5c.toInt() // Sunset Orange
            2 -> 0xffb388ff.toInt() // Deep Purple
            3 -> prefs.getInt("custom_accent", cyan)
            else -> cyan // Midnight Cyan
        }
    }

    fun appearanceDaylight(): Float {
        val now = Calendar.getInstance()
        return HomeAppearance.daylight(prefs.getString("theme_mode", "dark"), now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
    }

    fun surface(focused: Boolean): GradientDrawable = surface(focused, false)
    fun appSurface(focused: Boolean): GradientDrawable = surface(focused, true)
    fun appSurface(accent: Int, focused: Boolean): GradientDrawable = if (glassOs()) GlassAppCard.surface(activity, accent, focused, tileRadius()) else appSurface(focused)
    fun surface(focused: Boolean, drawer: Boolean): GradientDrawable {
        if (TvGlassStyle.enabled(prefs)) return if (drawer)
                TvGlassStyle.drawerCard(tileRadius(), dp(1), focused, prefs)
                else TvGlassStyle.surface(tileRadius(), dp(if (focused) 2 else 1), focused, prefs)
        val shape = GradientDrawable()
        val day = appearanceDaylight()
        // Theme-tinted glass (user 2026-11-01): the dark base takes the theme's hue.
        val accent = accentColor()
        val tint = 0.3f
        val night = HomeAppearance.blend(if (focused) 0xff153d5b.toInt() else 0xff092038.toInt(), accent, tint)
        val dayC = HomeAppearance.blend(if (focused) 0xff407596.toInt() else 0xff335975.toInt(), accent, tint)
        shape.setColor(HomeAppearance.blend(night, dayC, day))
        // Glass intensity (user 2026-11-01): 0=airy, 100=solid. Focused stays a touch brighter.
        val intensity = Math.max(0, Math.min(100, prefs.getInt("glass_alpha", 70)))
        shape.setAlpha(PanelTransparency.alpha(ClassicGlassAlpha.card(intensity, focused), prefs, drawer))
        shape.cornerRadius = tileRadius().toFloat()
        shape.setStroke(dp(if (focused) 3 else 1), if (focused) accent else 0xff28435e.toInt())
        return shape
    }

    fun toolbarSurface(focused: Boolean): GradientDrawable {
        val shape = surface(focused)
        shape.cornerRadius = dp(if (TvGlassStyle.enabled(prefs)) 24 else 16).toFloat()
        return shape
    }

    fun text(value: String, size: Int, color: Int): TextView {
        // Outline removed 2026-11-01 (user: "buri lag rahi hai") — plain text with drop shadow only.
        val view = TextView(activity)
        view.text = value
        view.setTextSize(size.toFloat())
        view.setTextColor(ThemeStyle.contentColor(prefs, color))
        applyAppFont(view)
        return view
    }

    fun tileBorder(accent: Int, focused: Boolean): Drawable? {
        if (ThemeStyle.custom(prefs)) return null // The shared path surface owns the matching edge.
        val border = GradientDrawable()
        border.setColor(Color.TRANSPARENT)
        border.cornerRadius = tileRadius().toFloat()
        val thickness = if (focused) intArrayOf(2, 3, 5)[Math.max(0, Math.min(2, prefs.getInt("focus_border", 1)))] else 1
        border.setStroke(dp(thickness), if (TvGlassStyle.enabled(prefs)) (if (focused) 0xfff4f8ff.toInt() else TvGlassStyle.edge(prefs)) else (if (focused) accent else 0x4028435e))
        val bevel = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x20ffffff, 0x00ffffff, 0x28000000))
        bevel.cornerRadius = tileRadius().toFloat()
        return LayerDrawable(arrayOf<Drawable>(bevel, border))
    }

    /** Applies the launcher-wide font to a TextView. */
    fun applyAppFont(view: TextView) {
        view.typeface = Typeface.create(
                ThemeStyle.fontFamily(prefs), Typeface.NORMAL)
    }

    /** Tile corner radius from settings: sharp / rounded / round. */
    fun tileRadius(): Int {
        return dp(HomeGeometry.tileRadius(prefs.getInt("tile_corners", 1)))
    }

    /** Logo size inside tiles from settings: small / normal / large. */
    fun logoScale(): Float {
        return floatArrayOf(0.8f, 1f, 1.2f)[Math.max(0, Math.min(2, prefs.getInt("logo_size", 1)))]
    }
}
