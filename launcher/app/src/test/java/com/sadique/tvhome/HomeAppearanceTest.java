package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeAppearanceTest {
    @Test public void explicitModesOverrideTimeWhileAutomaticFollowsDaylight() {
        assertEquals(0f, HomeAppearance.daylight("dark", 720), 0f);
        assertEquals(1f, HomeAppearance.daylight("light", 0), 0f);
        assertEquals(0f, HomeAppearance.daylight("auto", 0), 0f);
        assertEquals(1f, HomeAppearance.daylight("auto", 720), 0f);
        assertEquals(64, HomeAppearance.shade("dark", 720, 0));
        assertEquals(0, HomeAppearance.shade("light", 0, 0));
        assertEquals(150, HomeAppearance.shade("dark", 0, 999));
    }
    @Test public void portraitMountainsStayNearClockWithoutFullHeightMagnification() {
        float[] frame = HomeAppearance.frame(393, 850, 1280, 720, 170, true);
        float height = frame[3] - frame[1];
        assertTrue(height < 400);
        assertEquals(170, frame[1] + height * .49f, .1f);
        assertTrue(frame[0] <= 0 && frame[2] >= 393);
    }
    @Test public void televisionRidgeStaysAtTheClockWhilePhotoStillCoversTheScreen() {
        float[] frame = HomeAppearance.frame(960, 540, 1280, 720, 160, true);
        assertEquals(160, frame[1] + (frame[3] - frame[1]) * .49f, .1f);
        assertTrue(frame[1] <= 0 && frame[3] >= 540);
    }
    @Test public void landscapeAndCustomPhotosCoverTheirViewportWithoutDistortion() {
        for (boolean mountains : new boolean[]{true, false}) {
            float[] frame = HomeAppearance.frame(960, 540, 1280, 720, 160, mountains);
            assertTrue(frame[0] <= 0 && frame[1] <= 0);
            assertTrue(frame[2] >= 960 && frame[3] >= 540);
            assertEquals(1280f / 720, (frame[2]-frame[0])/(frame[3]-frame[1]), .001f);
        }
        float[] custom = HomeAppearance.frame(393, 850, 1280, 720, 170, false);
        assertTrue(custom[1] <= 0 && custom[3] >= 850);
    }
}
