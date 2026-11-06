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
    @Test public void pearlClockCanChangeInkWithoutChangingTheWallpaper() {
        assertEquals(0xffffffff,ThemeStyle.wallpaperClockInk(0xff10202b));
        assertEquals(0xff11191c,ThemeStyle.wallpaperClockInk(0xfff6e9d4));
        for(int value=0;value<=255;value+=15) {
            int background=0xff000000|(value<<16)|(value<<8)|value;
            double a=ThemeStyle.luminance(background),b=ThemeStyle.luminance(ThemeStyle.wallpaperClockInk(background));
            assertTrue((Math.max(a,b)+.05)/(Math.min(a,b)+.05)>3);
        }
    }
    @Test public void compactClockStillHasThreeDifferentUserSizes() {
        assertTrue(HomeGeometry.clockSize(36,true)<HomeGeometry.clockSize(44,true));
        assertTrue(HomeGeometry.clockSize(44,true)<HomeGeometry.clockSize(52,true));
        assertEquals(52,HomeGeometry.clockSize(52,false));
    }
}
