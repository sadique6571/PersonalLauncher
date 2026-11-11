package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class HarmonyVisionLayoutTest {
    @Test public void harmonyDockNeverStretchesOrMirrorsIcons(){
        assertFalse(HarmonyVisionLayout.showDockPanel());
        assertFalse(HarmonyVisionLayout.showDockReflections());
        assertFalse(HarmonyVisionLayout.expandAppOnFocus());
        assertTrue(HarmonyVisionLayout.showNativeWeatherWidget());
        assertFalse(HarmonyVisionLayout.showSecondGreeting());
        assertFalse(HarmonyVisionLayout.showWatchNextFooter());
    }
    @Test public void sevenTwentyPixelTVKeepsClockClearOfDock(){
        // 1280x720 @ 1.33 density corresponds to about 961x540 dp.
        int height=540;
        assertEquals(8,HarmonyVisionLayout.dockColumns(913));
        assertEquals(68,HarmonyVisionLayout.dockPlateDp(height));
        assertEquals(114,HarmonyVisionLayout.dockHeightDp(height));
        for(int face=0;face<3;face++)
            assertTrue(HarmonyVisionLayout.clockDiameterDp(height,face)
                    +HarmonyVisionLayout.dockHeightDp(height)+70<=height);
    }
    @Test public void compactTVStillHasAStableNonExpandingDock(){
        assertEquals(4,HarmonyVisionLayout.dockColumns(430));
        assertEquals(58,HarmonyVisionLayout.dockPlateDp(420));
        assertTrue(HarmonyVisionLayout.clockDiameterDp(420,2)<=150);
    }
}
