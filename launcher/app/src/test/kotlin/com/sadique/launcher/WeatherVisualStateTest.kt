package com.sadique.launcher

import org.junit.Test
import org.junit.Assert.*

class WeatherVisualStateTest {
    @Test fun everySupportedWmoCodeUsesTheCorrectAtmosphere() {
        val groups = arrayOf(intArrayOf(0, 1), intArrayOf(2), intArrayOf(3), intArrayOf(45, 48), intArrayOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82), intArrayOf(71, 73, 75, 77, 85, 86), intArrayOf(95, 96, 99))
        for (kind in groups.indices) for (code in groups[kind]) assertEquals("WMO " + code, kind + 1, WeatherVisualState.kind(code))
    }
    @Test fun unknownAndDryConditionsNeverInventPrecipitation() {
        for (code in intArrayOf(-1, 4, 50, 60, 70, 78, 83, 94, 97, 100, Integer.MAX_VALUE)) { assertEquals(WeatherVisualState.UNKNOWN, WeatherVisualState.kind(code)); assertEquals(0, WeatherVisualState.drops(code)) }
        for (code in intArrayOf(0, 1, 2, 3, 45, 48, 71, 75, 85)) assertEquals(0, WeatherVisualState.drops(code))
    }
    @Test fun rainIntensityIsBoundedAndDistinguishesDrizzle() {
        assertTrue(WeatherVisualState.drops(51) < WeatherVisualState.drops(63))
        assertTrue(WeatherVisualState.drops(63) < WeatherVisualState.drops(65))
        for (code in 0 until 110) assertTrue(WeatherVisualState.drops(code) <= 42)
    }
}
