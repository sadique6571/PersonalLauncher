package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class AppGridNavigationTest {
    @Test fun footerReturnsToItsEntryAppRatherThanTheLastApp() {
        val apps = listOf("a", "b", "c", "d", "e", "f", "g", "h")
        for (columns in 2..6) for (from in apps.indices) {
            if (AppGridNavigation.verticalWithFooter(from, apps.size, columns, true, true) == apps.size)
                assertEquals(from, AppGridNavigation.footerReturn(apps, apps[from], from))
        }
        assertEquals(1, AppGridNavigation.footerReturn(apps, "b", 7))
    }

    @Test fun refreshAndReorderPreserveIdentityAndRemovedAppsHaveBoundedFallback() {
        val reordered = listOf("c", "e", "b", "a")
        assertEquals(2, AppGridNavigation.footerReturn(reordered, "b", 1))
        assertEquals(3, AppGridNavigation.footerReturn(reordered, "removed", 7))
        assertEquals(0, AppGridNavigation.footerReturn(reordered, null, -1))
        assertEquals(-1, AppGridNavigation.footerReturn(emptyList(), "b", 1))
    }

    @Test fun upReachesToolbarOnlyFromFirstRowAndDownStaysInPartialLastRow() {
        assertEquals(-1, AppGridNavigation.vertical(3, 10, 4, false))
        assertEquals(0, AppGridNavigation.vertical(4, 10, 4, false))
        assertEquals(3, AppGridNavigation.vertical(7, 10, 4, false))
        assertEquals(9, AppGridNavigation.vertical(7, 10, 4, true))
        assertEquals(8, AppGridNavigation.vertical(8, 10, 4, true))
        assertEquals(9, AppGridNavigation.vertical(9, 10, 4, true))
        assertEquals(-1, AppGridNavigation.vertical(0, 0, 4, false))
    }

    @Test fun horizontalTravelCrossesRowsInBothDirections() {
        assertEquals(4, AppGridNavigation.horizontal(3, 10, true))
        assertEquals(3, AppGridNavigation.horizontal(4, 10, false))
        var position = 9
        for (i in 8 downTo 0) {
            position = AppGridNavigation.horizontal(position, 10, false)
            assertEquals(i, position)
        }
    }

    @Test fun boundariesDoNotEscapeAndEmptyOrInvalidPositionsAreRejected() {
        assertEquals(0, AppGridNavigation.horizontal(0, 10, false))
        assertEquals(9, AppGridNavigation.horizontal(9, 10, true))
        assertEquals(0, AppGridNavigation.horizontal(0, 1, true))
        assertEquals(-1, AppGridNavigation.horizontal(0, 0, true))
        assertEquals(-1, AppGridNavigation.horizontal(-1, 10, true))
        assertEquals(-1, AppGridNavigation.horizontal(10, 10, false))
    }
}
