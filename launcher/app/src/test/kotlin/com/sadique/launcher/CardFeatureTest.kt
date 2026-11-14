package com.sadique.launcher

import java.util.ArrayList
import java.util.Collections
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The guard that keeps a failing module card feature from taking the home screen down. */
class CardFeatureTest {

    private val reported = ArrayList<Throwable>()

    @Before
    fun setUp() {
        CardFeature.reset()
        CardFeature.reporter = CardFeature.Reporter { reported.add(it) }
    }

    @After
    fun tearDown() {
        CardFeature.reset()
    }

    @Test
    fun aWorkingStepReturnsItsValue() {
        assertEquals("ok", CardFeature.run({ "ok" }, "fallback"))
        assertTrue(CardFeature.working())
        assertTrue(reported.isEmpty())
    }

    @Test
    fun anExceptionGivesTheFallbackAndSwitchesCardsOff() {
        val none: List<String> = Collections.emptyList()
        val tiles = CardFeature.run({ throw IllegalStateException("bad store") }, none)
        assertEquals(none, tiles)                    // the adapter gets no cards: ordinary tiles only
        assertFalse(CardFeature.working())
        assertEquals(1, reported.size)
        // From now on every step is skipped, even one that would work, and nothing more is logged.
        assertEquals("fallback", CardFeature.run({ "would work" }, "fallback"))
        assertEquals(1, reported.size)
    }

    @Test
    fun aClassThatFailsToInitialiseIsCaught() {
        assertEquals("fallback", CardFeature.run({ throw ExceptionInInitializerError(RuntimeException("regex rejected by this runtime")) }, "fallback"))
        assertFalse(CardFeature.working())
        CardFeature.reset()
        assertEquals("fallback", CardFeature.run({ throw NoClassDefFoundError("com.sadique.launcher.Cards") }, "fallback"))
        assertFalse(CardFeature.working())
        assertEquals(2, reported.size)
    }

    @Test
    fun outOfMemoryIsNotSwallowed() {
        try {
            CardFeature.run({ throw OutOfMemoryError() }, "fallback")
            fail()
        } catch (expected: OutOfMemoryError) {
        }
        assertTrue(CardFeature.working())
    }

    @Test
    fun aFailingReporterDoesNotMatter() {
        CardFeature.reporter = CardFeature.Reporter { throw RuntimeException("log unavailable") }
        assertEquals("fallback", CardFeature.run({ throw RuntimeException() }, "fallback"))
        assertFalse(CardFeature.working())
    }
}
