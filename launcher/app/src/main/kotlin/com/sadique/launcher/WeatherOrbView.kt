package com.sadique.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/** Shaded weather artwork beside the reading. */
class WeatherOrbView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val destination = RectF()
    private var artwork: Bitmap? = null
    private var code = -1
    private var day = true

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun condition(value: Int, daytime: Boolean) {
        if (code != value || day != daytime) {
            code = value
            day = daytime
            artwork = null
        }
        invalidate()
    }

    private fun render(): Bitmap {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(2f, 2f)
        val kind = WeatherVisualState.kind(code)
        if (code < 0 || kind == WeatherVisualState.UNKNOWN) return bitmap
        paint.alpha = 255
        paint.color = Color.WHITE
        paint.isFilterBitmap = true
        if (kind == WeatherVisualState.CLEAR || kind == WeatherVisualState.PARTLY) {
            val disc = if (day) WeatherImages.sun else moon
            if (disc != null) canvas.drawBitmap(disc, null, RectF(3f, 0f, 61f, 58f), paint)
        }
        val cloud = WeatherImages.cloud
        if (kind == WeatherVisualState.CLEAR || cloud == null) return bitmap
        val rain = kind == WeatherVisualState.STORM || kind == WeatherVisualState.RAIN
        paint.colorFilter = PorterDuffColorFilter(if (rain) 0xff82909b.toInt() else 0xffe3e8eb.toInt(), PorterDuff.Mode.MULTIPLY)
        canvas.drawBitmap(cloud, null, RectF(0f, if (kind == WeatherVisualState.PARTLY) 24f else 12f, 64f, 63f), paint)
        paint.colorFilter = null
        when {
            rain -> {
                paint.color = 0xffa9c4d3.toInt()
                paint.strokeWidth = .8f
                repeat(5) { canvas.drawLine(17f + it * 7, 52f, 15f + it * 7, 59f, paint) }
            }
            kind == WeatherVisualState.SNOW -> {
                paint.color = 0xfff0f5f7.toInt()
                repeat(4) { canvas.drawCircle(19f + it * 8, 56f, 1.2f, paint) }
            }
            kind == WeatherVisualState.FOG -> {
                paint.color = 0xffb5c1c9.toInt()
                paint.strokeWidth = 1.2f
                repeat(3) { canvas.drawLine(14f, 48f + it * 4, 52f - it * 3, 48f + it * 4, paint) }
            }
        }
        return bitmap
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = artwork ?: render().also { artwork = it }
        val side = minOf(width, height).toFloat()
        destination.set((width - side) / 2, (height - side) / 2, (width + side) / 2, (height + side) / 2)
        paint.color = Color.WHITE
        paint.isFilterBitmap = true
        canvas.drawBitmap(bitmap, null, destination, paint)
    }

    companion object {
        // Fixed NASA SVS LRO artwork: https://svs.gsfc.nasa.gov/5048/
        @Volatile private var moon: Bitmap? = null

        @JvmStatic
        fun prepareMoon(context: Context) {
            if (moon != null) return
            synchronized(WeatherOrbView::class.java) {
                if (moon != null) return
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 8
                    inScaled = false
                }
                val source = BitmapFactory.decodeResource(context.resources, R.drawable.weather_moon_nasa, options) ?: return
                val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                val photo = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                photo.shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(Matrix().apply { setScale(128f / source.width, 128f / source.height) })
                }
                Canvas(bitmap).drawCircle(64f, 64f, 54.5f, photo)
                moon = bitmap
            }
        }
    }
}
