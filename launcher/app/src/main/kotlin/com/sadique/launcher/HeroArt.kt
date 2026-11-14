package com.sadique.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable

/** Builds the cinematic shelf's backdrop as a small texture. */
object HeroArt {
    const val W = 48
    const val H = 27
    private const val SATURATION = 1.7f

    @JvmStatic
    fun from(live: Drawable?): Bitmap? {
        if (live == null) return null
        val drawable = live.constantState?.newDrawable()?.mutate() ?: live
        val saved = Rect(drawable.bounds)
        val big = Bitmap.createBitmap(W * 4, H * 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(big)
        canvas.drawColor(Color.BLACK)
        drawable.setBounds(0, 0, big.width, big.height)
        try {
            drawable.draw(canvas)
        } finally {
            drawable.bounds = saved
        }
        // Two filtered halvings average more neighboring pixels than a single four-times reduction.
        val half = Bitmap.createScaledBitmap(big, W * 2, H * 2, true)
        big.recycle()
        val small = Bitmap.createScaledBitmap(half, W, H, true)
        half.recycle()
        var pixels = IntArray(W * H)
        small.getPixels(pixels, 0, W, 0, 0, W, H)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val lum = .2126f * r + .7152f * g + .0722f * b
            pixels[i] = Color.rgb(color(lum, r), color(lum, g), color(lum, b))
        }
        repeat(3) {
            pixels = boxBlur(pixels, true)
            pixels = boxBlur(pixels, false)
        }
        val out = if (small.isMutable) small else small.copy(Bitmap.Config.ARGB_8888, true)
        if (out !== small) small.recycle()
        out.setPixels(pixels, 0, W, 0, 0, W, H)
        return out
    }

    private fun color(luminance: Float, channel: Int): Int =
        Math.round((luminance + (channel - luminance) * SATURATION).coerceIn(0f, 255f))

    private fun boxBlur(source: IntArray, horizontal: Boolean): IntArray {
        val output = IntArray(source.size)
        val lines = if (horizontal) H else W
        val length = if (horizontal) W else H
        val stride = if (horizontal) 1 else W
        for (line in 0 until lines) {
            val start = if (horizontal) line * W else line
            for (i in 0 until length) {
                var r = 0
                var g = 0
                var b = 0
                for (k in -2..2) {
                    val p = source[start + (i + k).coerceIn(0, length - 1) * stride]
                    r += Color.red(p)
                    g += Color.green(p)
                    b += Color.blue(p)
                }
                output[start + i * stride] = Color.rgb(r / 5, g / 5, b / 5)
            }
        }
        return output
    }
}
