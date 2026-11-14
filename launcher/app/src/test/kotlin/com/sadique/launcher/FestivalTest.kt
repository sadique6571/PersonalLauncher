package com.sadique.launcher

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalTest {

    companion object {
        private val DAY = LocalDate.of(2026, 10, 20)
    }

    @Test
    fun activeThroughTheLastDayInclusive() {
        val f = Festival.parse("Dussehra", "2026-10-21", "", "")
        assertTrue(f.active(DAY))
        assertTrue(f.active(DAY.plusDays(1)))
        assertFalse(f.active(DAY.plusDays(2)))
    }

    @Test
    fun noEndDateStaysOn() {
        assertTrue(Festival.parse("diwali", "", "", "").active(DAY.plusYears(1)))
    }

    @Test
    fun malformedValuesTurnTheSkinOff() {
        assertSame(Festival.NONE, Festival.parse("", "2026-10-21", "", ""))
        assertSame(Festival.NONE, Festival.parse("dussehra", "21/10/2026", "", ""))
        assertFalse(Festival.NONE.active(DAY))
    }

    @Test
    fun customGreetingIsSanitizedAndOverridesTheFestivalDefault() {
        assertEquals("Shubho Bijoya",
            Festival.parse("dussehra", "", "  Shubho\u202E Bijoya ", "").greetingText(null))
    }

    @Test
    fun unknownFestivalKeepsTheTimeOfDayGreeting() {
        assertEquals("", Festival.parse("onam", "", "", "").greetingText(null))
    }

    @Test
    fun accentFallsBackToDiyaGold() {
        assertEquals(Festival.DEFAULT_ACCENT, Festival.parse("dussehra", "", "", "").accentColor())
        assertEquals(0xFF102030.toInt(), Festival.parse("dussehra", "", "", "#102030").accentColor())
        assertEquals(Festival.DEFAULT_ACCENT, Festival.parse("dussehra", "", "", "gold").accentColor())
    }
}
