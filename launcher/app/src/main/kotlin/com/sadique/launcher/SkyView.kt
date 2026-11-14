package com.sadique.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/** Renders the time-of-day backdrop into a small reusable texture. */
open class SkyView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var intensity = 0
    private var lastMinute = -1
    private var texture: Bitmap? = null
    private var drawable: BitmapDrawable? = null

    override fun hasOverlappingRendering(): Boolean = false

    fun setIntensity(percent: Int) {
        intensity = percent.coerceIn(0, 100)
        alpha = intensity / 100f
        visibility = if (intensity == 0) GONE else VISIBLE
        if (intensity > 0) render(true)
    }

    fun update() {
        if (intensity > 0 && visibility == VISIBLE) render(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (intensity > 0) render(true)
    }

    private fun sunTimes(): IntArray? {
        val latitude = context.getString(R.string.latitude).toDoubleOrNull() ?: return null
        val longitude = context.getString(R.string.longitude).toDoubleOrNull() ?: return null
        return Sun.riseAndSet(LocalDate.now(), latitude, longitude, ZoneId.systemDefault())
    }

    private fun render(force: Boolean) {
        val now = Calendar.getInstance()
        val minute = Sky.minuteOfDay(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        if (!force && minute == lastMinute) return
        lastMinute = minute
        val sun = sunTimes()
        val sky = if (sun == null) Sky.at(minute) else Sky.at(minute, sun[0], sun[1])
        val bitmap = texture ?: Bitmap.createBitmap(TEX_W, TEX_H, Bitmap.Config.ARGB_8888).also { texture = it }
        val canvas = Canvas(bitmap)
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, TEX_H.toFloat(), intArrayOf(sky.top, sky.mid, sky.bottom),
            floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, TEX_W.toFloat(), TEX_H.toFloat(), paint)
        if (sky.sun > 0) {
            val warm = Sky.lerpColor(sky.bottom, 0xffffd9a0.toInt(), .65f) and 0xffffff
            paint.shader = RadialGradient(sky.sunX * TEX_W, TEX_H * .80f, TEX_W * .55f,
                intArrayOf((sky.sun shl 24) or warm, warm), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, TEX_W.toFloat(), TEX_H.toFloat(), paint)
        }
        if (drawable == null) {
            val layer = BitmapDrawable(resources, bitmap).apply { gravity = Gravity.FILL }
            drawable = layer
            background = layer
        }
        invalidate()
    }

    private companion object {
        const val TEX_W = 480
        const val TEX_H = 270
    }
}
