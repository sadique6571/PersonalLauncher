package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

internal class HomeClockLayout(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val tv: Boolean,
    private val resumed: Boolean,
    private val glassOs: Boolean,
    private val harmony: Boolean,
    private val weatherWidgetEnabled: Boolean,
    private val clockDatesInline: Boolean,
    private val dp: (Float) -> Int,
    private val text: (String, Int, Int) -> TextView,
    private val clockAlignment: () -> Int,
    private val fitClockText: (CharSequence) -> Unit,
) {
    class Views(
        val container: LinearLayout,
        val time: TextView,
        val date: TextView,
        val ampm: TextView,
        val islamicDate: TextView,
        val analogClock: SweepAnalogClock?,
        val spotlight: TextView?,
        val textSize: Int,
    )

    fun build(root: FrameLayout, sky: HomeWallpaperView, onClockLayout: () -> Unit): Views {
        val clock = LinearLayout(host)
        clock.orientation = LinearLayout.VERTICAL
        clock.gravity = if (weatherWidgetEnabled || glassOs) Gravity.START else intArrayOf(Gravity.START, Gravity.CENTER_HORIZONTAL, Gravity.END)[clockAlignment()]
        val compact = host.resources.configuration.screenHeightDp < 480
        clock.setPadding(0, dp(if (compact) 0 else if (tv) if (ThemeStyle.custom(prefs)) 32 else 10 else 16), 0, dp(if (compact) 8 else 12))
        var clockSize = prefs.getInt("clock_size", if (tv) 52 else 44)
        // One-time migration (2026-10-31): old sizes were too big, map to new scale.
        val oldSizes = if (tv) intArrayOf(66, 80, 96) else intArrayOf(56, 68, 80)
        val newSizes = if (tv) intArrayOf(42, 52, 62) else intArrayOf(36, 44, 52)
        for (i in oldSizes.indices) {
            if (clockSize == oldSizes[i]) {
                clockSize = newSizes[i]
                prefs.edit().putInt("clock_size", clockSize).apply()
                break
            }
        }
        clockSize = HomeGeometry.clockSize(clockSize, compact)
        val timeView = text("", clockSize, TEXT)
        timeView.gravity = Gravity.CENTER
        if (glassOs) timeView.id = R.id.clock
        timeView.isSingleLine = true
        // Clock shadow for readability on bright wallpapers (toggle).
        if (prefs.getBoolean("clock_shadow", true)) {
            timeView.setShadowLayer(dp(8).toFloat(), 0f, dp(3).toFloat(), 0xcc000000.toInt())
        }
        // Use the regular family with bold weight; the light family overrides the intended weight.
        timeView.typeface = Typeface.create(
                ThemeStyle.clockFontFamily(prefs), if (ThemeStyle.clockBold(prefs)) Typeface.BOLD else Typeface.NORMAL)
        // Analog mode uses a bounded independent dial, including on the Glass gradient.
        val analogClockMode = prefs.getInt("clock_style", 0) == 1 && !harmony
        // AM/PM is a separate view, not a span: it can never be clipped when
        // seconds make the string longest. (User 2026-11-01.)
        val ampmView = text("", Math.max(14, (clockSize * 0.42f).toInt()), TEXT)
        ampmView.isSingleLine = true
        if (prefs.getBoolean("clock_shadow", true)) {
            ampmView.setShadowLayer(dp(4).toFloat(), 0f, dp(2).toFloat(), 0xcc000000.toInt())
        }
        if (analogClockMode) {
            timeView.visibility = View.INVISIBLE
            ampmView.visibility = View.INVISIBLE
        }
        // No auto-size: fitClockText() sizes precisely.
        timeView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
        // Horizontal padding prevents clipping from the shadow layer.
        timeView.setPadding(dp(12), dp(2), dp(4), dp(10))
        // Analog face radius follows the clock size (diameter ~= digital height).
        sky.clockRadius = dp(clockSize * 0.85f).toFloat()
        val dateView = text("", if (ThemeStyle.custom(prefs)) if (tv) 20 else 16 else if (tv) 14 else 13, MUTED)
        if (prefs.getBoolean("clock_shadow", true)) {
            dateView.setShadowLayer(dp(6).toFloat(), 0f, dp(2).toFloat(), 0xcc000000.toInt())
        }
        dateView.gravity = if (weatherWidgetEnabled) Gravity.START else Gravity.CENTER
        if (glassOs) { dateView.id = R.id.date; ampmView.id = R.id.ampm; ampmView.setTextSize(18f) }
        val timeRow = LinearLayout(host)
        timeRow.orientation = LinearLayout.HORIZONTAL
        timeRow.gravity = Gravity.CENTER_VERTICAL or intArrayOf(Gravity.START, Gravity.CENTER_HORIZONTAL, Gravity.END)[clockAlignment()]
        timeRow.addView(timeView, LinearLayout.LayoutParams(-2, -2))
        val ampmParams = LinearLayout.LayoutParams(-2, -2)
        ampmParams.gravity = if (glassOs) Gravity.CENTER_VERTICAL else Gravity.BOTTOM
        ampmParams.bottomMargin = dp(if (glassOs) 0 else 6)
        if (glassOs) ampmParams.marginStart = dp(8)
        timeRow.addView(ampmView, ampmParams)
        clock.addView(timeRow, LinearLayout.LayoutParams(-1, dp(clockSize + if (compact) 10 else 36)))
        clock.addView(dateView)
        val islamicDate: TextView
        if (glassOs) {
            timeView.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            ampmView.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            val ampmBadge = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(if (harmony) 0xff406c92.toInt() else 0xff1b3855.toInt(), if (harmony) 0xff183b65.toInt() else 0xff102941.toInt()))
            ampmBadge.cornerRadius = dp(10).toFloat()
            ampmBadge.setStroke(dp(1), 0x7796bdf5)
            ampmView.setTextColor(Color.WHITE)
            ampmView.setTextSize(20f)
            ampmView.background = ampmBadge
            ampmView.setPadding(dp(8), dp(4), dp(8), dp(4))
            ampmView.minWidth = dp(56)
            ampmView.minHeight = dp(36)
            ampmView.gravity = Gravity.CENTER
            ampmView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            clock.setPadding(0, 0, 0, 0)
            val islamicView = text("", 13, MUTED)
            islamicDate = islamicView
            islamicView.id = R.id.lunarDate
            clock.addView(islamicView)
            if (clockDatesInline) {
                clock.removeView(dateView)
                clock.removeView(islamicView)
                val divider = View(host)
                divider.id = R.id.clockDivider
                divider.setBackgroundColor(0x38ffffff)
                val line = LinearLayout.LayoutParams(dp(1.5f), dp(48))
                line.setMargins(dp(12), 0, dp(12), 0)
                timeRow.addView(divider, line)
                val dates = LinearLayout(host)
                dates.orientation = LinearLayout.VERTICAL
                dateView.setTextSize(18f)
                dateView.setTypeface(dateView.typeface, Typeface.BOLD)
                dateView.gravity = Gravity.START
                dates.addView(dateView)
                dates.addView(islamicView)
                timeRow.addView(dates, LinearLayout.LayoutParams(0, -2, 1f))
            }
        } else {
            val islamicView = text("", 12, MUTED)
            islamicDate = islamicView
            islamicView.id = R.id.lunarDate
            clock.addView(islamicView)
        }
        val analogClock = if (analogClockMode) {
            timeRow.visibility = View.GONE
            clock.removeView(dateView)
            clock.removeView(islamicDate)
            val analogRow = LinearLayout(host)
            analogRow.gravity = Gravity.CENTER_VERTICAL
            analogRow.setPadding(dp(if (glassOs) 16 else 8), 0, 0, 0)
            val dial = SweepAnalogClock(host)
            val preferred = dp(if (glassOs) 208 else 196)
            analogRow.addView(dial, LinearLayout.LayoutParams(preferred, preferred))
            val dates = LinearLayout(host)
            dates.orientation = LinearLayout.VERTICAL
            dates.gravity = Gravity.CENTER_VERTICAL
            dateView.setTextSize(15f)
            dateView.gravity = Gravity.START
            dateView.maxLines = 2
            islamicDate.setTextSize(13f)
            islamicDate.maxLines = 2
            islamicDate.setPadding(0, dp(10), 0, 0)
            dates.addView(dateView, LinearLayout.LayoutParams(-1, -2))
            dates.addView(islamicDate, LinearLayout.LayoutParams(-1, -2))
            val dpDates = LinearLayout.LayoutParams(0, -2, 1f)
            dpDates.marginStart = dp(16)
            analogRow.addView(dates, dpDates)
            clock.addView(analogRow, 0, LinearLayout.LayoutParams(-1, -2))
            dial.active(resumed)
            analogRow.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
                if (r - l <= 0) return@addOnLayoutChangeListener
                val available = Math.max(1, r - l - analogRow.paddingLeft)
                var diameter = Math.min(preferred, Math.max(dp(144), available - dp(132)))
                diameter = Math.min(diameter, available)
                val lp = dial.layoutParams as LinearLayout.LayoutParams
                if (lp.width != diameter) { lp.width = diameter; lp.height = diameter; dial.layoutParams = lp }
                val pos = IntArray(2)
                val origin = IntArray(2)
                dial.getLocationInWindow(pos)
                root.getLocationInWindow(origin)
                sky.clockX = pos[0] - origin[0] + dial.width / 2f
                sky.clockY = pos[1] - origin[1] + dial.height / 2f
                sky.invalidate()
            }
            dial
        } else null
        timeRow.addOnLayoutChangeListener { _, l, _, r, _, ol, _, orOld, _ ->
            if (r - l != orOld - ol && timeView.text.isNotEmpty()) fitClockText(timeView.text)
        }
        val spotlight = if (glassOs && !harmony && !analogClockMode) {
            val spotlight = text("", 13, 0xffffffff.toInt())
            spotlight.isSingleLine = true
            spotlight.ellipsize = TextUtils.TruncateAt.END
            spotlight.setPadding(dp(13), dp(7), dp(13), dp(7))
            spotlight.background = GlassOsUi.panel(host, 999)
            val sp = LinearLayout.LayoutParams(-2, -2)
            sp.topMargin = dp(8)
            clock.addView(spotlight, sp)
            spotlight
        } else null
        timeView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val position = IntArray(2)
            val origin = IntArray(2)
            timeView.getLocationInWindow(position)
            root.getLocationInWindow(origin)
            sky.clockX = position[0] - origin[0] + timeView.width / 2f
            sky.clockY = position[1] - origin[1] + timeView.height / 2f
            sky.invalidate()
            onClockLayout()
        }
        return Views(clock, timeView, dateView, ampmView, islamicDate, analogClock, spotlight, clockSize)
    }

    private fun dp(value: Int): Int = dp(value.toFloat())

    private companion object {
        const val TEXT = 0xfff0f7ff.toInt()
        const val MUTED = 0xffa4bcd6.toInt()
    }
}
