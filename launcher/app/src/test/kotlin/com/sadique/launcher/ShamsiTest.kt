package com.sadique.launcher

import org.junit.Test

import org.junit.Assert.assertArrayEquals

/** Gregorian to Solar Hijri: Nowruz, the leap-year Esfand 30, and a couple of fixed reference days. */
class ShamsiTest {

    @Test
    fun nowruzIsTheFirstOfFarvardin() {
        assertArrayEquals(intArrayOf(1400, 1, 1), Shamsi.toJalali(2021, 3, 21))
        assertArrayEquals(intArrayOf(1403, 1, 1), Shamsi.toJalali(2024, 3, 20))
        assertArrayEquals(intArrayOf(1404, 1, 1), Shamsi.toJalali(2025, 3, 21))
    }

    @Test
    fun theDayBeforeNowruzClosesEsfand() {
        // 1399 and 1403 are leap years (Esfand has 30 days), 1400 is not (29 days).
        assertArrayEquals(intArrayOf(1399, 12, 30), Shamsi.toJalali(2021, 3, 20))
        assertArrayEquals(intArrayOf(1400, 12, 29), Shamsi.toJalali(2022, 3, 20))
        assertArrayEquals(intArrayOf(1403, 12, 30), Shamsi.toJalali(2025, 3, 20))
    }

    @Test
    fun fixedReferenceDays() {
        assertArrayEquals(intArrayOf(1348, 10, 11), Shamsi.toJalali(1970, 1, 1))
        assertArrayEquals(intArrayOf(1378, 10, 11), Shamsi.toJalali(2000, 1, 1))
    }

    @Test
    fun firstSixMonthsHave31DaysAndTheNextFiveHave30() {
        assertArrayEquals(intArrayOf(1405, 6, 31), Shamsi.toJalali(2026, 9, 22))
        assertArrayEquals(intArrayOf(1405, 7, 1), Shamsi.toJalali(2026, 9, 23))
        assertArrayEquals(intArrayOf(1405, 7, 30), Shamsi.toJalali(2026, 10, 22))
        assertArrayEquals(intArrayOf(1405, 8, 1), Shamsi.toJalali(2026, 10, 23))
    }
}
