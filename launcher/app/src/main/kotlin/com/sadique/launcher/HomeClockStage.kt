package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout

/** Arranges the clock and its neighboring instruments above the app grid. */
internal class HomeClockStage(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val tv: Boolean,
    private val resumed: Boolean,
    private val glassOs: Boolean,
    private val harmony: Boolean,
) {
    class Views(
        val content: LinearLayout,
        val weather: WeatherWidgetView?,
        val telemetry: GlassTelemetry?,
    )

    fun build(
        clock: LinearLayout,
        alignment: Int,
        clockVisible: Boolean,
        showWeather: Boolean,
        harmonyHero: (() -> View)?,
        harmonyShortcuts: () -> View,
        forecast: Runnable,
        focusGrid: () -> Unit,
        focusSearch: () -> Unit,
    ): Views {
        val scrollContent = LinearLayout(host)
        scrollContent.orientation = LinearLayout.VERTICAL
        if (glassOs && !harmony) scrollContent.gravity = Gravity.CENTER_VERTICAL
        if (!clockVisible) clock.visibility = View.GONE
        val clockParams = LinearLayout.LayoutParams(-1, -2)
        clockParams.gravity = intArrayOf(Gravity.START, Gravity.CENTER_HORIZONTAL, Gravity.END)[alignment]
        var weather: WeatherWidgetView? = null
        var telemetry: GlassTelemetry? = null
        if (harmonyHero != null) {
            scrollContent.addView(harmonyHero(), LinearLayout.LayoutParams(-1, -2))
        } else if (glassOs || showWeather) {
            val stage = LinearLayout(host)
            stage.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            val wide = host.resources.configuration.screenWidthDp >= 700
            stage.orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (wide) stage.addView(clock, LinearLayout.LayoutParams(0, -2, 1f)) else stage.addView(clock, clockParams)
            val instruments = LinearLayout(host)
            instruments.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            if (!wide) instruments.orientation = LinearLayout.VERTICAL
            val available = host.resources.configuration.screenWidthDp - if (tv) 64 else 32
            if (showWeather) {
                val widget = WeatherWidgetView(host, forecast)
                weather = widget
                val wp = LinearLayout.LayoutParams(dp(Math.min(available, WeatherWidgetLayout.width(available, glassOs))), -2)
                if (glassOs && wide) wp.marginEnd = dp(12) else if (!wide) wp.bottomMargin = dp(10)
                instruments.addView(widget, wp)
                widget.setOnKeyListener { _, key, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    if (key == KeyEvent.KEYCODE_DPAD_DOWN) { focusGrid(); return@setOnKeyListener true }
                    if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_LEFT) { focusSearch(); return@setOnKeyListener true }
                    key == KeyEvent.KEYCODE_DPAD_RIGHT
                }
                widget.active(resumed)
            }
            if (glassOs) {
                val cardWidth = if (wide) (if (showWeather) WeatherWidgetLayout.telemetryWidth(available) else Math.min(315, host.resources.configuration.screenWidthDp / 3)) else Math.min(280, available)
                if (harmony) instruments.addView(harmonyShortcuts(), LinearLayout.LayoutParams(dp(cardWidth), -2))
                else {
                    val tele = GlassTelemetry(host)
                    telemetry = tele
                    instruments.addView(tele, LinearLayout.LayoutParams(dp(cardWidth), -2))
                    if (resumed) tele.start()
                }
            }
            stage.addView(instruments)
            if (prefs.getBoolean("apps_only", false)) stage.visibility = View.GONE
            scrollContent.addView(stage, LinearLayout.LayoutParams(-1, -2))
        } else scrollContent.addView(clock, clockParams)
        return Views(scrollContent, weather, telemetry)
    }

    private fun dp(value: Int): Int = Math.round(value * host.resources.displayMetrics.density)
}
