package com.sadique.launcher

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HijriCalendarTest {
    @Test fun islamicDatesUseHijriMonthsAndYears() {
        assertEquals("1 Ramadan 1445 AH", HijriCalendar.format(LocalDate.of(2024, 3, 11), 0))
        assertEquals("1 Shawwal 1445 AH", HijriCalendar.format(LocalDate.of(2024, 4, 10), 0))
    }

    @Test fun localAdjustmentCrossesMonthBoundariesAndIsBounded() {
        val date = LocalDate.of(2024, 3, 11)
        assertEquals(HijriCalendar.format(date.minusDays(1), 0), HijriCalendar.format(date, -1))
        assertEquals("2 Ramadan 1445 AH", HijriCalendar.format(date, 1))
        assertEquals(HijriCalendar.format(date, 2), HijriCalendar.format(date, 100))
        assertEquals(HijriCalendar.format(date, -2), HijriCalendar.format(date, -100))
    }
}
