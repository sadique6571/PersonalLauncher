package com.sadique.launcher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAppearanceTest {
    @Test fun explicitModesOverrideTimeWhileAutomaticFollowsDaylight() {
        assertEquals(0f, HomeAppearance.daylight("dark", 720), 0f)
        assertEquals(1f, HomeAppearance.daylight("light", 0), 0f)
        assertEquals(0f, HomeAppearance.daylight("auto", 0), 0f)
        assertEquals(1f, HomeAppearance.daylight("auto", 720), 0f)
        assertEquals(24, HomeAppearance.shade("dark", 720, 0))
        assertEquals(0, HomeAppearance.shade("light", 0, 0))
        assertEquals(150, HomeAppearance.shade("dark", 0, 999))
    }

    @Test fun portraitKeepsTheSkyWithoutFullHeightMagnification() {
        val frame = HomeAppearance.frame(393, 850, 1280, 720, 170f, true)
        val height = frame[3] - frame[1]
        assertTrue(height < 400)
        assertEquals(0f, frame[1], 0.1f)
        assertTrue(frame[1] + height * 0.49f > 140)
        assertTrue(frame[0] <= 0 && frame[2] >= 393)
    }

    @Test fun fitAt1080pKeepsEveryEdgeAcrossAspectRatios() {
        assertArrayEquals(floatArrayOf(0f, 0f, 1920f, 1080f), HomeAppearance.fitFrame(1920, 1080, 1920, 1080), 0.1f)
        assertArrayEquals(floatArrayOf(240f, 0f, 1680f, 1080f), HomeAppearance.fitFrame(1920, 1080, 1600, 1200), 0.1f)
        assertArrayEquals(floatArrayOf(600f, 0f, 1320f, 1080f), HomeAppearance.fitFrame(1920, 1080, 800, 1200), 0.1f)
        assertArrayEquals(floatArrayOf(0f, 135f, 1920f, 945f), HomeAppearance.fitFrame(1920, 1080, 1920, 810), 0.1f)
    }

    @Test fun televisionShowsFullSkyAndDoesNotZoomToAlignRidgeWithClock() {
        val frame = HomeAppearance.frame(960, 540, 1280, 720, 160f, true)
        assertArrayEquals(floatArrayOf(0f, 0f, 960f, 540f), frame, 0.1f)
        assertTrue((frame[3] - frame[1]) * 0.49f > 160)
    }

    @Test fun landscapeAndCustomPhotosCoverTheirViewportWithoutDistortion() {
        for (mountains in booleanArrayOf(true, false)) {
            val frame = HomeAppearance.frame(960, 540, 1280, 720, 160f, mountains)
            assertTrue(frame[0] <= 0 && frame[1] <= 0)
            assertTrue(frame[2] >= 960 && frame[3] >= 540)
            assertEquals(1280f / 720, (frame[2] - frame[0]) / (frame[3] - frame[1]), 0.001f)
        }
        val custom = HomeAppearance.frame(393, 850, 1280, 720, 170f, false)
        assertTrue(custom[1] <= 0 && custom[3] >= 850)
    }
}
