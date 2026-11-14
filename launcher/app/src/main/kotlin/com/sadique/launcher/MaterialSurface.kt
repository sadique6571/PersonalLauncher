package com.sadique.launcher

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable

/** Copper and pearl themes clip their decorative contours to the card outline. */
internal class MaterialSurface(
    radius: Int, stroke: Int, private val focused: Boolean,
    prefs: SharedPreferences, drawer: Boolean, finish: Int
) : GlassSurface(stroke, 0) {
    private val pearl = ThemeStyle.light(prefs)
    private val accent = ThemeStyle.focusAccent(prefs)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = accent
    }
    private val clip = Path()
    private val contours = Array(4) { Path() }
    private val tabInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val tab = RectF()
    private var cachedRadius = -1f
    private var cachedShape = -1
    private val decorationAlpha: Int
    private val frame = RectF()

    init {
        val intensity = TvGlassStyle.intensity(prefs)
        decorationAlpha = Math.round((if (pearl) 34 else 68) * intensity / 100f)
        val fillAlpha = PanelTransparency.alpha(MaterialAppearance.alpha(finish, intensity, drawer, focused), prefs, drawer)
        val top = ThemeStyle.materialColor(prefs, ThemeStyle.baseTop(prefs), focused)
        val bottom = ThemeStyle.materialColor(prefs, ThemeStyle.baseBottom(prefs), focused)
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        colors = intArrayOf((fillAlpha shl 24) or (top and 0xffffff), (fillAlpha shl 24) or (bottom and 0xffffff))
        cornerRadius = radius.toFloat()
        val edge = TvGlassStyle.edge(prefs) ushr 24
        setStroke(stroke, if (focused) accent else (edge shl 24) or (if (pearl) 0x929b9a else 0xba8e69))
        backdropOpacity(GlassTransparency.backdropAlpha(finish, intensity))
    }

    private fun geometry() {
        val bounds = bounds
        val radius = cornerRadius
        val shape = shape
        if (frame.left == bounds.left.toFloat() && frame.top == bounds.top.toFloat() &&
            frame.right == bounds.right.toFloat() && frame.bottom == bounds.bottom.toFloat() &&
            cachedRadius == radius && cachedShape == shape) return
        frame.set(bounds)
        cachedRadius = radius
        cachedShape = shape
        clip.reset()
        if (shape == OVAL) clip.addOval(frame, Path.Direction.CW)
        else clip.addRoundRect(frame, radius, radius, Path.Direction.CW)
        ink.strokeWidth = maxOf(1f, frame.height() / 180f)
        if (pearl) {
            val contour = contours[0]
            contour.reset()
            contour.moveTo(frame.left, frame.top + 2)
            val top = frame.top + frame.height() * .13f
            contour.cubicTo(frame.centerX(), top, frame.centerX(), top, frame.right, frame.top + 2)
        } else {
            for ((index, contour) in contours.withIndex()) {
                val gap = frame.height() * .10f
                val x = frame.right - frame.width() * .34f + index * gap
                contour.reset()
                contour.moveTo(x, frame.top)
                contour.cubicTo(x + frame.width() * .22f, frame.centerY(),
                    x - frame.width() * .08f, frame.bottom, frame.right, frame.bottom - gap * index)
            }
        }
        val size = minOf(frame.width(), frame.height()) * .12f
        tab.set(frame.right - size * 2.4f, frame.top, frame.right, frame.top + size)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        geometry()
        val save = canvas.save()
        canvas.clipPath(clip)
        ink.alpha = decorationAlpha * alpha / 255
        for (index in 0 until if (pearl) 1 else 4) canvas.drawPath(contours[index], ink)
        if (focused) {
            tabInk.alpha = alpha
            val round = tab.height() / 3
            canvas.drawRoundRect(tab, round, round, tabInk)
        }
        canvas.restoreToCount(save)
    }
}
