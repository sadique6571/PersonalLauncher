package com.sadique.launcher

import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** When Home rebuilds its tiles and when it only puts the focus back. */
class TileRefreshTest {

    @Test
    fun firstResumeBuildsTheTiles() {
        val r = TileRefresh()
        r.onStart()
        assertEquals(TileRefresh.Action.REBUILD, r.onResume(false))
    }

    @Test
    fun backFromAnAppWithNothingChangedOnlyRefocuses() {
        val r = shown()
        r.onStop()
        r.onStart()
        assertEquals(TileRefresh.Action.REFOCUS, r.onResume(false))
    }

    @Test
    fun anAppChangedInTheBackgroundRebuildsOnceOnReturn() {
        val r = shown()
        r.onStop()
        assertFalse(r.onPackagesChanged())
        assertFalse(r.onPackagesChanged())
        r.onStart()
        assertEquals(TileRefresh.Action.REBUILD, r.onResume(false))
        r.onStop()
        r.onStart()
        assertEquals(TileRefresh.Action.REFOCUS, r.onResume(false))
    }

    @Test
    fun aMissedBroadcastStillRebuilds() {
        val r = shown()
        r.onStop()
        r.onStart()
        assertEquals(TileRefresh.Action.REBUILD, r.onResume(true))
    }

    @Test
    fun anAppChangedWhileHomeIsShownRebuildsAtOnce() {
        val r = shown()
        assertTrue(r.onPackagesChanged())
        assertEquals(TileRefresh.Action.NOTHING, r.onResume(false))
    }

    @Test
    fun homeOnHomeGoesBackToTheFirstTileWithoutRebuilding() {
        val r = shown()
        r.onHomeAgain()
        assertEquals(TileRefresh.Action.REFOCUS, r.onResume(false))
    }

    @Test
    fun homePressedInAnAppRefocusesWithoutRebuilding() {
        // onNewIntent calls onHomeAgain only while Home is stopped; on screen it just focuses the first tile.
        val r = shown()
        r.onStop()
        r.onHomeAgain()
        r.onStart()
        assertEquals(TileRefresh.Action.REFOCUS, r.onResume(false))
    }

    @Test
    fun tilesDroppedForMemoryAreRebuiltOnReturn() {
        val r = shown()
        r.onStop()
        r.invalidate()
        r.onStart()
        assertEquals(TileRefresh.Action.REBUILD, r.onResume(false))
    }

    @Test
    fun pauseWithoutStopChangesNothing() {
        val r = shown()
        assertEquals(TileRefresh.Action.NOTHING, r.onResume(false))
    }

    private fun shown(): TileRefresh {
        val r = TileRefresh()
        r.onStart()
        r.onResume(false)
        return r
    }
}
