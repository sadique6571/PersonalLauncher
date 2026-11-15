package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarmonyVisionLayoutTest {
    @Test fun dockKeepsTheFocusedPlateInsideItsCellAtCommonTvDensities() {
        for (height in intArrayOf(420, 540, 720, 1080)) {
            val plate = HarmonyVisionLayout.dockPlateDp(height)
            assertTrue(HarmonyVisionLayout.dockTileHeightDp(height) - plate >
                HarmonyVisionLayout.DOCK_CAPTION_HEIGHT_DP)
            val focused = plate * HarmonyVisionLayout.DOCK_FOCUS_SCALE
            assertTrue(focused < HarmonyVisionLayout.dockCellWidthDp(height) - 6)
            assertTrue(HarmonyVisionLayout.DOCK_FOCUS_LIFT_DP + (focused - plate) / 2 <
                HarmonyVisionLayout.DOCK_HEADROOM_DP)
            assertTrue(HarmonyVisionLayout.clockDiameterDp(height, 2) +
                HarmonyVisionLayout.dockHeightDp(height) + 70 <= height)
        }
    }

    @Test fun shortDockIsCenteredAndLongDockShowsWholeCells() {
        assertEquals(250, HarmonyVisionLayout.dockSidePadding(800, 100, 3))
        assertEquals(23, HarmonyVisionLayout.dockSidePadding(846, 100, 15))
        assertEquals(0, HarmonyVisionLayout.dockSidePadding(846, 100, 0))
    }

    @Test fun harmonyDrawerAndWidgetsFitCommonTvSizes() {
        assertTrue(HarmonyVisionLayout.showNativeWeatherWidget())
        assertFalse(HarmonyVisionLayout.showSecondGreeting())
        assertEquals(5, HarmonyVisionLayout.drawerColumns(800))
        assertEquals(4, HarmonyVisionLayout.drawerColumns(650))
        assertEquals(2, HarmonyVisionLayout.drawerColumns(300))
        assertEquals(420, HarmonyVisionLayout.drawerHeightDp(540))
        assertEquals(336, HarmonyVisionLayout.drawerHeightDp(420))
    }
}
