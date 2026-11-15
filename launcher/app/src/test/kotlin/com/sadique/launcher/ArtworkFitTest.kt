package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkFitTest {
    @Test fun largeMarksNeverExceedTheirCacheBitmap() {
        for (w in intArrayOf(32, 128, 256, 512)) for (h in intArrayOf(32, 128, 256, 512)) for (size in floatArrayOf(0.8f, 1f, 1.2f)) {
            val scale = ArtworkFit.scale(w, h, size)
            assertTrue(scale > 0)
            assertTrue(w * scale <= 240.001f)
            assertTrue(h * scale <= 240.001f)
            assertEquals(w / h.toFloat(), (w * scale) / (h * scale), 0.0001f)
        }
        assertEquals(220f / 256, ArtworkFit.scale(256, 256, 1f), 0.0001f)
        assertEquals(240f / 256, ArtworkFit.scale(256, 256, 1.2f), 0.0001f)
    }
}
