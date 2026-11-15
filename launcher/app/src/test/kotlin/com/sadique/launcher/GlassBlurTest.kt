package com.sadique.launcher

import java.util.Arrays
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class GlassBlurTest {
    @Test fun uniformWallpaperDoesNotChangeColour() {
        val pixels = IntArray(15)
        Arrays.fill(pixels, 0xff527a96.toInt())
        GlassBlur.blur(pixels, 5, 3, 5)
        for (pixel in pixels) assertEquals(0xff527a96.toInt(), pixel)
    }

    @Test fun clampedEdgesAndBothAxesHaveTheSameResult() {
        val horizontal = intArrayOf(0xff000000.toInt(), 0xffffffff.toInt())
        val vertical = horizontal.clone()
        GlassBlur.blur(horizontal, 2, 1, 1)
        GlassBlur.blur(vertical, 1, 2, 1)
        assertArrayEquals(intArrayOf(0xff7a7a7a.toInt(), 0xff838383.toInt()), horizontal)
        assertArrayEquals(horizontal, vertical)
    }

    @Test fun zeroRadiusLeavesInputUntouched() {
        val pixels = intArrayOf(0xffaabbcc.toInt())
        GlassBlur.blur(pixels, 1, 1, 0)
        assertEquals(0xffaabbcc.toInt(), pixels[0])
    }

    @Test fun invalidBoundsAreRejected() {
        try {
            GlassBlur.blur(IntArray(3), 2, 2, 1)
            fail()
        } catch (expected: IllegalArgumentException) {
        }
        try {
            GlassBlur.blur(IntArray(1), 1, 1, -1)
            fail()
        } catch (expected: IllegalArgumentException) {
        }
    }
}
