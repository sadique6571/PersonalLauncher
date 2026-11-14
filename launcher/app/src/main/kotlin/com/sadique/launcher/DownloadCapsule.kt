package com.sadique.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View

/** The volume capsule, rotated horizontally: the entire body fills, with readable text over both halves. */
internal class DownloadCapsule(context: Context) : View(context) {
    private var capsule = GlassCapsule(context)
    private val prefs: SharedPreferences = ThemePreferences.get(context)
    private val styleChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "visual_style" || key == "tv_glass_style" || key == "panel_transparency" || key == "glass_alpha"
            || key == "app_font" || key == "glass_finish" || key == "glass_edge" || key == "theme_name" || key == "custom_accent"
        ) refreshStyle()
    }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(ThemeStyle.fontFamily(prefs), Typeface.BOLD)
        textSize = 14 * context.resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private var caption = "Checking GitHub"
    private var fitted: CharSequence = caption
    private var percent = -1
    private var baseline = 0f

    init {
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun refreshStyle() {
        text.typeface = Typeface.create(ThemeStyle.fontFamily(prefs), Typeface.BOLD)
        capsule = GlassCapsule(context)
        capsule.size(width, height)
        fit()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(styleChanged)
        refreshStyle()
    }

    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(styleChanged)
        super.onDetachedFromWindow()
    }

    fun progress(message: String, percent: Int) {
        this.percent = percent
        caption = message + if (percent < 0) "" else " · $percent%"
        contentDescription = caption
        fit()
        invalidate()
    }

    private fun fit() {
        val margin = 16 * resources.displayMetrics.density
        fitted = TextUtils.ellipsize(caption, text, Math.max(1f, width - margin * 2), TextUtils.TruncateAt.END)
        baseline = (height - text.ascent() - text.descent()) / 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        capsule.size(w, h)
        fit()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val fraction = Math.max(0, Math.min(100, percent)) / 100f
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        capsule.draw(canvas, fraction, false, rtl)
        text.color = capsule.textColor()
        canvas.drawText(fitted, 0, fitted.length, width / 2f, baseline, text)
        // Repaint only the text over the full-height themed fill for contrast.
        if (fraction > 0f) {
            val save = canvas.save()
            if (rtl) {
                canvas.clipRect(width * (1 - fraction), 0f, width.toFloat(), height.toFloat())
            } else {
                canvas.clipRect(0f, 0f, width * fraction, height.toFloat())
            }
            text.color = capsule.filledTextColor()
            canvas.drawText(fitted, 0, fitted.length, width / 2f, baseline, text)
            canvas.restoreToCount(save)
        }
    }
}
