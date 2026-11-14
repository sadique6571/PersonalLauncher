package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

import java.time.Duration
import java.time.Instant
import java.util.Arrays
import java.util.Collections
import java.util.List

import org.junit.Test

class RaceWeekendTest {

    private companion object {
        // The 2026 United States GP, times as Jolpica gives them (UTC).
        private val AUSTIN = RaceWeekend.Race(18, "United States Grand Prix", "Austin",
                Arrays.asList(
                        // Out of order on purpose: the calendar is sorted by start time.
                        RaceWeekend.Session("race", Instant.parse("2026-10-25T20:00:00Z")),
                        RaceWeekend.Session("FP1", Instant.parse("2026-10-23T17:30:00Z")),
                        RaceWeekend.Session("FP2", Instant.parse("2026-10-23T21:00:00Z")),
                        RaceWeekend.Session("FP3", Instant.parse("2026-10-24T17:30:00Z")),
                        RaceWeekend.Session("Qualifying", Instant.parse("2026-10-24T21:00:00Z"))))
        private val CALENDAR: List<RaceWeekend.Race> = Collections.singletonList(AUSTIN)
    }

    @Test
    fun nothingBeforeTheWeekendOpens() {
        assertNull(RaceWeekend.at(CALENDAR, Instant.parse("2026-10-23T05:29:00Z")))
    }

    @Test
    fun opensTwelveHoursBeforeFirstPractice() {
        val s = RaceWeekend.at(CALENDAR, Instant.parse("2026-10-23T05:30:00Z"))!!
        assertSame(AUSTIN, s.race)
        assertEquals("FP1", s.next!!.name)
        assertEquals(Duration.ofHours(12), s.untilNext)
        assertEquals(RaceWeekend.Status.NEXT, s.statuses[0])
        assertEquals(RaceWeekend.Status.LATER, s.statuses[4])
    }

    @Test
    fun aSessionOnTrackIsLiveAndTheNextOneIsCountedDown() {
        val s = RaceWeekend.at(CALENDAR, Instant.parse("2026-10-24T21:30:00Z"))!!
        assertEquals("QUALIFYING", s.live!!.name)
        assertTrue(s.next!!.isRace())
        assertEquals(Arrays.asList(RaceWeekend.Status.DONE, RaceWeekend.Status.DONE, RaceWeekend.Status.DONE,
                RaceWeekend.Status.LIVE, RaceWeekend.Status.NEXT), s.statuses)
    }

    @Test
    fun theRaceIsLiveForTwoHoursThenFinished() {
        val live = RaceWeekend.at(CALENDAR, Instant.parse("2026-10-25T21:59:00Z"))!!
        assertTrue(live.live!!.isRace())
        assertNull(live.next)
        val done = RaceWeekend.at(CALENDAR, Instant.parse("2026-10-25T22:00:00Z"))!!
        assertTrue(done.finished())
    }

    @Test
    fun closesFourHoursAfterTheRaceStarts() {
        assertTrue(RaceWeekend.at(CALENDAR, Instant.parse("2026-10-25T23:59:00Z"))!!.finished())
        assertNull(RaceWeekend.at(CALENDAR, Instant.parse("2026-10-26T00:00:00Z")))
    }

}
