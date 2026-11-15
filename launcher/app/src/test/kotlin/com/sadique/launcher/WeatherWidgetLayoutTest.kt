package com.sadique.launcher

import org.junit.Test
import org.junit.Assert.*

class WeatherWidgetLayoutTest {
    @Test fun widgetOverridesWithoutChangingSavedAlignment() {
        for (saved in 0..2) {
            assertEquals(0, WeatherWidgetLayout.clockAlignment(true, saved))
            assertEquals(saved, WeatherWidgetLayout.clockAlignment(false, saved))
        }
    }
    @Test fun boundsFitThe1080pTvStageAndSmallPreviews() {
        for (width in intArrayOf(320, 700, 896, 1216, 1856)) {
            val widget = WeatherWidgetLayout.width(width, true)
            val stats = WeatherWidgetLayout.telemetryWidth(width)
            assertTrue(widget >= 180 && widget <= 260); assertTrue(stats >= 180 && stats <= 240)
            if (width >= 700) assertTrue(widget + stats + 12 + 160 < width)
            assertTrue(WeatherWidgetLayout.width(width, false) <= 300)
        }
    }
}
