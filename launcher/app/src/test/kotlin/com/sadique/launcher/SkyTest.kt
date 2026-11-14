package com.sadique.launcher

import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

/** Plain JVM tests for the sunset palette: anchors, interpolation and clamping. */
class SkyTest {

    @Test
    fun nightLooksTheSameAtBothEndsOfTheDay() {
        val first = Sky.at(0)
        val last = Sky.at(1439)
        assertEquals(first.top, last.top)
        assertEquals(first.mid, last.mid)
        assertEquals(first.bottom, last.bottom)
        assertEquals(0, last.sun)
    }

    @Test
    fun glowPeaksAtSunriseAndSunset() {
        assertTrue(Sky.at(450).sun > Sky.at(780).sun)       // 07:30 sunrise vs 13:00 day
        assertTrue(Sky.at(1140).sun > Sky.at(1260).sun)     // 19:00 sunset vs 21:00 dusk
        assertTrue(Sky.at(1140).sun > Sky.at(450).sun)      // the evening is the warmest moment
        assertEquals(0, Sky.at(60).sun)
        assertEquals(0, Sky.at(1400).sun)
    }

    @Test
    fun theGlowTracksTheSunFromLeftToRight() {
        assertTrue(Sky.at(390).sunX < Sky.at(780).sunX)
        assertTrue(Sky.at(780).sunX < Sky.at(1140).sunX)
        assertTrue(Sky.at(780).sunX > 0.4f && Sky.at(780).sunX < 0.6f)   // midday is near the centre
    }

    @Test
    fun coloursInterpolateBetweenAnchors() {
        val dawn = Sky.at(390)
        val sunrise = Sky.at(450)
        val half = Sky.at(420)
        assertEquals(Sky.lerpColor(dawn.top, sunrise.top, 0.5f), half.top)
        assertNotEquals(dawn.top, half.top)
        assertNotEquals(sunrise.top, half.top)
    }

    @Test
    fun timeIsClampedAndAnsweredEveryMinuteOfTheDay() {
        assertEquals(Sky.at(0).top, Sky.at(-30).top)
        assertEquals(Sky.at(1440).top, Sky.at(4000).top)
        for (minute in 0 until 1440 step 5) {
            val p = Sky.at(minute)
            val top = luma(p.top)
            val mid = luma(p.mid)
            // The header and the clock sit on the top two thirds; they stay on dark ground all day.
            assertTrue("top too bright at " + minute, top < 0.4)
            assertTrue("mid too bright at " + minute, mid < 0.4)
        }
        assertEquals(1439, Sky.minuteOfDay(23, 59))
        assertEquals(0, Sky.minuteOfDay(0, 0))
    }

    /** Rough relative brightness of an opaque ARGB colour, 0 to 1. */
    private fun luma(color: Int): Double {
        return (0.2126 * ((color shr 16) and 0xFF) + 0.7152 * ((color shr 8) and 0xFF) + 0.0722 * (color and 0xFF)) / 255.0
    }
}
