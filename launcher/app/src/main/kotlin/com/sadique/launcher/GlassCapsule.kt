package com.sadique.launcher

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable

/** Shared full-height capsule: white glass fill for Apple, accent fill and border for Classic. */
internal class GlassCapsule {
    private val shell: GradientDrawable
    private val glass: Boolean
    private val textColor: Int
    private val accent: Int
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var radius = 0f
    private val relief: CapsuleRelief?

    constructor(context: Context) : this(context, false)

    constructor(context: Context, embossed: Boolean) {
        val prefs = ThemePreferences.get(context)
        glass = ThemeStyle.id(prefs) == ThemeStyle.GLASS
        accent = GlassSettings.themeAccent(context)
        textColor = ThemeStyle.foreground(prefs)
        relief = if (embossed && ThemeStyle.id(prefs) != ThemeStyle.CLASSIC) CapsuleRelief(context.resources.displayMetrics.density) else null
        val stroke = Math.max(1, Math.round(context.resources.displayMetrics.density))
        if (TvGlassStyle.enabled(prefs)) {
            shell = TvGlassStyle.surface(0, stroke, false, prefs)
        } else {
            shell = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(TvGlassStyle.tinted(0xff132539.toInt(), prefs), TvGlassStyle.tinted(0xff09182a.toInt(), prefs))
            )
            shell.setStroke(stroke, 0x55415a72.toInt())
            shell.alpha = PanelTransparency.alpha(ClassicGlassAlpha.card(TvGlassStyle.intensity(prefs), false), prefs, false)
        }
    }

    fun textColor(): Int = textColor

    fun filledTextColor(): Int {
        return if (glass) 0xff142239.toInt() else if (Color.luminance(accent) > .179) 0xff000000.toInt() else 0xffffffff.toInt()
    }

    fun size(width: Int, height: Int) {
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        radius = Math.min(width, height) / 2f
        shell.cornerRadius = radius
        shell.setBounds(0, 0, width, height)
        relief?.size(width, height)
        fill.shader = LinearGradient(
            0f, 0f, Math.max(1, width).toFloat(), Math.max(1, height).toFloat(),
            if (glass) 0xffffffff.toInt() else accent, if (glass) 0xffe9edf5.toInt() else accent, Shader.TileMode.CLAMP
        )
    }

    fun draw(canvas: Canvas, fraction: Float, vertical: Boolean, rtl: Boolean) {
        var frac = fraction
        shell.draw(canvas)
        frac = Math.max(0f, Math.min(1f, frac))
        if (frac > 0) {
            val save = canvas.save()
            if (vertical) canvas.clipRect(0f, bounds.height() * (1 - frac), bounds.width(), bounds.height())
            else if (rtl) canvas.clipRect(bounds.width() * (1 - frac), 0f, bounds.width(), bounds.height())
            else canvas.clipRect(0f, 0f, bounds.width() * frac, bounds.height())
            canvas.drawRoundRect(bounds, radius, radius, fill)
            canvas.restoreToCount(save)
        }
        (shell as? GlassSurface)?.drawRim(canvas)
        relief?.draw(canvas)
    }
}
