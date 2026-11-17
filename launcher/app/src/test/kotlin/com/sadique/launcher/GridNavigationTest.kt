package com.sadique.launcher

import java.util.ArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridNavigationTest {
    @Test fun hebrewDockStartsOnTheRightAndStopsAtTheLeft() {
        assertTrue(GridNavigation.atHorizontalEdge(0, 11, 4, true, false))
        assertFalse(GridNavigation.atHorizontalEdge(0, 11, 4, true, true))
        assertTrue(GridNavigation.atHorizontalEdge(3, 11, 4, true, true))
        assertFalse(GridNavigation.atHorizontalEdge(3, 11, 4, true, false))
    }

    @Test fun incompleteRightToLeftLastRowDoesNotWrapIntoEmptySpace() {
        assertTrue(GridNavigation.atHorizontalEdge(8, 11, 4, true, false))
        assertTrue(GridNavigation.atHorizontalEdge(10, 11, 4, true, true))
        assertFalse(GridNavigation.atHorizontalEdge(9, 11, 4, true, true))
        assertFalse(GridNavigation.atHorizontalEdge(9, 11, 4, true, false))
    }

    @Test fun originalLeftToRightBoundariesArePreserved() {
        assertTrue(GridNavigation.atHorizontalEdge(0, 11, 4, false, true))
        assertTrue(GridNavigation.atHorizontalEdge(3, 11, 4, false, false))
        assertTrue(GridNavigation.atHorizontalEdge(10, 11, 4, false, false))
        assertFalse(GridNavigation.atHorizontalEdge(1, 11, 4, false, true))
        assertFalse(GridNavigation.atHorizontalEdge(1, 11, 4, false, false))
    }

    @Test fun singleColumnHasBothHorizontalEdges() {
        assertTrue(GridNavigation.atHorizontalEdge(1, 3, 1, true, true))
        assertTrue(GridNavigation.atHorizontalEdge(1, 3, 1, true, false))
        assertTrue(GridNavigation.atHorizontalEdge(1, 3, 1, false, true))
        assertTrue(GridNavigation.atHorizontalEdge(1, 3, 1, false, false))
    }

    @Test fun horizontalEdgesMatchExplicitRowsAcrossColumnCountsAndDirections() {
        for (grid in grids()) {
            for (rtl in booleanArrayOf(false, true)) {
                for (position in 0 until grid.count) {
                    for (left in booleanArrayOf(false, true)) {
                        val expected = isPhysicalEdge(grid.rows, position, rtl, left)
                        val message = "count=" + grid.count + ", columns=" + grid.columns +
                            ", position=" + position + ", rtl=" + rtl +
                            ", direction=" + if (left) "left" else "right"
                        assertEquals(message, expected, GridNavigation.atHorizontalEdge(
                            position, grid.count, grid.columns, rtl, left))
                    }
                }
            }
        }
    }

    @Test fun sparseDockRedirectsEmptySlotsAndKeepsGridRowBoundary() {
        val dockApps = 2
        val columns = 6
        val gridApps = 12
        val emptySlots = DockSlots.empty(dockApps, columns, dockApps + gridApps, false)
        val totalSlots = dockApps + gridApps + emptySlots
        assertEquals("two occupied dock slots leave four empty slots", 4, emptySlots)
        for (position in dockApps until dockApps + emptySlots) {
            assertEquals("empty dock position=" + position, dockApps - 1,
                DockSlots.focusable(position, dockApps, emptySlots))
        }
        assertEquals("occupied dock position remains focused", 0,
            DockSlots.focusable(0, dockApps, emptySlots))
        assertEquals("first grid app remains focused", dockApps + emptySlots,
            DockSlots.focusable(dockApps + emptySlots, dockApps, emptySlots))

        for (rtl in booleanArrayOf(false, true)) {
            val dockStartIsLeft = !rtl
            assertTrue("dock count=" + dockApps + ", columns=" + columns +
                ", position=0, rtl=" + rtl + ", direction=" +
                if (dockStartIsLeft) "left" else "right",
                GridNavigation.atHorizontalEdge(0, dockApps, columns, rtl, dockStartIsLeft))
            assertFalse("dock count=" + dockApps + ", columns=" + columns +
                ", position=0, rtl=" + rtl + ", direction=" +
                if (dockStartIsLeft) "right" else "left",
                GridNavigation.atHorizontalEdge(0, dockApps, columns, rtl, !dockStartIsLeft))
            assertTrue("dock count=" + dockApps + ", columns=" + columns +
                ", position=1, rtl=" + rtl + ", direction=" +
                if (dockStartIsLeft) "right" else "left",
                GridNavigation.atHorizontalEdge(1, dockApps, columns, rtl, !dockStartIsLeft))
            assertFalse("dock count=" + dockApps + ", columns=" + columns +
                ", position=1, rtl=" + rtl + ", direction=" +
                if (dockStartIsLeft) "left" else "right",
                GridNavigation.atHorizontalEdge(1, dockApps, columns, rtl, dockStartIsLeft))

            // MainActivity must pass the reduced dock count to keep the outward dock edge here.
            // This helper contract does not exercise that wiring.
            assertTrue("grid count=" + totalSlots + ", columns=" + columns +
                ", position=" + columns + ", rtl=" + rtl +
                ", direction=" + if (dockStartIsLeft) "left" else "right",
                GridNavigation.atHorizontalEdge(columns, totalSlots, columns, rtl, dockStartIsLeft))
            assertFalse("grid count=" + totalSlots + ", columns=" + columns +
                ", position=" + columns + ", rtl=" + rtl +
                ", direction=" + if (dockStartIsLeft) "right" else "left",
                GridNavigation.atHorizontalEdge(columns, totalSlots, columns, rtl, !dockStartIsLeft))
        }
    }

    companion object {
        private fun grids(): List<GridCase> {
            val grids = ArrayList<GridCase>()
            for (columns in 1..8) {
                grids.add(GridCase(columns, 1))
                grids.add(GridCase(columns, columns))
                grids.add(GridCase(columns, columns, 1))
                grids.add(GridCase(columns, columns, columns))
            }
            return grids
        }

        private fun isPhysicalEdge(rows: List<List<Int>>, position: Int,
            rtl: Boolean, left: Boolean): Boolean {
            for (row in rows) {
                val leftEdge = if (rtl) row[row.size - 1] else row[0]
                val rightEdge = if (rtl) row[0] else row[row.size - 1]
                if (position == if (left) leftEdge else rightEdge) return true
            }
            return false
        }
    }

    /** Row lengths declare the expected groups; positions are assigned in reading order. */
    private class GridCase(val columns: Int, vararg rowLengths: Int) {
        val rows = ArrayList<List<Int>>()
        val count: Int

        init {
            var nextPosition = 0
            for (rowLength in rowLengths) {
                val row = ArrayList<Int>()
                for (i in 0 until rowLength) row.add(nextPosition++)
                rows.add(row)
            }
            count = nextPosition
        }
    }
}
