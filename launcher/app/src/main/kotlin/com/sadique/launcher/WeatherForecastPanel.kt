package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Forecast details in ordinary remote-focusable rows. */
class WeatherForecastPanel(
    private val host: Activity,
    private val refresh: Runnable,
    private val city: Runnable
) {
    private val rows = HomeLibraryUi.rows(host)
    private var dialog: AlertDialog? = null
    private var last: WeatherForecast? = null
    private var lastName: String? = null
    private var lastUnit = false

    fun show(data: WeatherForecast?, name: String, fahrenheit: Boolean) {
        render(data, name, fahrenheit)
        dialog = GlassSettings.panel(host, "Weather forecast", rows).apply { show() }
    }

    fun update(data: WeatherForecast?, name: String, fahrenheit: Boolean) {
        if (isShowing()) render(data, name, fahrenheit)
    }

    fun isShowing(): Boolean = dialog?.isShowing == true

    fun close() {
        dialog?.dismiss()
    }

    private fun render(data: WeatherForecast?, name: String, fahrenheit: Boolean) {
        if (data === last && name == lastName && fahrenheit == lastUnit) return
        last = data
        lastName = name
        lastUnit = fahrenheit
        val restore = rows.hasFocus()
        rows.removeAllViews()
        val prefs = ThemePreferences.get(host)
        val accent = GlassSettings.themeAccent(host)
        val muted = ThemeStyle.muted(prefs)
        val foreground = ThemeStyle.foreground(prefs)
        rows.addView(GlassSettings.label(host, name, 18, foreground))
        rows.addView(GlassSettings.label(host, data?.summary(fahrenheit) ?: "Unavailable", 15, foreground))
        rows.addView(GlassSettings.row(host, "Refresh weather", null, R.drawable.ic_brightness, accent, refresh))
        rows.addView(GlassSettings.row(host, "Change weather city", null, R.drawable.ic_picture, accent, city))
        if (data != null) {
            val locale = Locale.getDefault()
            val unit = if (fahrenheit) "F" else "C"
            rows.addView(GlassSettings.label(host, "Hourly forecast", 14, accent))
            if (data.hours.isEmpty()) rows.addView(GlassSettings.label(host, "Hourly forecast unavailable", 12, muted))
            for (hour in data.hours) {
                val time = if (hour.time.length >= 16) hour.time.substring(11, 16) else hour.time
                val temperature = String.format(locale, "%.0f°%s", hour.temperature, unit)
                row("$time · $temperature", LandscapeModel.condition(hour.code) + rain(hour.rain))
            }
            rows.addView(GlassSettings.label(host, "Daily forecast", 14, accent))
            if (data.days.isEmpty()) rows.addView(GlassSettings.label(host, "Daily forecast unavailable", 12, muted))
            val formatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
            for (day in data.days) {
                val date = try {
                    LocalDate.parse(day.date).format(formatter)
                } catch (_: DateTimeException) {
                    day.date
                }
                row(date + String.format(locale, " · %.0f° / %.0f°", day.high, day.low),
                    LandscapeModel.condition(day.code) + rain(day.rain))
            }
            rows.addView(GlassSettings.label(host, "Weather data: ${data.source}", 10, muted))
            rows.addView(GlassSettings.label(host, host.getString(R.string.moon_image_credit), 10, muted))
        }
        if (restore && rows.childCount > 2) rows.getChildAt(2).requestFocus()
    }

    private fun rain(value: Double): String {
        return if (value.isFinite()) String.format(Locale.getDefault(), " · Rain %.0f%%", value) else ""
    }

    private fun row(title: String, detail: String) {
        rows.addView(GlassSettings.row(host, title, detail, R.drawable.ic_brightness, GlassSettings.themeAccent(host)) {})
    }
}
