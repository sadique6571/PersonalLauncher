package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class HarmonyVisionLayoutTest {
    @Test public void dockKeepsTheFocusedPlateInsideItsCellAtCommonTvDensities() {
        for (int height : new int[]{420, 540, 720, 1080}) {
            int plate = HarmonyVisionLayout.dockPlateDp(height);
            float focused = plate * HarmonyVisionLayout.DOCK_FOCUS_SCALE;
            assertTrue(focused < HarmonyVisionLayout.dockCellWidthDp(height) - 6);
            assertTrue(HarmonyVisionLayout.DOCK_FOCUS_LIFT_DP + (focused - plate) / 2
                    < HarmonyVisionLayout.DOCK_HEADROOM_DP);
            assertTrue(HarmonyVisionLayout.clockDiameterDp(height, 2)
                    + HarmonyVisionLayout.dockHeightDp(height) + 70 <= height);
        }
    }

    @Test public void shortDockIsCenteredAndLongDockShowsWholeCells() {
        assertEquals(250, HarmonyVisionLayout.dockSidePadding(800, 100, 3));
        assertEquals(23, HarmonyVisionLayout.dockSidePadding(846, 100, 15));
        assertEquals(0, HarmonyVisionLayout.dockSidePadding(846, 100, 0));
    }

    @Test public void harmonyDrawerAndWidgetsFitCommonTvSizes() {
        assertTrue(HarmonyVisionLayout.showNativeWeatherWidget());
        assertFalse(HarmonyVisionLayout.showSecondGreeting());
        assertEquals(5, HarmonyVisionLayout.drawerColumns(800));
        assertEquals(4, HarmonyVisionLayout.drawerColumns(650));
        assertEquals(2, HarmonyVisionLayout.drawerColumns(300));
        assertEquals(420, HarmonyVisionLayout.drawerHeightDp(540));
        assertEquals(336, HarmonyVisionLayout.drawerHeightDp(420));
    }
}
