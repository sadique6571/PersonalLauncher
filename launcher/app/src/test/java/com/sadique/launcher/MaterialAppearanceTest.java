package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class MaterialAppearanceTest {
    @Test public void airyNormalSolidAndFinishAreDistinctForPanelsAndDrawer() {
        for(boolean drawer:new boolean[]{false,true}) for(boolean focused:new boolean[]{false,true}) {
            for(int finish=0;finish<3;finish++) {
                int airy=MaterialAppearance.alpha(finish,30,drawer,focused);
                int normal=MaterialAppearance.alpha(finish,70,drawer,focused);
                int solid=MaterialAppearance.alpha(finish,100,drawer,focused);
                assertTrue(drawer?normal>airy:normal-airy>=40); assertTrue(solid>normal);
                assertEquals(0,MaterialAppearance.alpha(finish,0,drawer,focused));
                assertTrue(solid<=255);
            }
            assertTrue(MaterialAppearance.alpha(1,70,drawer,focused)<MaterialAppearance.alpha(0,70,drawer,focused));
            assertTrue(MaterialAppearance.alpha(0,70,drawer,focused)<MaterialAppearance.alpha(2,70,drawer,focused));
        }
    }
    @Test public void materialDrawerUsesTheSameGlassOpacityCurveAsApple() {
        for(int finish=0;finish<3;finish++) for(int intensity:new int[]{0,30,70,100}) {
            assertEquals(GlassTransparency.tintAlpha(finish,intensity),MaterialAppearance.alpha(finish,intensity,true,false));
            assertTrue(MaterialAppearance.alpha(finish,intensity,true,true)>=MaterialAppearance.alpha(finish,intensity,true,false));
        }
    }
    @Test public void selectedAccentChangesBothMaterialBasesAndFocusKeepsItsHue() {
        for(int base:new int[]{0xfff9f1e8,0xffe9e4dc,0xff26363a,0xff111e24}) {
            int red=MaterialAppearance.tint(base,0xffff0000,false);
            int blue=MaterialAppearance.tint(base,0xff0000ff,false);
            assertNotEquals(red,blue);
            assertTrue(((red>>16)&255)>((blue>>16)&255));
            assertTrue((blue&255)>(red&255));
            assertNotEquals(MaterialAppearance.tint(base,0xffff0000,true),red);
        }
    }
    @Test public void clockStaysWhiteExceptOnNearlyWhiteWallpaper() {
        for(int background:new int[]{0xff10202b,0xff808080,0xffdddddd,0xfff6e9d4,0xffeef3e7,0xffffff00,0xffe8f7ff})
            assertEquals(0xffffffff,ThemeStyle.wallpaperClockInk(background));
        assertEquals(0xffffffff,ThemeStyle.wallpaperClockInk(0xfff4f4f4));
        assertEquals(0xff11191c,ThemeStyle.wallpaperClockInk(0xfff5f5f5));
        assertEquals(0xff11191c,ThemeStyle.wallpaperClockInk(0xffffffff));
    }
    @Test public void headerAlwaysHasAReadableThemeFoundation() {
        assertEquals(224,HeaderAppearance.alpha(0));
        assertEquals(224,HeaderAppearance.alpha(100));
        assertEquals(240,HeaderAppearance.alpha(240));
        assertEquals(255,HeaderAppearance.alpha(255));
    }
    @Test public void compactClockStillHasThreeDifferentUserSizes() {
        assertTrue(HomeGeometry.clockSize(36,true)<HomeGeometry.clockSize(44,true));
        assertTrue(HomeGeometry.clockSize(44,true)<HomeGeometry.clockSize(52,true));
        assertEquals(52,HomeGeometry.clockSize(52,false));
    }
}
