package com.sadique.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import java.util.Calendar
import java.util.TimeZone

/** Reference-style dial in its own bounded view, independent of wallpaper/gradient drawing. */
class SweepAnalogClock(context: Context) : View(context) {
    private val paint = Paint(3)
    private val calendar = Calendar.getInstance()
    private val hand = Path()
    private val faceBounds = RectF(0f, 0f, 200f, 200f)
    private val frame = Runnable { invalidate() }
    private val frameMs: Int = if (MemoryProfile.effectiveTier(context, ThemePreferences.get(context)) == MemoryProfile.TIER_LIGHT) 33 else 16
    private var dial: Bitmap? = null
    private var active = false

    init {
        contentDescription = "Analog clock"
        isFocusable = false
    }

    fun active(value: Boolean) {
        active = value
        if (value) calendar.timeZone = TimeZone.getDefault()
        removeCallbacks(frame)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) removeCallbacks(frame) else invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        dial = null
    }

    private fun makeDial() {
        val size = Math.min(512, Math.max(1, Math.min(width, height)))
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        dial = b
        val c = Canvas(b)
        c.scale(size / 200f, size / 200f)
        paint.style = Paint.Style.FILL
        paint.color = 0xff050608.toInt()
        c.drawRoundRect(1f, 1f, 199f, 199f, 40f, 40f, paint)
        paint.color = 0xfff2f3f4.toInt()
        c.drawCircle(100f, 100f, 92f, paint)
        paint.color = 0xff080a0d.toInt()
        paint.strokeCap = Paint.Cap.BUTT
        for (i in 0..59) {
            val save = c.save()
            c.rotate(i * 6f, 100f, 100f)
            paint.strokeWidth = if (i % 5 == 0) 3f else 1.2f
            c.drawLine(100f, 12f, 100f, (if (i % 5 == 0) 21 else 17).toFloat(), paint)
            c.restoreToCount(save)
        }
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = 19f
        paint.textAlign = Paint.Align.CENTER
        for (i in 1..12) {
            val a = Math.toRadians((i * 30 - 90).toDouble())
            val x = 100 + Math.cos(a).toFloat() * 70
            val y = 100 + Math.sin(a).toFloat() * 70 - (paint.ascent() + paint.descent()) / 2
            c.drawText(i.toString(), x, y, paint)
        }
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (width <= 0 || height <= 0) return
        if (dial == null) makeDial()
        val size = Math.min(width, height).toFloat()
        val save = c.save()
        c.translate((width - size) / 2f, (height - size) / 2f)
        c.scale(size / 200f, size / 200f)
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        paint.isFilterBitmap = true
        dial?.let { c.drawBitmap(it, null, faceBounds, paint) }
        calendar.timeInMillis = System.currentTimeMillis()
        val hour = calendar.get(Calendar.HOUR)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        val millis = calendar.get(Calendar.MILLISECOND)
        drawHand(c, AnalogClockAngles.hours(hour, minute, second, millis), 49f, 5.8f)
        drawHand(c, AnalogClockAngles.minutes(minute, second, millis), 70f, 4.6f)
        val secondsSave = c.save()
        c.rotate(AnalogClockAngles.seconds(second, millis), 100f, 100f)
        paint.color = 0xffff9800.toInt()
        paint.strokeWidth = 2f
        paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(100f, 110f, 100f, 27f, paint)
        c.restoreToCount(secondsSave)
        paint.color = 0xff080a0d.toInt()
        c.drawCircle(100f, 100f, 5f, paint)
        paint.color = 0xffff9800.toInt()
        c.drawCircle(100f, 100f, 3.6f, paint)
        c.restoreToCount(save)
        if (active && windowVisibility == VISIBLE && isShown) {
            removeCallbacks(frame)
            postDelayed(frame, frameMs.toLong())
        }
    }

    private fun drawHand(c: Canvas, angle: Float, length: Float, width: Float) {
        val save = c.save()
        c.rotate(angle, 100f, 100f)
        paint.color = 0xff080a0d.toInt()
        paint.style = Paint.Style.FILL
        hand.reset()
        hand.moveTo(100f, 100f - length)
        hand.lineTo(100f + width, 94f)
        hand.lineTo(100f + width * 0.7f, 110f)
        hand.lineTo(100f - width * 0.7f, 110f)
        hand.lineTo(100f - width, 94f)
        hand.close()
        c.drawPath(hand, paint)
        c.restoreToCount(save)
    }
}
