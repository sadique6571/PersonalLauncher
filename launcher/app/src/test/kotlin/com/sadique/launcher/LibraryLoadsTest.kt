package com.sadique.launcher

import org.junit.Test
import org.junit.Assert.*

class LibraryLoadsTest {
    @Test fun aRefreshDuringTheOpeningStillRevealsOnce() {
        val loads = LibraryLoads()
        val opening = loads.start(true)
        val refresh = loads.start(false)   // a package changed before the opening's result came back
        assertFalse(loads.isLatest(opening))
        assertTrue(loads.isLatest(refresh))
        assertTrue(loads.takeReveal())
        assertFalse(loads.takeReveal())
    }

    @Test fun aRefreshOnItsOwnNeverReveals() {
        val loads = LibraryLoads()
        assertTrue(loads.isLatest(loads.start(false)))
        assertFalse(loads.takeReveal())
    }

    @Test fun anOpeningAfterARefreshRevealsWithItsOwnLoad() {
        val loads = LibraryLoads()
        val refresh = loads.start(false)
        val opening = loads.start(true)
        assertFalse(loads.isLatest(refresh))
        assertTrue(loads.isLatest(opening))
        assertTrue(loads.takeReveal())
    }

    @Test fun refreshesAfterTheRevealDoNotRevealAgain() {
        val loads = LibraryLoads()
        loads.start(true)
        assertTrue(loads.takeReveal())
        loads.start(false)
        assertFalse(loads.takeReveal())
    }
}
