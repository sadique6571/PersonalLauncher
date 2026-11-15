package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

internal class HarmonyHomeHero(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val resumed: Boolean,
    private val dp: (Int) -> Int,
    private val text: (String, Int, Int) -> TextView,
    private val greetingText: () -> String,
    private val onHeroKey: (Int, KeyEvent) -> Boolean,
    private val showClockSettings: () -> Unit,
    private val showForecast: () -> Unit,
    private val restoreWeather: (WeatherWidgetView) -> Unit,
) {
    data class Views(
        val container: View,
        val clock: HarmonyVisionClockView,
        val greeting: TextView,
        val date: TextView,
        val quote: TextView,
        val weather: WeatherWidgetView?,
    )

    /** Three balanced zones: the original watch, one daily thought and a real weather widget. */
    fun build(): Views {
        val stage = LinearLayout(host)
        stage.orientation = LinearLayout.HORIZONTAL
        stage.gravity = Gravity.CENTER_VERTICAL
        stage.clipChildren = false
        stage.clipToPadding = false
        val screenH = host.resources.configuration.screenHeightDp
        val screenW = host.resources.configuration.screenWidthDp
        val side = dp(HarmonyVisionLayout.clockDiameterDp(screenH, prefs.getInt("harmony_clock_size", 1)))
        val clock = HarmonyVisionClockView(host)
        clock.setOnClickListener { showClockSettings() }
        clock.setOnKeyListener { _, key, event -> onHeroKey(key, event) }
        if (!prefs.getBoolean("hide_clock", false)) {
            val face = LinearLayout.LayoutParams(side, side)
            face.marginEnd = dp(if (screenW < 780) 12 else 22)
            stage.addView(clock, face)
        }
        val thought = LinearLayout(host)
        thought.orientation = LinearLayout.VERTICAL
        thought.gravity = Gravity.CENTER_VERTICAL
        stage.addView(thought, LinearLayout.LayoutParams(0, -2, 1f))
        val greetingValue = text(greetingText(), if (screenH < 470) 12 else 14, 0xffd7eaff.toInt())
        greetingValue.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        thought.addView(greetingValue)
        val quoteValue = text(HarmonyDailyWords.forCalendar(Calendar.getInstance()),
                if (screenW < 780) 19 else if (screenH < 470) 21 else 25, 0xffffffff.toInt())
        quoteValue.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        quoteValue.maxLines = 3
        quoteValue.ellipsize = TextUtils.TruncateAt.END
        quoteValue.setShadowLayer(dp(6).toFloat(), 0f, dp(2).toFloat(), 0x88061627.toInt())
        val quote = LinearLayout.LayoutParams(-1, -2)
        quote.topMargin = dp(7)
        thought.addView(quoteValue, quote)
        val dateValue = text("", 12, 0xffc0d3e3.toInt())
        val dateParams = LinearLayout.LayoutParams(-1, -2)
        dateParams.topMargin = dp(10)
        thought.addView(dateValue, dateParams)
        quoteValue.visibility = if (prefs.getBoolean("harmony_daily_quote", true)) View.VISIBLE else View.GONE
        var weather: WeatherWidgetView? = null
        if (HarmonyVisionLayout.showNativeWeatherWidget()) {
            val widget = WeatherWidgetView(host, showForecast)
            weather = widget
            widget.setOnKeyListener { _, key, event -> onHeroKey(key, event) }
            val weatherWidth = Math.max(210, Math.min(285, Math.round(screenW * 0.29f)))
            val wp = LinearLayout.LayoutParams(dp(weatherWidth), -2)
            wp.marginStart = dp(if (screenW < 780) 8 else 24)
            stage.addView(widget, wp)
            widget.active(resumed)
            restoreWeather(widget)
        }
        clock.active(resumed && !prefs.getBoolean("hide_clock", false))
        return Views(stage, clock, greetingValue, dateValue, quoteValue, weather)
    }
}
