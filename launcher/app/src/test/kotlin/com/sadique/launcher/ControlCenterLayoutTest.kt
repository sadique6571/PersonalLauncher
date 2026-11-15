package com.sadique.launcher

import java.util.Arrays
import java.util.HashSet
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCenterLayoutTest {
    @Test fun everyThemeHasDistinctLayoutAndEveryQuickActionExactlyOnce() {
        val arrangements = HashSet<String>()
        for (style in 0 until ThemeStyle.COUNT) {
            val rows = ControlCenterLayout.rows(style)
            assertTrue("Theme repeats another layout: " + style, arrangements.add(Arrays.deepToString(rows)))
            val seen = BooleanArray(6)
            for (row in rows) {
                assertTrue(row.size >= 1 && row.size <= 3)
                for (action in row) {
                    assertTrue(action >= 0 && action < 6)
                    assertFalse("Duplicate action", seen[action])
                    seen[action] = true
                }
            }
            for (present in seen) assertTrue("Missing quick action", present)
        }
    }

    @Test fun harmonyVisionHasItsOwnRemoteFriendlyControls() {
        assertArrayEquals(intArrayOf(0, 1), ControlCenterLayout.rows(ThemeStyle.HARMONY)[0])
        assertArrayEquals(intArrayOf(2, 4), ControlCenterLayout.rows(ThemeStyle.HARMONY)[1])
        assertArrayEquals(intArrayOf(3, 5), ControlCenterLayout.rows(ThemeStyle.HARMONY)[2])
        assertEquals(28, ControlCenterLayout.radius(ThemeStyle.HARMONY))
        assertEquals("appearance.harmony.", ThemeStyle.prefix(ThemeStyle.HARMONY))
        assertEquals(ThemeStyle.HARMONY, ThemeStyle.clamp(ThemeStyle.HARMONY))
    }

    @Test fun verticalNavigationStaysInAdjacentRowAndMaintainsLeftToRightOrder() {
        for (count in 1..3) for (target in 1..3) {
            var previous = -1
            for (col in 0 until count) {
                val next = ControlCenterLayout.neighbour(col, count, target)
                assertTrue(next >= 0 && next < target)
                assertTrue(next >= previous)
                previous = next
                if (count == target) assertEquals(col, next)
            }
        }
    }
}
