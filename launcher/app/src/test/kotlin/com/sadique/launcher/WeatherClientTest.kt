package com.sadique.launcher

import java.io.IOException
import org.junit.Test
import org.junit.Assert.*

class WeatherClientTest {

    private companion object {
        private const val PRIMARY = "{\"current\":{\"temperature_2m\":24,\"weather_code\":0}}"
        private const val FALLBACK = "{\"current_condition\":[{\"temp_C\":\"22\",\"temp_F\":\"72\",\"weatherDesc\":[{\"value\":\"Partly cloudy\"}]}]}"
    }

    @Test fun primarySuccessDoesNotCallFallback() {
        val calls = intArrayOf(0)
        assertEquals("24°C · Clear", WeatherClient.fetch(30.81, 73.45, false) { url -> calls[0]++; PRIMARY })
        assertEquals(1, calls[0])
    }
    @Test fun server503UsesSameCoordinatesOnIndependentProvider() {
        val value = WeatherClient.fetch(30.81, 73.45, false) { url ->
            if (url.startsWith("https://api.open-meteo.com/")) throw IOException("HTTP 503")
            assertEquals("https://wttr.in/30.81,73.45?format=j1&lang=en", url)
            FALLBACK
        }
        assertEquals("22°C · Partly cloudy", value)
    }
    @Test fun fallbackHonoursFahrenheit() {
        assertEquals("72°F · Partly cloudy", WeatherClient.parseFallback(FALLBACK, true))
    }
    @Test fun bothFailuresPreservePrimaryErrorForDiagnosis() {
        try {
            WeatherClient.fetch(0.0, 0.0, false) { url -> throw IOException(url) }
            fail("Expected provider failure")
        } catch (failed: IOException) { assertEquals(1, failed.suppressed.size) }
    }
}
