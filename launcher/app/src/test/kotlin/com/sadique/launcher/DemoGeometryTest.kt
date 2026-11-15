package com.sadique.launcher

import java.util.Arrays
import java.util.HashSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoGeometryTest {
    @Test fun everyDemoOutlineIsClosedAndInsideTheArtworkBounds() {
        val shapes = HashSet<String>()
        for (style in ThemeStyle.DUNE until ThemeStyle.COUNT) {
            val outline = DemoGeometry.outline(style, 320f, 123f, 16f)
            assertEquals(0, outline[0][0].toInt())
            assertEquals(3, outline[outline.size - 1][0].toInt())
            for (segment in outline) for (i in 1 until segment.size step 2) {
                assertTrue(segment[i].isFinite())
                assertTrue(segment[i + 1].isFinite())
                assertTrue(segment[i] >= 0 && segment[i] <= 320)
                assertTrue(segment[i + 1] >= 0 && segment[i + 1] <= 123)
            }
            shapes.add(Arrays.deepToString(outline))
        }
        assertEquals(4, shapes.size)
    }

    @Test fun allCornerChoicesRemainDistinctForEachCardFamily() {
        for (style in 2 until ThemeStyle.COUNT) {
            val choices = HashSet<String>()
            for (radius in intArrayOf(4, 16, 28)) choices.add(Arrays.deepToString(DemoGeometry.outline(style, 260f, 100f, radius.toFloat())))
            assertEquals(3, choices.size)
        }
    }

    @Test fun referencesHaveWideCardsAndSpaceForTheirOwnFocusMarks() {
        assertEquals(2.6f, DemoGeometry.ratio(), 0.001f)
        assertTrue(DemoGeometry.footer(ThemeStyle.DUNE, 1f) > 0)
        assertTrue(DemoGeometry.footer(ThemeStyle.COBALT, 1f) > 0)
        assertEquals(0f, DemoGeometry.footer(ThemeStyle.PEARL, 1f), 0.001f)
    }
}
