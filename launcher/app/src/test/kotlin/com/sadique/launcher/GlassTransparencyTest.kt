package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassTransparencyTest {
    @Test fun zeroIntensityMakesBothLayersTransparent() {
        for (finish in 0 until 3) {
            assertEquals(0, GlassTransparency.backdropAlpha(finish, 0))
            assertEquals(0, GlassTransparency.tintAlpha(finish, 0))
            assertEquals(0, GlassTransparency.blurRadius(finish, 0))
        }
    }

    @Test fun everySettingRetainsSomeOriginalWallpaper() {
        for (finish in 0 until 3) for (intensity in 0..100) {
            assertTrue(GlassTransparency.backdropAlpha(finish, intensity) < 255)
            assertTrue(GlassTransparency.tintAlpha(finish, intensity) < 255)
        }
        assertTrue(originalFraction(0, 30) > 0.70)
        assertTrue(originalFraction(0, 70) > 0.40)
        assertTrue(originalFraction(1, 100) > 0.50)
    }

    @Test fun airyNormalAndSolidChangeBothLayersAndClearDoesNotBlur() {
        for (finish in 0 until 3) {
            assertTrue(GlassTransparency.backdropAlpha(finish, 30) < GlassTransparency.backdropAlpha(finish, 70))
            assertTrue(GlassTransparency.backdropAlpha(finish, 70) < GlassTransparency.backdropAlpha(finish, 100))
            assertTrue(GlassTransparency.tintAlpha(finish, 30) < GlassTransparency.tintAlpha(finish, 70))
            assertTrue(GlassTransparency.tintAlpha(finish, 70) < GlassTransparency.tintAlpha(finish, 100))
        }
        assertEquals(0, GlassTransparency.blurRadius(1, 100))
    }

    private fun originalFraction(finish: Int, intensity: Int): Double {
        return (1 - GlassTransparency.backdropAlpha(finish, intensity) / 255.0) *
            (1 - GlassTransparency.tintAlpha(finish, intensity) / 255.0)
    }
}
