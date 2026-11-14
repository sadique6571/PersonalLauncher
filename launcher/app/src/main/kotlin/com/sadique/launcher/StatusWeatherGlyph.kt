package com.sadique.launcher

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import java.util.Locale

/** Small factual condition glyph; unavailable weather never shows a guessed condition. */
class StatusWeatherGlyph(weather: String, hour: Int, private val color: Int) : Drawable() {
    private val ink = Paint(3)
    private val moon = Path()
    private val kind: Int

    init {
        val s = weather.lowercase(Locale.ROOT)
        kind = if (s.contains("cloud") || s.contains("rain") || s.contains("snow") || s.contains("fog") || s.contains("overcast")) {
            2
        } else if (hour < 6 || hour >= 18) {
            1
        } else {
            0
        }
        moon.addCircle(12f, 12f, 8f, Path.Direction.CW)
        val cut = Path()
        cut.addCircle(16f, 8f, 8f, Path.Direction.CW)
        moon.op(cut, Path.Op.DIFFERENCE)
    }

    override fun draw(c: Canvas) {
        val save = c.save()
        val b = bounds
        c.translate(b.left.toFloat(), b.top.toFloat())
        c.scale(b.width() / 24f, b.height() / 24f)
        ink.color = color
        ink.style = Paint.Style.FILL
        if (kind == 1) {
            c.drawPath(moon, ink)
        } else if (kind == 2) {
            c.drawCircle(8f, 13f, 5f, ink)
            c.drawCircle(13f, 10f, 6f, ink)
            c.drawCircle(18f, 14f, 4f, ink)
            c.drawRect(7f, 13f, 19f, 18f, ink)
        } else {
            c.drawCircle(12f, 12f, 5f, ink)
            ink.strokeWidth = 1.7f
            for (i in 0..7) {
                val a = i * Math.PI / 4
                c.drawLine(
                    12 + Math.cos(a).toFloat() * 8,
                    12 + Math.sin(a).toFloat() * 8,
                    12 + Math.cos(a).toFloat() * 11,
                    12 + Math.sin(a).toFloat() * 11,
                    ink
                )
            }
        }
        c.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        ink.alpha = alpha
    }

    override fun setColorFilter(filter: ColorFilter?) {
        ink.colorFilter = filter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
