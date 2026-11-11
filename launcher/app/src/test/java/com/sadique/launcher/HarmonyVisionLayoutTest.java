package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class HarmonyVisionLayoutTest {
    @Test public void harmonyNeverLayersAnOpaqueDrawerOverReflections(){
        assertFalse(HarmonyVisionLayout.showDockPanel());
        assertTrue(HarmonyVisionLayout.showDockReflections());
        assertFalse(HarmonyVisionLayout.showSecondGreeting());
        assertFalse(HarmonyVisionLayout.showWatchNextFooter());
    }
    @Test public void watchesFitSmallTVHeights(){
        assertEquals(242,HarmonyVisionLayout.clockDiameterDp(540,1));
        assertEquals(268,HarmonyVisionLayout.clockDiameterDp(540,2));
        assertTrue(HarmonyVisionLayout.clockDiameterDp(405,0)<=185);
        assertTrue(HarmonyVisionLayout.clockDiameterDp(540,2)<540-190);
    }
}
