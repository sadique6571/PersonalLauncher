package com.sadique.launcher

/** Weather overrides clock alignment on screen while preserving the saved preference. */
object WeatherWidgetLayout {
    @JvmStatic
    fun clockAlignment(enabled: Boolean, saved: Int): Int = if (enabled) 0 else saved.coerceIn(0, 2)

    @JvmStatic
    fun width(available: Int, glass: Boolean): Int {
        val preferred = Math.round(available * if (glass) .25f else .34f)
        return preferred.coerceIn(180, if (glass) 260 else 300)
    }

    @JvmStatic
    fun telemetryWidth(available: Int): Int = Math.round(available * .23f).coerceIn(180, 240)
}
