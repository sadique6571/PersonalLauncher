package com.sadique.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Original rounded line symbols, sized for the TV toolbar; no external icon/font dependency. */
class TvSymbols private constructor(resource: Int) : Drawable() {
    private val resource: Int = normalize(resource)
    private var style = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        paint.color = 0xfff1f5fb.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.1f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val b = bounds
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(b.width() / 24f, b.height() / 24f)
        path.reset()
        if (resource == R.drawable.ic_search) {
            canvas.drawCircle(10.3f, 10.3f, 6.6f, paint)
            canvas.drawLine(15.3f, 15.3f, 21f, 21f, paint)
        } else if (resource == R.drawable.ic_settings) {
            canvas.drawCircle(12f, 12f, 6.2f, paint)
            canvas.drawCircle(12f, 12f, 2.4f, paint)
            for (i in 0..7) {
                canvas.save()
                canvas.rotate(i * 45f, 12f, 12f)
                canvas.drawLine(12f, 3f, 12f, 5.6f, paint)
                canvas.restore()
            }
        } else if (resource == R.drawable.ic_notifications) {
            path.moveTo(5f, 17f)
            path.cubicTo(7f, 15f, 7f, 13f, 7f, 10f)
            path.cubicTo(7f, 3f, 17f, 3f, 17f, 10f)
            path.cubicTo(17f, 13f, 17f, 15f, 19f, 17f)
            path.close()
            canvas.drawPath(path, paint)
            canvas.drawArc(RectF(10f, 17f, 14f, 21f), 0f, 180f, false, paint)
            canvas.drawLine(12f, 3f, 12f, 4.5f, paint)
        } else if (resource == R.drawable.ic_volume || resource == R.drawable.ic_volume_muted) {
            path.moveTo(3f, 9f)
            path.lineTo(7f, 9f)
            path.lineTo(12f, 5f)
            path.lineTo(12f, 19f)
            path.lineTo(7f, 15f)
            path.lineTo(3f, 15f)
            path.close()
            canvas.drawPath(path, paint)
            if (resource == R.drawable.ic_volume_muted) {
                canvas.drawLine(16f, 9f, 21f, 15f, paint)
                canvas.drawLine(21f, 9f, 16f, 15f, paint)
            } else {
                canvas.drawArc(RectF(11f, 7f, 20f, 17f), -55f, 110f, false, paint)
                canvas.drawArc(RectF(11f, 3f, 25f, 21f), -50f, 100f, false, paint)
            }
        } else if (resource == R.drawable.ic_person) {
            canvas.drawCircle(12f, 8f, 3.7f, paint)
            path.moveTo(4f, 21f)
            path.cubicTo(4f, 12f, 20f, 12f, 20f, 21f)
            canvas.drawPath(path, paint)
        } else if (resource == R.drawable.ic_picture) {
            canvas.drawRoundRect(RectF(2.5f, 4f, 21.5f, 20f), 3f, 3f, paint)
            path.moveTo(4f, 18f)
            path.lineTo(10f, 11f)
            path.lineTo(14f, 15f)
            path.lineTo(17f, 12f)
            path.lineTo(20f, 16f)
            canvas.drawPath(path, paint)
            canvas.drawCircle(17f, 8f, 1f, paint)
        } else if (resource == R.drawable.ic_wifi) {
            canvas.drawArc(RectF(2f, 5f, 22f, 23f), 218f, 104f, false, paint)
            canvas.drawArc(RectF(6f, 10f, 18f, 22f), 220f, 100f, false, paint)
            canvas.drawCircle(12f, 18f, 0.7f, paint)
        } else if (resource == R.drawable.ic_internet) {
            canvas.drawCircle(12f, 12f, 9f, paint)
            canvas.drawOval(RectF(8f, 3f, 16f, 21f), paint)
            canvas.drawLine(3f, 12f, 21f, 12f, paint)
        } else if (resource == R.drawable.ic_ethernet) {
            canvas.drawRoundRect(RectF(3f, 4f, 21f, 17f), 2f, 2f, paint)
            canvas.drawLine(12f, 17f, 12f, 21f, paint)
            canvas.drawLine(7f, 21f, 17f, 21f, paint)
            var x = 7
            while (x <= 17) {
                canvas.drawLine(x.toFloat(), 6f, x.toFloat(), 10f, paint)
                x += 3
            }
        } else if (resource == R.drawable.ic_volume_bluetooth) {
            path.moveTo(7f, 7f)
            path.lineTo(17f, 17f)
            path.lineTo(12f, 22f)
            path.lineTo(12f, 2f)
            path.lineTo(17f, 7f)
            path.lineTo(7f, 17f)
            canvas.drawPath(path, paint)
        } else if (resource == R.drawable.ic_volume_headphones) {
            canvas.drawArc(RectF(3f, 3f, 21f, 21f), 180f, 180f, false, paint)
            canvas.drawRoundRect(RectF(3f, 11f, 7f, 20f), 2f, 2f, paint)
            canvas.drawRoundRect(RectF(17f, 11f, 21f, 20f), 2f, 2f, paint)
        } else if (resource == R.drawable.ic_volume_speaker) {
            canvas.drawRoundRect(RectF(6f, 2f, 18f, 22f), 2f, 2f, paint)
            canvas.drawCircle(12f, 8f, 1f, paint)
            canvas.drawCircle(12f, 16f, 3f, paint)
        } else if (resource == R.drawable.ic_apps) {
            if (style >= ThemeStyle.PEARL) paint.style = Paint.Style.FILL
            val step = if (style == ThemeStyle.SAGE) 8 else 11
            val side = if (style == ThemeStyle.SAGE) 4 else 7
            val rows = if (style == ThemeStyle.SAGE) 3 else 2
            val cols = if (style == ThemeStyle.SAGE) 3 else 2
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val x = 3 + col * step
                    val y = 3 + row * step
                    canvas.drawRoundRect(RectF(x.toFloat(), y.toFloat(), (x + side).toFloat(), (y + side).toFloat()), 1.5f, 1.5f, paint)
                }
            }
            paint.style = Paint.Style.STROKE
        } else if (resource == R.drawable.ic_home_settings) {
            path.moveTo(3f, 11f)
            path.lineTo(12f, 3f)
            path.lineTo(21f, 11f)
            path.moveTo(5f, 10f)
            path.lineTo(5f, 21f)
            path.lineTo(10f, 21f)
            path.lineTo(10f, 15f)
            path.lineTo(14f, 15f)
            path.lineTo(14f, 21f)
            path.lineTo(19f, 21f)
            path.lineTo(19f, 10f)
            canvas.drawPath(path, paint)
        } else if (resource == R.drawable.ic_input) {
            if (style == ThemeStyle.PEARL) {
                path.moveTo(4f, 5f)
                path.lineTo(20f, 5f)
                path.lineTo(22f, 14f)
                path.lineTo(16f, 14f)
                path.lineTo(14f, 20f)
                path.lineTo(10f, 20f)
                path.lineTo(8f, 14f)
                path.lineTo(2f, 14f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawLine(7f, 9f, 17f, 9f, paint)
            } else if (style >= ThemeStyle.DUNE) {
                path.moveTo(10f, 3f)
                path.lineTo(21f, 3f)
                path.lineTo(21f, 21f)
                path.lineTo(10f, 21f)
                canvas.drawPath(path, paint)
                canvas.drawLine(2f, 12f, 15f, 12f, paint)
                path.reset()
                path.moveTo(10f, 7f)
                path.lineTo(15f, 12f)
                path.lineTo(10f, 17f)
                canvas.drawPath(path, paint)
            } else {
                canvas.drawRoundRect(RectF(3f, 4f, 21f, 20f), 3f, 3f, paint)
                canvas.drawLine(8f, 8f, 8f, 16f, paint)
                canvas.drawLine(5f, 12f, 15f, 12f, paint)
                path.moveTo(12f, 9f)
                path.lineTo(15f, 12f)
                path.lineTo(12f, 15f)
                canvas.drawPath(path, paint)
            }
        } else if (resource == R.drawable.ic_livetv) {
            canvas.drawRoundRect(RectF(3f, 6f, 21f, 19f), 2f, 2f, paint)
            canvas.drawLine(8f, 2f, 12f, 6f, paint)
            canvas.drawLine(16f, 2f, 12f, 6f, paint)
            canvas.drawLine(8f, 22f, 16f, 22f, paint)
        } else if (resource == R.drawable.ic_watchlist) {
            canvas.drawRoundRect(RectF(4f, 3f, 20f, 21f), 2f, 2f, paint)
            var y = 7
            while (y <= 17) {
                canvas.drawLine(8f, y.toFloat(), 16f, y.toFloat(), paint)
                y += 5
            }
        } else if (resource == R.drawable.ic_lock || resource == R.drawable.ic_lock_open) {
            canvas.drawRoundRect(RectF(5f, 10f, 19f, 21f), 2f, 2f, paint)
            canvas.drawArc(
                RectF(8f, 2f, 16f, 16f),
                if (resource == R.drawable.ic_lock) 180f else 200f,
                if (resource == R.drawable.ic_lock) 180f else 140f,
                false,
                paint
            )
            canvas.drawLine(12f, 14f, 12f, 17f, paint)
        } else if (resource == R.drawable.ic_media_pause) {
            canvas.drawLine(8f, 4f, 8f, 20f, paint)
            canvas.drawLine(16f, 4f, 16f, 20f, paint)
        } else if (resource == R.drawable.ic_media_play || resource == R.drawable.ic_media_next || resource == R.drawable.ic_media_previous) {
            val previous = resource == R.drawable.ic_media_previous
            path.moveTo(if (previous) 17f else 7f, 4f)
            path.lineTo(if (previous) 5f else 19f, 12f)
            path.lineTo(if (previous) 17f else 7f, 20f)
            path.close()
            canvas.drawPath(path, paint)
            if (resource != R.drawable.ic_media_play) {
                canvas.drawLine(if (previous) 3f else 21f, 4f, if (previous) 3f else 21f, 20f, paint)
            }
        } else if (resource == R.drawable.ic_microphone) {
            canvas.drawRoundRect(RectF(9f, 2f, 15f, 15f), 3f, 3f, paint)
            canvas.drawArc(RectF(5f, 7f, 19f, 19f), 0f, 180f, false, paint)
            canvas.drawLine(12f, 19f, 12f, 22f, paint)
            canvas.drawLine(8f, 22f, 16f, 22f, paint)
        } else if (resource == R.drawable.ic_palette) {
            path.moveTo(20f, 13f)
            path.cubicTo(25f, 2f, 7f, 0f, 3f, 9f)
            path.cubicTo(-1f, 20f, 12f, 24f, 14f, 19f)
            path.cubicTo(16f, 15f, 17f, 17f, 20f, 13f)
            path.close()
            canvas.drawPath(path, paint)
            canvas.drawCircle(7f, 10f, 0.7f, paint)
            canvas.drawCircle(11f, 6f, 0.7f, paint)
            canvas.drawCircle(17f, 7f, 0.7f, paint)
        } else if (resource == R.drawable.ic_brightness) {
            canvas.drawCircle(12f, 12f, 4f, paint)
            for (i in 0..7) {
                canvas.save()
                canvas.rotate(i * 45f, 12f, 12f)
                canvas.drawLine(12f, 2f, 12f, 4f, paint)
                canvas.restore()
            }
        } else if (resource == R.drawable.ic_clock) {
            canvas.drawCircle(12f, 12f, 9f, paint)
            canvas.drawLine(12f, 6f, 12f, 12f, paint)
            canvas.drawLine(12f, 12f, 17f, 15f, paint)
        } else if (resource == R.drawable.ic_swap_vert) {
            path.moveTo(4f, 7f)
            path.lineTo(8f, 3f)
            path.lineTo(12f, 7f)
            path.moveTo(8f, 3f)
            path.lineTo(8f, 20f)
            path.moveTo(12f, 17f)
            path.lineTo(16f, 21f)
            path.lineTo(20f, 17f)
            path.moveTo(16f, 21f)
            path.lineTo(16f, 4f)
            canvas.drawPath(path, paint)
        }
        canvas.restoreToCount(save)
    }

    override fun setTint(color: Int) {
        paint.color = color
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(filter: ColorFilter?) {
        paint.colorFilter = filter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = 24
    override fun getIntrinsicHeight(): Int = 24

    companion object {
        private val SUPPORTED = intArrayOf(
            R.drawable.ic_search, R.drawable.ic_notifications, R.drawable.ic_settings, R.drawable.ic_person,
            R.drawable.ic_picture, R.drawable.ic_volume, R.drawable.ic_volume_muted, R.drawable.ic_volume_bluetooth,
            R.drawable.ic_volume_headphones, R.drawable.ic_volume_speaker, R.drawable.ic_wifi, R.drawable.ic_ethernet,
            R.drawable.ic_internet, R.drawable.ic_apps, R.drawable.ic_apps_pill, R.drawable.ic_categories,
            R.drawable.ic_home_settings, R.drawable.ic_home_settings_pill, R.drawable.ic_input, R.drawable.ic_av,
            R.drawable.ic_live_guide, R.drawable.ic_livetv, R.drawable.ic_lock, R.drawable.ic_lock_open,
            R.drawable.ic_media_next, R.drawable.ic_media_pause, R.drawable.ic_media_play, R.drawable.ic_media_previous,
            R.drawable.ic_microphone, R.drawable.ic_palette, R.drawable.ic_brightness, R.drawable.ic_clock,
            R.drawable.ic_swap_vert, R.drawable.ic_watchlist, android.R.drawable.stat_sys_data_bluetooth
        )

        @JvmStatic
        fun supports(resource: Int): Boolean {
            for (id in SUPPORTED) if (id == resource) return true
            return false
        }

        private fun normalize(resource: Int): Int {
            if (resource == android.R.drawable.stat_sys_data_bluetooth) return R.drawable.ic_volume_bluetooth
            if (resource == R.drawable.ic_apps_pill || resource == R.drawable.ic_categories) return R.drawable.ic_apps
            if (resource == R.drawable.ic_home_settings_pill) return R.drawable.ic_home_settings
            if (resource == R.drawable.ic_av) return R.drawable.ic_input
            if (resource == R.drawable.ic_live_guide) return R.drawable.ic_watchlist
            return resource
        }

        @JvmStatic
        fun icon(context: Context, resource: Int): Drawable? {
            if (ThemeStyle.harmony(ThemePreferences.get(context))) {
                val mapped = when (resource) {
                    R.drawable.ic_apps, R.drawable.ic_apps_pill -> R.drawable.ic_harmony_apps
                    R.drawable.ic_settings, R.drawable.ic_home_settings -> R.drawable.ic_harmony_settings
                    R.drawable.ic_search -> R.drawable.ic_harmony_search
                    R.drawable.ic_notifications -> R.drawable.ic_harmony_notifications
                    R.drawable.ic_picture -> R.drawable.ic_harmony_picture
                    else -> resource
                }
                var vector = context.getDrawable(mapped)
                if (vector != null) {
                    vector = vector.mutate()
                    vector.setTint(ThemeStyle.foreground(ThemePreferences.get(context)))
                    return vector
                }
            }
            if (ThemeStyle.id(ThemePreferences.get(context)) == ThemeStyle.GLASS) {
                val reference = when (resource) {
                    R.drawable.ic_settings -> R.drawable.ic_glass_settings
                    R.drawable.ic_search -> R.drawable.ic_glass_search
                    R.drawable.ic_wifi -> R.drawable.ic_glass_wifi
                    R.drawable.ic_volume_bluetooth, android.R.drawable.stat_sys_data_bluetooth -> R.drawable.ic_glass_bluetooth
                    else -> 0
                }
                if (reference != 0) {
                    var d = context.getDrawable(reference)
                    if (d != null) {
                        d = d.mutate()
                        d.setTint(ThemeStyle.foreground(ThemePreferences.get(context)))
                    }
                    return d
                }
            }
            if (ThemeStyle.id(ThemePreferences.get(context)) == ThemeStyle.SAGE && resource == R.drawable.ic_settings) {
                var gear = context.getDrawable(resource)
                if (gear != null) {
                    gear = gear.mutate()
                    gear.setTint(ThemeStyle.foreground(ThemePreferences.get(context)))
                }
                return gear
            }
            if (TvGlassStyle.enabled(ThemePreferences.get(context)) && supports(resource)) {
                val symbol = TvSymbols(resource)
                symbol.style = ThemeStyle.id(ThemePreferences.get(context))
                symbol.paint.color = ThemeStyle.foreground(ThemePreferences.get(context))
                return symbol
            }
            val original = context.getDrawable(resource)
            return original?.mutate()
        }
    }
}
