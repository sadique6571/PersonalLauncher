package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** The dock band's corners are concentric with its tiles' corners. */
class DockCornersTest {
    companion object {
        private const val DENSITY = 2f   // 1080p TV, 320 dpi
        /** activity_main.xml: 56dp root padding and 10dp grid padding on each side of a 1920 px screen. */
        private const val INNER = 1920 - 2 * 56 * 2 - 2 * 10 * 2
    }

    @Test fun bandRadiusIsTileRadiusPlusPadding() {
        assertEquals(77f, DockDecoration.radiusPx(25f, 52f), 0f)
    }

    @Test fun evenPaddingIsTheGapBetweenTilesUnlessTheEdgeIsTighter() {
        assertEquals(50f, DockDecoration.evenPaddingPx(1000, 4, 200, 80f), 0.001f)
        assertEquals(30f, DockDecoration.evenPaddingPx(1000, 4, 200, 30f), 0.001f)
        assertEquals(0f, DockDecoration.evenPaddingPx(1000, 0, 200, 30f), 0.001f)
    }

    @Test fun evenPaddingStaysPositiveAndBelowTheCellFromFourToSevenColumns() {
        for (columns in 4..7) {
            val tile = AppAdapter.tileWidthPx(INNER, columns, DENSITY)
            val gap = INNER / columns.toFloat() - tile
            val pad = DockDecoration.evenPaddingPx(INNER, columns, tile, Float.MAX_VALUE)
            assertEquals("columns " + columns, gap, pad, 0.001f)
            val radius = DockDecoration.radiusPx(AppAdapter.cornerRadius(tile, DENSITY), pad)
            assertEquals("columns " + columns, pad, radius - AppAdapter.cornerRadius(tile, DENSITY), 0.001f)
        }
    }
}
