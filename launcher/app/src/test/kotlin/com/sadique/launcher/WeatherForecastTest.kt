package com.sadique.launcher

import java.io.IOException
import org.json.*
import org.junit.Test
import org.junit.Assert.*

class WeatherForecastTest {

    private companion object {
        private const val CURRENT = "{\"current\":{\"time\":\"2026-11-10T09:15\",\"temperature_2m\":24,\"weather_code\":2,\"is_day\":0}}"
        private const val FALLBACK = "{\"current_condition\":[{\"temp_C\":\"22\",\"temp_F\":\"72\",\"FeelsLikeF\":\"70\",\"humidity\":\"42\",\"windspeedKmph\":\"9\",\"weatherDesc\":[{\"value\":\"Partly cloudy\"}]}]}"
    }

    @Test fun validatesCoordinatesBeforeAnyRequest() {
        for (lat in doubleArrayOf(Double.NaN, 91.0, Double.POSITIVE_INFINITY)) try {
            WeatherForecast.fetch(lat, 0.0, false) { url -> fail("Invalid coordinates reached loader"); CURRENT }; fail()
        } catch (expected: IllegalArgumentException) { }
        val url = WeatherForecast.url(30.81, 73.45, true)
        assertTrue(url.contains("temperature_unit=fahrenheit")); assertTrue(url.contains("timezone=auto")); assertTrue(url.contains("forecast_days=7"))
    }
    @Test fun optionalObservationsStayMissingRatherThanZero() {
        val f = WeatherForecast.parse(CURRENT); assertEquals(24.0, f.temperature, 0.0); assertFalse(f.day)
        assertTrue(Double.isNaN(f.feels)); assertTrue(Double.isNaN(f.humidity)); assertTrue(Double.isNaN(f.wind))
        assertTrue(f.hours.isEmpty()); assertTrue(f.days.isEmpty())
        try { WeatherForecast.parse("{\"current\":{}}"); fail() } catch (expected: IllegalArgumentException) { }
    }
    @Test fun filtersPastAndMalformedHoursAndBoundsBothForecasts() {
        val json = JSONObject(CURRENT)
        val times = JSONArray()
        val temps = JSONArray()
        val rain = JSONArray()
        times.put("bad"); temps.put(23); rain.put(30)
        times.put("2026-11-10T08:00"); temps.put(23); rain.put(30)
        for (h in 10 until 24) { times.put(String.format(java.util.Locale.ROOT, "2026-11-10T%02d:00", h)); temps.put(h); rain.put(if (h == 10) 150 else 20) }
        json.put("hourly", JSONObject().put("time", times).put("temperature_2m", temps).put("precipitation_probability", rain))
        val dates = JSONArray()
        val low = JSONArray()
        val high = JSONArray()
        for (d in 10 until 20) { dates.put("2026-11-" + d); low.put(10); high.put(20) }
        json.put("daily", JSONObject().put("time", dates).put("temperature_2m_min", low).put("temperature_2m_max", high))
        val f = WeatherForecast.parse(json.toString()); assertEquals(12, f.hours.size); assertEquals("2026-11-10T10:00", f.hours[0].time)
        assertTrue(Double.isNaN(f.hours[0].rain)); assertEquals(7, f.days.size)
        val restored = WeatherForecast.decode(f.encode()); assertEquals(f.hours.size, restored.hours.size); assertEquals(7, restored.days.size); assertFalse(restored.day)
    }
    @Test fun fallbackHasActualCurrentDataAndNoInventedForecast() {
        val f = WeatherForecast.fetch(0.0, 0.0, true) { url -> if (url.contains("open-meteo")) throw IOException("503"); FALLBACK }
        assertEquals(72.0, f.temperature, 0.0); assertEquals(70.0, f.feels, 0.0); assertEquals(9.0, f.wind, 0.0); assertTrue(f.hours.isEmpty()); assertTrue(f.days.isEmpty())
        val cache = WeatherForecast.decode(f.encode()); assertEquals("wttr.in", cache.source); assertEquals("Partly cloudy", cache.condition); assertEquals(42.0, cache.humidity, 0.0)
    }
    @Test fun failedProvidersPreserveDiagnosis() {
        try { WeatherForecast.fetch(0.0, 0.0, false) { url -> throw IOException(url) }; fail() }
        catch (failure: IOException) { assertEquals(1, failure.suppressed.size) }
    }
}
