package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassDockLayoutTest {
    @Test fun iconDockLeavesRoomForCaptionAndShortReflection() {
        for (screen in intArrayOf(480, 540, 600, 720, 1080)) for (width in intArrayOf(100, 128, 160, 192, 256, 400)) {
            val tile = GlassDockLayout.iconTileHeight(width, screen, GlassDockLayout.captionHeight(16, 1))
            val reflection = GlassDockLayout.reflectionHeight(tile)
            assertTrue(reflection >= 16 && reflection <= 32)
            assertTrue(reflection < tile / 2)
            assertEquals(tile + reflection + 60, GlassDockLayout.dockHeight(width, screen))
            if (screen == 540) assertTrue(GlassDockLayout.dockHeight(width, screen) <= 244)
        }
    }

    @Test fun wideIconsFitTheirViewportAndKeepExternalCaptions() {
        for (screen in intArrayOf(480, 540, 600, 720, 1080)) for (width in intArrayOf(96, 128, 160, 192, 256, 400)) {
            val side = GlassDockLayout.iconSide(width, screen)
            val caption = GlassDockLayout.captionHeight(16, 1)
            assertTrue(side >= 48 && side <= 128)
            assertTrue(side <= width)
            val body = GlassDockLayout.wideBodyHeight(width, screen)
            val bodyWidth = GlassDockLayout.wideBodyWidth(width, body)
            assertEquals(body + caption, GlassDockLayout.iconTileHeight(width, screen, caption))
            assertTrue(bodyWidth <= width)
            assertEquals(1.60, bodyWidth / body.toDouble(), 0.025)
            assertTrue(GlassDockLayout.reflectionHeight(side + caption) < side / 2)
        }
        assertTrue(GlassDockLayout.captionHeight(20, 1.5f) > GlassDockLayout.captionHeight(16, 1))
        assertTrue(GlassDockLayout.iconSide(160, 540) > GlassDockLayout.iconSide(160, 480))
    }

    @Test fun compactWidthFitsEvenTheSmallestNeighbourDuringExpansion() {
        for (columns in 2..6) for (rowWidth in intArrayOf(640, 897, 1216, 1856)) for (selected in 0 until columns) {
            val safe = GlassDockLayout.compactCellWidth(rowWidth, columns)
            for (column in 0 until columns) if (column != selected) {
                val actual = rowWidth * GlassDockLayout.span(column, columns, -1, selected, 1) / GlassDockLayout.SPANS
                assertTrue(safe <= actual)
            }
        }
    }

    @Test fun expandingAcrossRowsNeverChangesRowMembershipOrOverflows() {
        for (columns in 2..6) for (from in -1 until columns * 3) for (to in -1 until columns * 3) for (tick in 0..10) for (row in 0 until 3) {
            var sum = 0
            for (col in 0 until columns) {
                val span = GlassDockLayout.span(row * columns + col, columns, from, to, tick / 10f)
                assertTrue(span > 0)
                sum += span
            }
            assertEquals(GlassDockLayout.SPANS, sum)
        }
    }

    @Test fun selectedCardIsNearlyTwiceItsNeighbourWithoutScalingText() {
        for (columns in 2..6) {
            val large = GlassDockLayout.span(0, columns, -1, 0, 1)
            val normal = GlassDockLayout.span(1, columns, -1, 0, 1)
            assertEquals(1.98, large / normal.toDouble(), 0.04)
            assertEquals(GlassDockLayout.SPANS / columns, GlassDockLayout.span(columns, columns, -1, 0, 1))
        }
    }

    @Test fun clearingFocusRestoresEqualColumns() {
        for (columns in 2..6) for (p in 0 until columns) assertEquals(GlassDockLayout.SPANS / columns, GlassDockLayout.span(p, columns, 0, -1, 1))
    }
}
