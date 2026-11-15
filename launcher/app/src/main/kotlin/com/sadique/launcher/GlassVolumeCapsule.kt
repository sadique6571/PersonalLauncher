package com.sadique.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Presentation only. VolumeSlider owns the shared observer, window and auto-hide deadline. */
internal class GlassVolumeCapsule(context: Context) : LinearLayout(context) {
    private val percent: TextView
    private val track: Track
    private val foreground: Int
    private var animator: ValueAnimator? = null
    private var shown = 0f
    private var previousPercent = -1

    init {
        val prefs = ThemePreferences.get(context)
        foreground = ThemeStyle.foreground(prefs)
        id = R.id.volumeCapsule
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(10f), dp(12f), dp(10f), dp(12f))
        val shell: GradientDrawable
        if (ThemeStyle.id(prefs) == ThemeStyle.GLASS) {
            val finish = if (TvGlassStyle.finish(prefs) == 1) 0.65f else if (TvGlassStyle.finish(prefs) == 2) 1.2f else 1f
            val alpha = PanelTransparency.alpha(Math.min(245, Math.round((194 * TvGlassStyle.intensity(prefs)).toFloat() / 70f * finish)), prefs, false)
            shell = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf((alpha shl 24) or 0x132539, (Math.min(255, alpha + 26) shl 24) or 0x09182a))
            shell.setStroke(dp(1f), TvGlassStyle.edge(prefs))
        } else if (ThemeStyle.harmony(prefs)) {
            shell = HarmonyVisionSurface.status(context)
        } else if (ThemeStyle.id(prefs) == ThemeStyle.CLASSIC) {
            shell = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(TvGlassStyle.tinted(0xff132539.toInt(), prefs), TvGlassStyle.tinted(0xff09182a.toInt(), prefs)))
            shell.setStroke(dp(1f), 0x55415a72)
            shell.alpha = PanelTransparency.alpha(ClassicGlassAlpha.card(TvGlassStyle.intensity(prefs), false), prefs, false)
        } else {
            shell = TvGlassStyle.surface(dp(999f), dp(1f), false, prefs)
        }
        shell.cornerRadius = dp(999f).toFloat()
        background = shell
        percent = label("—", 12, foreground)
        percent.id = R.id.volPercent
        addView(percent, LayoutParams(-1, dp(24f)))
        track = Track(context, prefs)
        track.id = R.id.volTrack
        addView(track, LayoutParams(dp(38f), 0, 1f))
        val caption = label(if (ThemeStyle.harmony(prefs)) "SOUND" else "VOL", if (ThemeStyle.harmony(prefs)) 9 else 10, ThemeStyle.muted(prefs))
        addView(caption, LayoutParams(-1, dp(24f)))
        isFocusable = false
    }

    private fun dp(n: Float): Int = Math.round(n * resources.displayMetrics.density)

    private fun label(value: String, size: Int, color: Int): TextView {
        val text = TextView(context)
        text.text = value
        text.textSize = size.toFloat()
        text.setTextColor(color)
        text.gravity = Gravity.CENTER
        text.typeface = Typeface.create(ThemeStyle.fontFamily(ThemePreferences.get(context)), Typeface.BOLD)
        return text
    }

    fun state(fraction: Float, value: Int, muted: Boolean, symbol: Int, accent: Int, animate: Boolean) {
        val clamped = Math.max(0f, Math.min(1f, fraction))
        if (previousPercent != value) {
            percent.text = if (muted || value == 0) "MUTE" else "$value%"
            contentDescription = context.getString(R.string.h_sound) + " " + percent.text
        }
        track.symbol(symbol)
        track.accent(accent)
        animator?.cancel()
        if (animate && previousPercent >= 0 && isShown && shown != clamped) {
            val anim = ValueAnimator.ofFloat(shown, clamped)
            anim.duration = 160
            anim.addUpdateListener { a -> shown = a.animatedValue as Float; track.level(shown) }
            anim.start()
            animator = anim
        } else {
            shown = clamped
            track.level(shown)
        }
        if (animate && previousPercent != value) {
            animate().cancel()
            scaleX = 1.02f
            scaleY = 1.02f
            animate().scaleX(1f).scaleY(1f).setDuration(180).start()
        }
        previousPercent = value
    }

    fun stopAnimation() {
        animator?.cancel()
        animator = null
        animate().cancel()
        scaleX = 1f
        scaleY = 1f
    }

    private inner class Track(context: Context, prefs: SharedPreferences) : View(context) {
        private val paint = Paint(3)
        private val outline = Path()
        private val bounds = RectF()
        private val fillMatrix = Matrix()
        private val light: Boolean = ThemeStyle.light(prefs)
        private var fraction = 0f
        private var accentValue = 0
        private var resource = 0
        private var gradient: Shader? = null
        private var symbolDrawable: Drawable? = null

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun level(value: Float) {
            fraction = value
            invalidate()
        }

        fun accent(value: Int) {
            if (gradient == null || accentValue != value) {
                accentValue = value
                gradient = LinearGradient(
                    0f, 0f, 0f, 1f,
                    intArrayOf(
                        0xffffffff.toInt(), 0xfff4f6fa.toInt(),
                        if (ThemeStyle.id(ThemePreferences.get(context)) == ThemeStyle.GLASS || ThemeStyle.harmony(ThemePreferences.get(context))) 0xffdceeff.toInt() else (value or 0xff000000.toInt())
                    ),
                    floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
                )
            }
            invalidate()
        }

        fun symbol(value: Int) {
            if (value != resource) {
                resource = value
                symbolDrawable = TvSymbols.icon(context, value)
                place()
            }
            invalidate()
        }

        private fun place() {
            val symbol = symbolDrawable
            if (symbol != null) {
                val side = dp(22f)
                val left = (width - side) / 2
                val bottom = height - dp(14f)
                symbol.setBounds(left, bottom - side, left + side, bottom)
            }
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            bounds.set(dp(1f).toFloat(), dp(1f).toFloat(), (w - dp(1f)).toFloat(), (h - dp(1f)).toFloat())
            outline.reset()
            outline.addRoundRect(bounds, w / 2f, w / 2f, Path.Direction.CW)
            place()
        }

        override fun onDraw(c: Canvas) {
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = if (light) 0x18000000 else 0x18ffffff
            c.drawRoundRect(bounds, width / 2f, width / 2f, paint)
            val fillHeight = bounds.height() * fraction
            val top = bounds.bottom - fillHeight
            val grad = gradient
            if (fillHeight > 0 && grad != null) {
                val save = c.save()
                c.clipPath(outline)
                fillMatrix.setScale(1f, fillHeight)
                fillMatrix.postTranslate(0f, top)
                grad.setLocalMatrix(fillMatrix)
                paint.alpha = 255
                paint.shader = grad
                c.drawRect(bounds.left, top, bounds.right, bounds.bottom, paint)
                paint.shader = null
                c.restoreToCount(save)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f).toFloat()
            paint.color = if (light) 0x24000000 else 0x29ffffff
            c.drawRoundRect(bounds, width / 2f, width / 2f, paint)
            paint.style = Paint.Style.FILL
            val symbol = symbolDrawable
            if (symbol != null) {
                var save = c.save()
                c.clipRect(0f, 0f, width.toFloat(), top)
                symbol.setColorFilter(this@GlassVolumeCapsule.foreground, PorterDuff.Mode.SRC_IN)
                symbol.draw(c)
                c.restoreToCount(save)
                save = c.save()
                c.clipRect(0f, top, width.toFloat(), height.toFloat())
                symbol.setColorFilter(if (Color.luminance(accentValue).toDouble() > 0.179) 0xff142239.toInt() else 0xfff8fafc.toInt(), PorterDuff.Mode.SRC_IN)
                symbol.draw(c)
                c.restoreToCount(save)
            }
        }
    }
}
