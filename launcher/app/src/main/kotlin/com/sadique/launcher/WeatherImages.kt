package com.sadique.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader

/** Bundled weather photographs decoded once by the existing weather worker. */
object WeatherImages {
    @JvmField @Volatile var sun: Bitmap? = null
    @JvmField @Volatile var cloud: Bitmap? = null
    @JvmField @Volatile var clouds: Bitmap? = null
    @JvmField @Volatile var overcast: Bitmap? = null
    @JvmField @Volatile var storm: Bitmap? = null
    @JvmField @Volatile var fog: Bitmap? = null
    private var ready = false

    @JvmStatic
    @Synchronized
    fun prepare(context: Context) {
        if (ready) return
        sun = decode(context, R.drawable.weather_sun_sdo, 128)?.let { disc(it, 59.5f) }
        val sky = decode(context, R.drawable.weather_clouds_noaa, 512)
        clouds = sky
        overcast = decode(context, R.drawable.weather_overcast_noaa, 512)
        storm = decode(context, R.drawable.weather_storm_noaa, 512)
        fog = decode(context, R.drawable.weather_fog_noaa, 512)
        if (sky != null) cloud = extractCloud(sky)
        ready = true
    }

    private fun extractCloud(source: Bitmap): Bitmap {
        val width = 160
        val height = 107
        val small = Bitmap.createScaledBitmap(source, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        // Remove blue-sky chroma while preserving cloud edges and shading.
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val pixel = pixels[index]
                val neutral = 1f - ((Color.blue(pixel) - Color.red(pixel) - 9) / 35f).coerceIn(0f, 1f)
                var edge = minOf(1f, minOf(x, 159 - x) / 12f, minOf(y, 106 - y) / 10f)
                if (y > 82) edge *= maxOf(0f, (100 - y) / 18f)
                pixels[index] = (Math.round(255 * neutral * maxOf(0f, edge)) shl 24) or (pixel and 0xffffff)
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun decode(context: Context, id: Int, limit: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeResource(context.resources, id, options)
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > limit) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeResource(context.resources, id, options)
    }

    private fun disc(source: Bitmap, radius: Float): Bitmap {
        val result = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(128f / source.width, 128f / source.height) })
        }
        Canvas(result).drawCircle(64f, 64f, radius, paint)
        return result
    }

    @JvmStatic
    fun scene(kind: Int): Bitmap? = when (kind) {
        WeatherVisualState.PARTLY -> clouds
        WeatherVisualState.CLOUD, WeatherVisualState.SNOW -> overcast
        WeatherVisualState.FOG -> fog
        WeatherVisualState.RAIN, WeatherVisualState.STORM -> storm
        else -> null
    }
}
