package com.sadique.launcher

import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** AppAdapter.tileWidthPx with rows_below_dock: the dock, its title and the rows must fit the grid's height. */
class TileFitTest {

    private companion object {
        private const val D = 2f              // 1080p TV: 2 px per dp
        private const val INNER = 1696          // grid width minus padding at 1920 px
        private const val GRID_H = 846          // grid height under the top bar
    }

    private fun height(tileW: Int, rows: Int, title: Boolean): Int {
        val itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(tileW), D)
        return itemH * (rows + 1) + Math.round((AppAdapter.DOCK_EXTRA_DP + AppAdapter.BOTTOM_ROOM_DP
                + if (title) AppAdapter.TITLE_ROOM_DP else 0f) * D)
    }

    @Test
    fun noRowsBelowDockKeepsTheWidthOnlySize() {
        val widthOnly = AppAdapter.tileWidthPx(INNER, 4, D)
        assertEquals(widthOnly, AppAdapter.tileWidthPx(INNER, GRID_H, 4, 0, true, true, D))
        assertEquals(widthOnly, AppAdapter.tileWidthPx(INNER, 0, 4, 3, true, true, D))   // not laid out yet
    }

    @Test
    fun dockTitleAndRowsFitTheHeightUnlessThatMeansTinyTiles() {
        val floor = Math.round(AppAdapter.tileWidthPx(INNER, 4, D) * AppAdapter.MIN_HEIGHT_FIT)
        for (rows in 1..3) {
            for (title in booleanArrayOf(true, false)) {
                val w = AppAdapter.tileWidthPx(INNER, GRID_H, 4, rows, title, true, D)
                assertTrue("rows " + rows + " title " + title + " below the floor", w >= floor)
                if (w > floor) assertTrue("rows " + rows + " title " + title, height(w, rows, title) <= GRID_H + 2)
            }
        }
    }

    @Test
    fun oneRowUnderATitledDockFits() {
        val w = AppAdapter.tileWidthPx(INNER, GRID_H, 4, 1, true, true, D)
        assertTrue(w < AppAdapter.tileWidthPx(INNER, 4, D))
        assertTrue(height(w, 1, true) <= GRID_H + 2)
    }

    @Test
    fun moreRowsStopAtTheFloorInsteadOfShrinkingTheTiles() {
        // Two or three rows under a titled dock would need tiles under half their size: they stop at 85% and
        // the extra rows run off the bottom instead.
        val floor = Math.round(AppAdapter.tileWidthPx(INNER, 4, D) * AppAdapter.MIN_HEIGHT_FIT)
        assertEquals(floor, AppAdapter.tileWidthPx(INNER, GRID_H, 4, 2, true, true, D))
        assertEquals(floor, AppAdapter.tileWidthPx(INNER, GRID_H, 4, 3, true, true, D))
    }

    @Test
    fun plainGridFitsFourRowsAtSixColumns() {
        // dock_row false, rows_below_dock 3: the first row and 3 more under a 12dp top margin.
        val w = AppAdapter.tileWidthPx(INNER, GRID_H, 6, 3, true, false, D)
        val itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(w), false, D)
        val needed = itemH * 4 + Math.round((AppAdapter.TOP_ROOM_DP + AppAdapter.COMPACT_BOTTOM_ROOM_DP) * D)
        assertTrue("$needed > $GRID_H", needed <= GRID_H + 2)
        // With the compact spacing the width limit decides, not the height: no extra whitespace between tiles.
        assertEquals(AppAdapter.tileWidthPx(INNER, 6, D), w)
    }

    @Test
    fun droppingTheTitleGivesBiggerTiles() {
        assertTrue(AppAdapter.tileWidthPx(INNER, GRID_H, 4, 1, false, true, D)
                > AppAdapter.tileWidthPx(INNER, GRID_H, 4, 1, true, true, D))
    }
}
