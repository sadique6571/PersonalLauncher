package com.sadique.launcher

import java.time.LocalDate
import java.util.Calendar
import java.util.HashSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarmonyDailyWordsTest {
    @Test fun dailyWordsAreOfflineStableAndChangeEveryLocalDay() {
        val first = LocalDate.of(2026, 11, 11)
        assertTrue(HarmonyDailyWords.count() >= 90)
        assertEquals(HarmonyDailyWords.forDay(first), HarmonyDailyWords.forDay(first))
        for (i in 0 until 400) {
            val a = HarmonyDailyWords.forDay(first.plusDays(i.toLong()))
            val b = HarmonyDailyWords.forDay(first.plusDays((i + 1).toLong()))
            assertNotEquals("Same text on consecutive days", a, b)
            assertTrue(a.length <= 64)
        }
    }

    @Test fun fullCycleHasNoDuplicateText() {
        val seen = HashSet<String>()
        val start = LocalDate.of(2026, 1, 1)
        for (i in 0 until HarmonyDailyWords.count())
            assertTrue(seen.add(HarmonyDailyWords.forDay(start.plusDays(i.toLong()))))
    }

    @Test fun usesDeviceLocalCalendarDateAcrossYearBoundary() {
        val c = Calendar.getInstance()
        c.clear()
        c.set(2026, Calendar.DECEMBER, 31, 23, 59, 0)
        assertEquals(HarmonyDailyWords.forDay(LocalDate.of(2026, 12, 31)), HarmonyDailyWords.forCalendar(c))
        c.add(Calendar.MINUTE, 1)
        assertEquals(HarmonyDailyWords.forDay(LocalDate.of(2027, 1, 1)), HarmonyDailyWords.forCalendar(c))
    }
}
