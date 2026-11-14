package com.sadique.launcher

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView

/** Circular photo with the reference's white-to-focused-app ring. Shared profile owns the bitmap. */
internal class AppAccentAvatar(context: Context) : ImageView(context) {
    private val photo = Paint(3)
    private val ring = Paint(3)
    private val dot = Paint(3)
    private val clip = Path()
    private val matrix = Matrix()
    private var last: Drawable? = null
    private var accent = 0xffe50914.toInt()
    private var online = false
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var shape = -1

    init {
        scaleType = ScaleType.CENTER_CROP
    }

    fun accent(color: Int) {
        accent = color
        rebuildRing()
        invalidate()
    }

    fun online(value: Boolean) {
        online = value
        invalidate()
    }

    private fun dp(n: Float): Float = n * resources.displayMetrics.density

    private fun rebuildRing() {
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = dp(1.5f)
        ring.shader = LinearGradient(
            0f, 0f,
            Math.max(1, width).toFloat(),
            Math.max(1, height).toFloat(),
            0xffffffff.toInt(),
            accent,
            Shader.TileMode.CLAMP
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        last = null
        rebuildRing()
        geometry()
    }

    private fun geometry() {
        shape = ThemePreferences.get(context).getInt("avatar_shape", 0)
        clip.reset()
        val inset = dp(2f)
        if (shape == 0) {
            clip.addCircle(
                width / 2f,
                height / 2f,
                Math.max(0f, Math.min(width, height) / 2f - inset),
                Path.Direction.CW
            )
        } else {
            clip.addRoundRect(
                inset,
                inset,
                width - inset,
                height - inset,
                dp(11f),
                dp(11f),
                Path.Direction.CW
            )
        }
    }

    override fun onDraw(c: Canvas) {
        if (shape != ThemePreferences.get(context).getInt("avatar_shape", 0)) {
            geometry()
        }
        val drawable = drawable
        if (drawable !== last || cachedWidth != width || cachedHeight != height) {
            last = drawable
            cachedWidth = width
            cachedHeight = height
            photo.shader = null
            if (drawable is BitmapDrawable) {
                val b = drawable.bitmap
                if (b != null && !b.isRecycled) {
                    val shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    val s = Math.max(
                        width / b.width.toFloat(),
                        height / b.height.toFloat()
                    )
                    matrix.setScale(s, s)
                    matrix.postTranslate(
                        (width - b.width * s) / 2,
                        (height - b.height * s) / 2
                    )
                    shader.setLocalMatrix(matrix)
                    photo.shader = shader
                }
            }
        }
        val save = c.save()
        c.clipPath(clip)
        if (photo.shader != null) {
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), photo)
        } else {
            super.onDraw(c)
        }
        c.restoreToCount(save)
        if (shape == 0) {
            c.drawCircle(
                width / 2f,
                height / 2f,
                Math.max(0f, Math.min(width, height) / 2f - dp(1f)),
                ring
            )
        } else {
            c.drawRoundRect(
                dp(1f),
                dp(1f),
                width - dp(1f),
                height - dp(1f),
                dp(12f),
                dp(12f),
                ring
            )
        }
        val x = width * 0.83f
        val y = height * 0.83f
        dot.color = 0xff0e1320.toInt()
        c.drawCircle(x, y, dp(4.5f), dot)
        dot.color = if (online) 0xff22c55e.toInt() else 0xffef4444.toInt()
        c.drawCircle(x, y, dp(3f), dot)
    }
}
