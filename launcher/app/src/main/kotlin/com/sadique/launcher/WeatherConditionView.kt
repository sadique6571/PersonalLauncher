package com.sadique.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View

/** Photographic atmosphere with a bounded precipitation frame rate. */
class WeatherConditionView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scene = RectF(0f, 0f, 320f, 220f)
    private val frame = Runnable(::invalidate)
    private val frameMs = if (MemoryProfile.effectiveTier(context, ThemePreferences.get(context)) ==
        MemoryProfile.TIER_LIGHT) 125L else 66L
    private var backdrop: Bitmap? = null
    private var code = -1
    private var day = true
    private var active = false
    private var animation = true

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun condition(next: Int, daytime: Boolean) {
        if (code != next || day != daytime) {
            code = next
            day = daytime
            backdrop = null
        }
        invalidate()
    }

    fun active(value: Boolean, animation: Boolean) {
        active = value
        this.animation = animation
        removeCallbacks(frame)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) invalidate() else removeCallbacks(frame)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backdrop = null
    }

    private fun makeBackdrop(): Bitmap {
        paint.alpha = 255
        val width = width.coerceIn(1, 512)
        val height = maxOf(1, Math.round(width * 220f / 320))
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(width / 320f, height / 220f)
        val top = when {
            code < 0 -> 0xff152131
            !day -> 0xff071529
            code >= 3 -> 0xff294657
            else -> 0xff206491
        }
        val bottom = when {
            code < 0 -> 0xff07111f
            !day -> 0xff14283a
            code >= 3 -> 0xff50646b
            else -> 0xff8eafb7
        }
        paint.shader = LinearGradient(0f, 0f, 0f, 220f, top.toInt(), bottom.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(scene, paint)
        paint.shader = null
        val photo = WeatherImages.scene(WeatherVisualState.kind(code))
        if (photo != null) {
            val scale = maxOf(320f / photo.width, 220f / photo.height)
            val w = photo.width * scale
            val h = photo.height * scale
            if (!day) paint.colorFilter = PorterDuffColorFilter(0xff53647c.toInt(), PorterDuff.Mode.MULTIPLY)
            canvas.drawBitmap(photo, null, RectF((320 - w) / 2, (220 - h) / 2, (320 + w) / 2, (220 + h) / 2), paint)
            paint.colorFilter = null
        }
        paint.shader = LinearGradient(0f, 0f, 0f, 220f,
            intArrayOf(0x18030b17, 0x55030b17, 0xee030b17.toInt()),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(scene, paint)
        paint.shader = null
        return bitmap
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val bitmap = backdrop ?: makeBackdrop().also { backdrop = it }
        val save = canvas.save()
        canvas.scale(width / 320f, height / 220f)
        paint.alpha = 255
        canvas.drawBitmap(bitmap, null, scene, paint)
        val moving = active && animation
        val seconds = if (moving) (SystemClock.uptimeMillis() % 60000) / 1000f else 0f
        val kind = WeatherVisualState.kind(code)
        val drops = WeatherVisualState.drops(code)
        if (drops > 0) drawRain(canvas, drops, seconds)
        if (kind == WeatherVisualState.SNOW) drawSnow(canvas, seconds)
        if (kind == WeatherVisualState.STORM && seconds % 19f > 12 && seconds % 19f < 12.16f) {
            paint.color = 0x20dce7f3
            canvas.drawRect(scene, paint)
        }
        canvas.restoreToCount(save)
        if (moving && (drops > 0 || kind == WeatherVisualState.SNOW || kind == WeatherVisualState.STORM) &&
            windowVisibility == VISIBLE && isShown) {
            removeCallbacks(frame)
            postDelayed(frame, frameMs)
        }
    }

    private fun drawRain(canvas: Canvas, drops: Int, seconds: Float) {
        repeat(drops) { index ->
            val depth = .35f + (index % 7) / 10f
            val x = ((index * 73 % 350) - 10).toFloat()
            val y = (index * 47 + seconds * (70 + depth * 110)) % 248 - 20
            paint.color = Color.WHITE
            paint.alpha = (24 + depth * 54).toInt()
            paint.strokeWidth = .35f + depth * .6f
            canvas.drawLine(x, y, x - 1 - depth * 2, y + 4 + depth * 12, paint)
        }
        paint.alpha = 255
    }

    private fun drawSnow(canvas: Canvas, seconds: Float) {
        repeat(24) { index ->
            val depth = .3f + (index % 5) * .15f
            val x = (index * 59) % 320 + Math.sin((seconds * .35f + index).toDouble()).toFloat() * 9
            val y = (index * 29 + seconds * (8 + depth * 12)) % 230 - 10
            paint.color = Color.WHITE
            paint.alpha = (70 + depth * 100).toInt()
            canvas.drawCircle(x, y, .4f + depth, paint)
        }
        paint.alpha = 255
    }
}
