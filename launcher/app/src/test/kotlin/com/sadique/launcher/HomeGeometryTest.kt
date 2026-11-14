package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGeometryTest {
    @Test fun cornerChoicesHaveNoGlassOnlyMinimumAndClampInvalidImports() {
        assertEquals(4, HomeGeometry.tileRadius(-1))
        assertEquals(4, HomeGeometry.tileRadius(0))
        assertEquals(16, HomeGeometry.tileRadius(1))
        assertEquals(28, HomeGeometry.tileRadius(2))
        assertEquals(28, HomeGeometry.tileRadius(99))
    }

    @Test fun insetRimsKeepTheSameOutsideCurvatureAtEveryStrokeWidth() {
        for (choice in 0 until 3) for (stroke in intArrayOf(1, 2, 3, 5)) {
            val radius = HomeGeometry.tileRadius(choice).toFloat()
            val inset = stroke / 2f
            assertEquals(radius, HomeGeometry.insetRadius(radius, inset) + inset, 0.001f)
            assertTrue(HomeGeometry.insetRadius(radius, (stroke * 2).toFloat()) >= 0)
        }
    }

    @Test fun roundedAvatarPreviewAndStatusBarHaveTheSameProportions() {
        assertEquals(0.25f, HomeGeometry.avatarRadius(44) / 44, 0.001f)
        assertEquals(0.25f, HomeGeometry.avatarRadius(96) / 96, 0.001f)
    }
}
