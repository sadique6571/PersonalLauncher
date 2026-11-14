package com.sadique.launcher

import java.time.Duration
import java.time.Instant
import java.util.Arrays
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDayTest {
    companion object {
        private val AUSTIN = RaceWeekend.Race(18, "United States Grand Prix", "Austin",
            Arrays.asList(
                RaceWeekend.Session("FP1", Instant.parse("2026-10-23T17:30:00Z")),
                RaceWeekend.Session("FP2", Instant.parse("2026-10-23T21:00:00Z")),
                RaceWeekend.Session("FP3", Instant.parse("2026-10-24T17:30:00Z")),
                RaceWeekend.Session("Qualifying", Instant.parse("2026-10-24T21:00:00Z")),
                RaceWeekend.Session("race", Instant.parse("2026-10-25T20:00:00Z"))))
        private val CALENDAR = Collections.singletonList(AUSTIN)
        /** Saturday afternoon: FP3 is over, qualifying is in 2h 05m. */
        private val NOW = Instant.parse("2026-10-24T18:55:00Z")
        private const val HOUR = 3_600_000L

        private fun game(top: String, fromNowMs: Long, lengthMs: Long): GameDay.Turn {
            val start = NOW.toEpochMilli() + fromNowMs
            return GameDay.Turn(top, "Puck drop", start, start + lengthMs, 0xFF2E7D32.toInt())
        }
    }

    @Test fun noTurnsHidesPanel() {
        assertTrue(GameDay.merge(Collections.emptyList(), null, NOW.toEpochMilli()).isEmpty())
    }

    @Test fun endedGamesAreDropped() {
        val t = GameDay.merge(Arrays.asList(
            game("Ended", -4 * HOUR, 3 * HOUR), game("Tonight", HOUR, 3 * HOUR)), null, NOW.toEpochMilli())
        assertEquals(1, t.size)
        assertEquals("Tonight", t[0].topLine)
    }

    @Test fun f1JoinsSortedByItsNextSession() {
        val f1 = RaceWeekend.at(CALENDAR, NOW)
        val t = GameDay.merge(Arrays.asList(
            game("In two days", 48 * HOUR, 3 * HOUR), game("In an hour", HOUR, 3 * HOUR)), f1, NOW.toEpochMilli())
        assertEquals(3, t.size)
        assertEquals("In an hour", t[0].topLine)
        assertTrue(t[1].f1)
        assertEquals(Instant.parse("2026-10-24T21:00:00Z").toEpochMilli(), t[1].startMs)
        assertEquals("In two days", t[2].topLine)
    }

    @Test fun f1LiveSortsFirst() {
        val duringQualifying = Instant.parse("2026-10-24T21:20:00Z")
        val f1 = RaceWeekend.at(CALENDAR, duringQualifying)
        val soon = duringQualifying.toEpochMilli() + 30 * 60_000L
        val t = GameDay.merge(Arrays.asList(
            GameDay.Turn("Soon", "Puck drop", soon, soon + 3 * HOUR, 0)), f1, duringQualifying.toEpochMilli())
        assertTrue(t[0].f1)
        assertEquals(duringQualifying.toEpochMilli(), t[0].startMs)
    }

    @Test fun noF1OutsideItsWeekend() {
        val none = RaceWeekend.at(CALENDAR, Instant.parse("2026-11-10T12:00:00Z"))
        assertEquals(1, GameDay.merge(Arrays.asList(game("Tonight", HOUR, 3 * HOUR)), none, NOW.toEpochMilli()).size)
    }

    @Test fun rotationIndex() {
        assertEquals(0, GameDay.current(3, 29_999, 30))
        assertEquals(1, GameDay.current(3, 31_000, 30))
        assertEquals(0, GameDay.current(3, 90_000, 30))
        assertEquals(0, GameDay.current(0, 5_000, 30))
        assertEquals(0, GameDay.current(1, 500_000, 30))
    }

    @Test fun nextTurnIsNeverMoreThanOneTurnAway() {
        assertEquals(30_000, GameDay.delayToNextTurn(0, 30))
        assertEquals(1_000, GameDay.delayToNextTurn(29_000, 30))
        assertEquals(30_000, GameDay.delayToNextTurn(60_000, 30))
        // A clock set back while Home is open must not stretch the turn.
        assertTrue(GameDay.delayToNextTurn(-45_000, 30) <= 30_000)
        assertTrue(GameDay.delayToNextTurn(-45_000, 30) > 0)
    }

    @Test fun turnIsLiveOnlyBetweenStartAndEnd() {
        val t = game("Game", HOUR, 3 * HOUR)
        assertFalse(GameDay.live(t, t.startMs - 1))
        assertTrue(GameDay.live(t, t.startMs))
        assertTrue(GameDay.live(t, t.endMs - 1))
        assertFalse(GameDay.live(t, t.endMs))
    }

    @Test fun rowsAreParsedAndBadOnesSkipped() {
        val rows = Arrays.asList(
            arrayOf<Any?>("Minnesota Wild at Dallas Stars", "Puck drop", 1000L, 5000L, 0xFF2E7D32.toInt(), "Minnesota Wild", 45),
            arrayOf<Any?>(null, "Puck drop", 1000L, 5000L, 0, "x", 45),
            arrayOf<Any?>("Bad start", "Kickoff", -1L, 5000L, 0, "x", 45),
            arrayOf<Any?>("Ends first", "Kickoff", 5000L, 5000L, 0, "x", 45),
            arrayOf<Any?>("No word", null, 2000L, 6000L, 0xFF1565C0.toInt(), "x", 45))
        val t = GameDay.fromRows(rows)
        assertEquals(2, t.size)
        assertEquals("Minnesota Wild at Dallas Stars", t[0].topLine)
        assertEquals("Puck drop", t[0].word)
        assertEquals(1000L, t[0].startMs)
        assertEquals(5000L, t[0].endMs)
        assertEquals(0xFF2E7D32.toInt(), t[0].colour)
        assertEquals("", t[1].word)
        assertEquals(45, GameDay.turnSeconds(rows))
    }

    @Test fun turnSecondsOutOfRangeBecomesThirty() {
        assertEquals(30, GameDay.turnSeconds(Collections.singletonList(arrayOf<Any?>("a", "b", 1L, 2L, 0, "c", 500))))
        assertEquals(30, GameDay.turnSeconds(Collections.singletonList(arrayOf<Any?>("a", "b", 1L, 2L, 0, "c", 3))))
        assertEquals(30, GameDay.turnSeconds(Collections.emptyList()))
    }

    @Test fun durationHelperMatchesTheCountdown() {
        assertEquals(Duration.ofMinutes(125), GameDay.until(game("Game", 125 * 60_000L, HOUR), NOW.toEpochMilli()))
    }
}
