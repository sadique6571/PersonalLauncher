package com.sadique.launcher

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/** Reference's blue sky gradient and lower-right focused-app halo, without full-screen blur. */
internal class GlassOsBackdrop {
    private val paint = Paint(3)
    private var base: Shader? = null
    private var left: Shader? = null
    private var right: Shader? = null
    private var focus: Shader? = null
    private var width = 0
    private var height = 0
    private var accent = 0
    private var strength = -1

    fun draw(canvas: Canvas, w: Int, h: Int, color: Int, intensity: Int) {
        if (w <= 0 || h <= 0) return
        if (w != width || h != height || color != accent || intensity != strength) {
            width = w
            height = h
            accent = color
            strength = intensity
            base = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xff041329.toInt(), 0xff071b34.toInt(), 0xff030b18.toInt()), floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
            left = RadialGradient(w * 0.22f, h * 0.28f, w * 0.52f, 0x6b145d85.toInt(), 0x00145d85, Shader.TileMode.CLAMP)
            right = RadialGradient(w * 0.80f, h * 0.24f, w * 0.48f, 0x3840d9ff.toInt(), 0x0040d9ff, Shader.TileMode.CLAMP)
            focus = RadialGradient(w * 0.72f, h * 0.78f, w * 0.38f, (color and 0xffffff) or (Math.round(122 * intensity / 100f) shl 24), color and 0xffffff, Shader.TileMode.CLAMP)
        }
        paint.shader = base
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = left
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = right
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = focus
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }
}
