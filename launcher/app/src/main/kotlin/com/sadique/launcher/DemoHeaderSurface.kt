package com.sadique.launcher

import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.GradientDrawable

/** Pearl's shaped porcelain bar, Cobalt's profile grooves, and open Dune/Sage headers. */
internal class DemoHeaderSurface(
    prefs: SharedPreferences,
    frosted: Boolean,
    radius: Int,
    stroke: Int
) : GlassSurface(stroke.toFloat(), 0) {
    private val style: Int = ThemeStyle.id(prefs)
    private val accent: Int = ThemeStyle.focusAccent(prefs)
    private val alphaVal: Int = PanelTransparency.alpha(
        MaterialAppearance.alpha(TvGlassStyle.finish(prefs), TvGlassStyle.intensity(prefs), false, false),
        prefs, false
    )
    private val titleColor: Int = ThemeStyle.headerTitleColor(prefs)
    private val paint = Paint(3)
    private val edge = Paint(3)
    private val foundation = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0))
    private val shape = Path()
    private val frost: GradientDrawable? = if (frosted) MaterialSurface(radius, stroke, false, prefs, false, 2) else null
    private var pearlFill: Shader? = null
    private var widthVal = -1
    private var heightVal = -1

    init {
        val support = HeaderAppearance.alpha(alphaVal)
        foundation.colors = intArrayOf(
            (support shl 24) or (ThemeStyle.materialColor(prefs, ThemeStyle.baseTop(prefs), false) and 0xffffff),
            (support shl 24) or (ThemeStyle.materialColor(prefs, ThemeStyle.baseBottom(prefs), false) and 0xffffff)
        )
        foundation.cornerRadius = radius.toFloat()
        setColor(Color.TRANSPARENT)
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = 1f
    }

    override fun backdrop(image: Bitmap?, w: Int, h: Int, x: Int, y: Int) {
        if (frost is GlassSurface) {
            frost.backdrop(image, w, h, x, y)
            invalidateSelf()
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width()
        val h = b.height()
        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        if (frost != null) {
            frost.setBounds(0, 0, w, h)
            frost.draw(canvas)
        }
        foundation.setBounds(0, 0, w, h)
        foundation.alpha = alpha
        foundation.draw(canvas)

        if (style == ThemeStyle.PEARL) {
            if (widthVal != w || heightVal != h) {
                widthVal = w
                heightVal = h
                shape.reset()
                shape.moveTo(0f, 0f)
                shape.lineTo(w.toFloat(), 0f)
                shape.lineTo(w.toFloat(), h * 0.72f)
                shape.cubicTo(w * 0.87f, h * 1.15f, w * 0.77f, h.toFloat(), w * 0.70f, h * 0.65f)
                shape.cubicTo(w * 0.64f, h * 0.13f, w * 0.58f, h * 0.26f, w * 0.5f, h * 0.26f)
                shape.cubicTo(w * 0.42f, h * 0.26f, w * 0.36f, h * 0.13f, w * 0.30f, h * 0.65f)
                shape.cubicTo(w * 0.23f, h.toFloat(), w * 0.13f, h * 1.15f, 0f, h * 0.72f)
                shape.close()
                pearlFill = LinearGradient(0f, 0f, 0f, Math.max(1, h).toFloat(), 0xfffcf1e8.toInt(), 0xffeae1db.toInt(), Shader.TileMode.CLAMP)
            }
            paint.shader = pearlFill
            paint.alpha = alphaVal * alpha / 255
            canvas.drawPath(shape, paint)
            edge.color = 0x99ffffff.toInt()
            edge.alpha = alpha
            canvas.drawPath(shape, edge)
        } else if (style == ThemeStyle.DUNE) {
            edge.color = accent
            edge.alpha = alpha / 4
            for (i in 0 until 4) {
                canvas.drawArc(-w * 0.05f, -h * (1.9f + i * 0.15f), w * 0.38f, h * (1.1f + i * 0.15f), 20f, 120f, false, edge)
            }
        } else if (style == ThemeStyle.COBALT) {
            val stripes = canvas.save()
            canvas.clipRect(0f, 0f, w * 0.30f, h.toFloat())
            paint.shader = null
            paint.color = 0x44182d73.toInt()
            paint.alpha = alphaVal * alpha / 255
            canvas.drawRect(0f, 0f, w * 0.30f, h.toFloat(), paint)
            edge.color = 0x773b7eff.toInt()
            edge.alpha = alpha
            var x = -h.toFloat()
            while (x < w * 0.30f) {
                canvas.drawLine(x, 0f, x + h * 0.65f, h.toFloat(), edge)
                x += Math.max(4, h / 12)
            }
            canvas.restoreToCount(stripes)
        } else if (style == ThemeStyle.SAGE) {
            paint.shader = null
            paint.color = 0xffc5d4ac.toInt()
            paint.alpha = alpha
            shape.reset()
            shape.moveTo(w - h * 0.25f, 0f)
            shape.lineTo(w.toFloat(), h * 0.25f)
            shape.lineTo(w.toFloat(), 0f)
            shape.close()
            canvas.drawPath(shape, paint)
        }

        if (w > h * 8) {
            paint.shader = null
            paint.alpha = alpha
            paint.color = titleColor
            paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            paint.textSize = h * 0.17f
            paint.textAlign = Paint.Align.CENTER
            val name = when (style) {
                ThemeStyle.DUNE -> "DUNE LOOM"
                ThemeStyle.PEARL -> "PEARL CURRENT"
                ThemeStyle.COBALT -> "COBALT RAIL"
                ThemeStyle.PINE -> "PINE MIST"
                ThemeStyle.AMBER -> "AMBER NIGHT"
                else -> "SAGE FOLD"
            }
            canvas.drawText(name, w / 2f, h * 0.2f, paint)
        }
        canvas.restoreToCount(save)
    }
}
