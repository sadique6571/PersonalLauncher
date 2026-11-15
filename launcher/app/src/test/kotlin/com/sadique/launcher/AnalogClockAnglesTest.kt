package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalogClockAnglesTest {
    @Test fun secondsMoveBetweenTicks() {
        assertEquals(61.5f, AnalogClockAngles.seconds(10, 250), 0.0001f)
        assertTrue(AnalogClockAngles.seconds(10, 33) > AnalogClockAngles.seconds(10, 0))
        assertEquals(0.006f, 360 - AnalogClockAngles.seconds(59, 999), 0.0001f)
        assertEquals(0f, AnalogClockAngles.seconds(0, 0), 0f)
    }

    @Test fun minuteAndHourHandsAlsoSweep() {
        assertEquals(3.05f, AnalogClockAngles.minutes(0, 30, 500), 0.0001f)
        assertEquals(97.75417f, AnalogClockAngles.hours(3, 15, 30, 500), 0.0001f)
        assertEquals(AnalogClockAngles.hours(0, 0, 0, 0), AnalogClockAngles.hours(12, 0, 0, 0), 0f)
    }
}
