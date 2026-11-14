package com.sadique.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Atmospheric sky behind the weather readings and a focusable forecast shortcut. */
class WeatherWidgetView(private val host: Activity, open: Runnable) : FrameLayout(host) {
    private val prefs = ThemePreferences.get(host)
    private val scene = WeatherConditionView(host)
    private val orb = WeatherOrbView(host)
    private val copy = LinearLayout(host)
    private val city = label("", 12)
    private val temperature = label("—", 36)
    private val condition = label("Unavailable", 13)
    private val details = label("", 10)
    private var active = false
    private var last: WeatherForecast? = null
    private var lastName: String? = null
    private var lastUnit = false

    init {
        val harmony = ThemeStyle.harmony(prefs)
        clipToOutline = true
        background = (if (harmony) HarmonyVisionSurface.feature(host, 0, false)
            else GradientDrawable().apply { setColor(0xff0c2035.toInt()) }).apply {
            cornerRadius = dp(if (harmony) 28 else 22).toFloat()
        }
        addView(scene, LayoutParams(-1, -1))
        copy.orientation = LinearLayout.VERTICAL
        copy.setPadding(dp(if (harmony) 16 else 14), dp(12), dp(14), dp(12))
        city.setSingleLine()
        city.ellipsize = TextUtils.TruncateAt.END
        copy.addView(city)
        val current = LinearLayout(host).apply { gravity = Gravity.CENTER_VERTICAL }
        temperature.setTypeface(temperature.typeface, Typeface.BOLD)
        temperature.setSingleLine()
        temperature.setAutoSizeTextTypeUniformWithConfiguration(24, 36, 1, TypedValue.COMPLEX_UNIT_SP)
        current.addView(temperature, LinearLayout.LayoutParams(0, -2, 1f))
        current.addView(orb, LinearLayout.LayoutParams(dp(58), dp(54)))
        copy.addView(current, LinearLayout.LayoutParams(-1, dp(54)))
        condition.setSingleLine()
        condition.ellipsize = TextUtils.TruncateAt.END
        copy.addView(condition)
        details.maxLines = 2
        copy.addView(details, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        tooltipText = "Hourly / daily forecast"
        addView(copy, LayoutParams(-1, -2, Gravity.TOP))
        isFocusable = true
        isClickable = true
        foreground = edge(false)
        setOnFocusChangeListener { _, focused -> foreground = edge(focused) }
        setOnClickListener { open.run() }
        bind(null, prefs.getString("weather_city", LandscapeModel.CITY) ?: LandscapeModel.CITY,
            prefs.getBoolean("weather_fahrenheit", false))
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        // The decorative MATCH_PARENT layer must not determine the card's height.
        measureChildWithMargins(copy, widthSpec, 0, heightSpec, 0)
        val width = resolveSizeAndState(copy.measuredWidth, widthSpec, copy.measuredState)
        val height = resolveSizeAndState(copy.measuredHeight, heightSpec, copy.measuredState shl MEASURED_HEIGHT_STATE_SHIFT)
        setMeasuredDimension(width, height)
        scene.measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
    }

    private fun dp(n: Int): Int = GlassSettings.dp(host, n.toFloat())

    private fun label(text: String, size: Int): TextView {
        return GlassSettings.label(host, text, size, Color.WHITE).apply {
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0xcc000000.toInt())
        }
    }

    private fun edge(focused: Boolean) = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        cornerRadius = dp(if (ThemeStyle.harmony(prefs)) 28 else 22).toFloat()
        setStroke(dp(if (focused) 2 else 1), if (focused) 0xfff3fcff.toInt() else 0x55d5e4f5)
    }

    fun bind(model: WeatherForecast?, name: String, fahrenheit: Boolean) {
        if (model === last && name == lastName && fahrenheit == lastUnit) return
        last = model
        lastName = name
        lastUnit = fahrenheit
        val locale = Locale.getDefault()
        city.text = name
        temperature.text = if (model == null) "—"
            else String.format(locale, "%.0f°%s", model.temperature, if (fahrenheit) "F" else "C")
        condition.text = model?.condition ?: "Unavailable"
        if (model == null) {
            details.text = "Weather is unavailable"
        } else {
            val readings = ArrayList<String>(2)
            if (model.feels.isFinite()) readings.add(String.format(locale, "Feels like %.0f°", model.feels))
            if (model.humidity.isFinite()) readings.add(String.format(locale, "Humidity %.0f%%", model.humidity))
            val summary = readings.joinToString(" · ")
            val wind = if (model.wind.isFinite()) String.format(locale, "Wind %.0f km/h", model.wind) else ""
            details.text = if (summary.isNotEmpty() && wind.isNotEmpty()) "$summary\n$wind" else summary + wind
        }
        scene.condition(model?.code ?: -1, model?.day ?: true)
        orb.condition(model?.code ?: -1, model?.day ?: true)
        active(active)
        contentDescription = "$name. ${model?.summary(fahrenheit) ?: "Weather unavailable"}. Open weather forecast"
    }

    fun active(value: Boolean) {
        active = value
        scene.active(value && last != null, prefs.getBoolean("weather_widget_animation", true))
    }
}
