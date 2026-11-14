package com.sadique.launcher

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/** Clean proportional marks for demo apps; other installed apps retain their own icon. */
internal class DemoBrandMark private constructor(private val kind: Int) : Drawable() {
    private val ink = Paint(3)
    private val path = Path()

    override fun getIntrinsicWidth(): Int = 128
    override fun getIntrinsicHeight(): Int = 128

    private fun polygon(c: Canvas, color: Int, vararg xy: Float) {
        path.reset()
        path.moveTo(xy[0], xy[1])
        var i = 2
        while (i < xy.size) {
            path.lineTo(xy[i], xy[i + 1])
            i += 2
        }
        path.close()
        ink.color = color
        ink.style = Paint.Style.FILL
        c.drawPath(path, ink)
    }

    override fun draw(c: Canvas) {
        val b = bounds
        val save = c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        c.scale(b.width() / 128f, b.height() / 128f)
        ink.style = Paint.Style.FILL
        if (kind == 1) {
            ink.color = 0xff087ccc.toInt()
            ink.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            ink.textAlign = Paint.Align.CENTER
            ink.textSize = 34f
            c.drawText("prime", 64f, 44f, ink)
            c.drawText("video", 64f, 78f, ink)
            ink.style = Paint.Style.STROKE
            ink.strokeWidth = 4f
            ink.strokeCap = Paint.Cap.ROUND
            path.reset()
            path.moveTo(22f, 91f)
            path.quadTo(67f, 116f, 108f, 89f)
            c.drawPath(path, ink)
            path.reset()
            path.moveTo(96f, 90f)
            path.lineTo(109f, 87f)
            path.lineTo(106f, 100f)
            c.drawPath(path, ink)
        } else if (kind == 2) {
            ink.color = 0xff1ed760.toInt()
            c.drawCircle(64f, 64f, 57f, ink)
            ink.color = 0xff10251c.toInt()
            ink.style = Paint.Style.STROKE
            ink.strokeWidth = 8f
            ink.strokeCap = Paint.Cap.ROUND
            for (i in 0..2) {
                path.reset()
                path.moveTo(28f + i * 4, 43f + i * 20)
                path.quadTo(64f, 31f + i * 20, 103f - i * 5, 52f + i * 17)
                c.drawPath(path, ink)
            }
        } else if (kind == 3) {
            polygon(c, 0xffff8b00.toInt(), 20f, 108f, 56f, 15f, 71f, 15f, 108f, 108f)
            polygon(c, 0xfffffff5.toInt(), 49f, 33f, 79f, 33f, 85f, 49f, 43f, 49f)
            polygon(c, 0xfffffff5.toInt(), 36f, 67f, 92f, 67f, 99f, 86f, 29f, 86f)
            ink.color = 0xffff8b00.toInt()
            c.drawRoundRect(9f, 103f, 119f, 118f, 7f, 7f, ink)
        } else {
            polygon(c, 0xff33bff3.toInt(), 15f, 8f, 76f, 64f, 15f, 120f)
            polygon(c, 0xff31d58a.toInt(), 15f, 8f, 93f, 51f, 76f, 64f)
            polygon(c, 0xffffca3a.toInt(), 93f, 51f, 116f, 64f, 93f, 78f, 76f, 64f)
            polygon(c, 0xffff4c63.toInt(), 15f, 120f, 76f, 64f, 93f, 78f)
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

    companion object {
        @JvmStatic
        fun choose(pkg: String, fallback: Drawable): Drawable {
            val kind = when (pkg) {
                "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient" -> 1
                "com.spotify.tv.android", "com.spotify.music" -> 2
                "org.videolan.vlc" -> 3
                "com.android.vending" -> 4
                else -> 0
            }
            return if (kind == 0) fallback else DemoBrandMark(kind)
        }
    }
}
