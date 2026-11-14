package com.sadique.launcher

import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.GradientDrawable

/** Four reference card silhouettes and decorations; all paints/paths are size-cached. */
internal class DemoCardSurface @JvmOverloads constructor(
    radius: Int,
    private val unit: Float,
    private val focused: Boolean,
    prefs: SharedPreferences,
    drawer: Boolean = true
) : GradientDrawable() {
    private val style: Int = ThemeStyle.id(prefs)
    private val accent: Int = ThemeStyle.focusAccent(prefs)
    private val edge: Int = (TvGlassStyle.edge(prefs) ushr 24)
    private val footer: Float = DemoGeometry.footer(style, unit)
    private val fill = Paint(3)
    private val line = Paint(3)
    private val detail = Paint(3)
    private val bevel = Paint(3)
    private val body = Path()
    private val inside = Path()
    private val fold = Path()
    private var widthVal = 0f
    private var heightVal = 0f
    private var lastRadius = -1f
    private var lastLeft = -1
    private var lastTop = -1
    private val topColor: Int = ThemeStyle.materialColor(prefs, ThemeStyle.baseTop(prefs), false)
    private val bottomColor: Int = ThemeStyle.materialColor(prefs, ThemeStyle.baseBottom(prefs), false)
    private val opacity: Int

    init {
        val s = TvGlassStyle.intensity(prefs) / 100f
        val max = when (TvGlassStyle.finish(prefs)) {
            1 -> 210
            2 -> 255
            else -> 245
        }
        opacity = PanelTransparency.alpha(Math.round(max * (2 * s - s * s)), prefs, drawer)
        setColor(Color.TRANSPARENT)
        cornerRadius = radius.toFloat()
        line.style = Paint.Style.STROKE
        bevel.style = Paint.Style.STROKE
        bevel.strokeWidth = unit
        line.strokeWidth = unit * (if (focused) intArrayOf(2, 3, 5)[Math.max(0, Math.min(2, prefs.getInt("focus_border", 1)))] else 1)
    }

    private fun geometry() {
        val b = bounds
        val r = cornerRadius
        val h = Math.max(1f, b.height() - footer)
        if (widthVal == b.width().toFloat() && heightVal == h && lastRadius == r && lastLeft == b.left && lastTop == b.top) return
        widthVal = b.width().toFloat()
        heightVal = h
        lastRadius = r
        lastLeft = b.left
        lastTop = b.top
        DemoPaths.set(body, style, widthVal, heightVal, r)
        DemoPaths.set(inside, style, Math.max(1f, widthVal - unit * 3), Math.max(1f, heightVal - unit * 3), Math.max(1f, r - unit))
        inside.offset(unit * 1.5f, unit * 1.5f)
        fill.shader = LinearGradient(0f, 0f, 0f, heightVal, topColor, bottomColor, Shader.TileMode.CLAMP)
        bevel.shader = LinearGradient(0f, 0f, 0f, heightVal, 0x33ffffff, 0x66000000, Shader.TileMode.CLAMP)
        fold.reset()
        val f = Math.min(widthVal * 0.15f, heightVal * 0.22f)
        fold.moveTo(widthVal - f, 0f)
        fold.lineTo(widthVal - f, f)
        fold.lineTo(widthVal, f)
        fold.close()
    }

    override fun draw(canvas: Canvas) {
        geometry()
        val save = canvas.save()
        canvas.translate(lastLeft.toFloat(), lastTop.toFloat())
        if (style == ThemeStyle.SAGE) {
            detail.style = Paint.Style.FILL
            detail.color = 0xff657c50.toInt()
            detail.alpha = alpha * opacity / 255
            canvas.save()
            canvas.translate(0f, footer)
            canvas.drawPath(body, detail)
            canvas.restore()
        }
        fill.alpha = opacity * alpha / 255
        canvas.drawPath(body, fill)
        val clip = canvas.save()
        canvas.clipPath(body)
        detail.style = Paint.Style.STROKE
        detail.strokeWidth = unit * 0.65f
        detail.color = if (style == ThemeStyle.COBALT) 0xff397aff.toInt() else accent
        detail.alpha = Math.round(alpha * (if (style == ThemeStyle.DUNE) 0.33f else 0.45f))
        if (style == ThemeStyle.DUNE) {
            for (i in 0 until 4) {
                canvas.drawArc(widthVal * 0.65f + i * unit * 5, -heightVal * 0.5f, widthVal * 1.22f + i * unit * 5, heightVal * 0.85f, 70f, 120f, false, detail)
            }
        }
        if (style == ThemeStyle.COBALT) {
            for (i in 0 until 2) {
                canvas.drawLine(unit * 10, heightVal - unit * (8 + i * 4), widthVal - unit * 10, heightVal - unit * (8 + i * 4), detail)
            }
        }
        if (style == ThemeStyle.PEARL && focused) {
            detail.style = Paint.Style.FILL
            detail.color = accent
            detail.alpha = alpha
            canvas.drawRoundRect(widthVal - heightVal * 0.22f, 0f, widthVal, heightVal * 0.18f, heightVal * 0.08f, heightVal * 0.08f, detail)
        }
        if (style == ThemeStyle.SAGE) {
            detail.style = Paint.Style.FILL
            detail.color = if (focused) 0xfff1b283.toInt() else 0xffeff3d9.toInt()
            detail.alpha = alpha * opacity / 255
            canvas.drawPath(fold, detail)
        }
        canvas.restoreToCount(clip)
        bevel.alpha = opacity * alpha / 255
        canvas.drawPath(inside, bevel)
        line.color = when {
            focused -> accent
            style == ThemeStyle.COBALT -> 0xff397aff.toInt()
            style == ThemeStyle.PEARL -> 0xfffcf4eb.toInt()
            else -> accent
        }
        line.alpha = if (focused) alpha else alpha * edge / 255
        canvas.drawPath(body, line)
        if (style == ThemeStyle.PEARL) {
            detail.style = Paint.Style.STROKE
            detail.color = if (focused) accent else 0xff8e9694.toInt()
            detail.alpha = alpha * edge / 255
            canvas.drawPath(inside, detail)
        }
        if (focused && (style == ThemeStyle.DUNE || style == ThemeStyle.COBALT)) {
            detail.style = Paint.Style.FILL
            detail.color = accent
            detail.alpha = alpha
            canvas.drawRoundRect(widthVal * 0.4f, heightVal + unit * 3, widthVal * 0.6f, heightVal + unit * 6, unit * 2, unit * 2, detail)
        }
        canvas.restoreToCount(save)
    }
}
