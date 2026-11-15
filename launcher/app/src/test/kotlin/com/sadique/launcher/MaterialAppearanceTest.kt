package com.sadique.launcher

import org.junit.Test
import org.junit.Assert.*

class MaterialAppearanceTest {
    @Test fun airyNormalSolidAndFinishAreDistinctForPanelsAndDrawer() {
        for (drawer in booleanArrayOf(false, true)) for (focused in booleanArrayOf(false, true)) {
            for (finish in 0 until 3) {
                val airy = MaterialAppearance.alpha(finish, 30, drawer, focused)
                val normal = MaterialAppearance.alpha(finish, 70, drawer, focused)
                val solid = MaterialAppearance.alpha(finish, 100, drawer, focused)
                assertTrue(if (drawer) normal > airy else normal - airy >= 40); assertTrue(solid > normal)
                assertEquals(0, MaterialAppearance.alpha(finish, 0, drawer, focused))
                assertTrue(solid <= 255)
            }
            assertTrue(MaterialAppearance.alpha(1, 70, drawer, focused) < MaterialAppearance.alpha(0, 70, drawer, focused))
            assertTrue(MaterialAppearance.alpha(0, 70, drawer, focused) < MaterialAppearance.alpha(2, 70, drawer, focused))
        }
    }
    @Test fun materialDrawerUsesTheSameGlassOpacityCurveAsApple() {
        for (finish in 0 until 3) for (intensity in intArrayOf(0, 30, 70, 100)) {
            assertEquals(GlassTransparency.tintAlpha(finish, intensity), MaterialAppearance.alpha(finish, intensity, true, false))
            assertTrue(MaterialAppearance.alpha(finish, intensity, true, true) >= MaterialAppearance.alpha(finish, intensity, true, false))
        }
    }
    @Test fun selectedAccentChangesBothMaterialBasesAndFocusKeepsItsHue() {
        for (base in intArrayOf(0xfff9f1e8.toInt(), 0xffe9e4dc.toInt(), 0xff26363a.toInt(), 0xff111e24.toInt())) {
            val red = MaterialAppearance.tint(base, 0xffff0000.toInt(), false)
            val blue = MaterialAppearance.tint(base, 0xff0000ff.toInt(), false)
            assertNotEquals(red, blue)
            assertTrue(((red shr 16) and 255) > ((blue shr 16) and 255))
            assertTrue((blue and 255) > (red and 255))
            assertNotEquals(MaterialAppearance.tint(base, 0xffff0000.toInt(), true), red)
        }
    }
    @Test fun clockStaysWhiteExceptOnNearlyWhiteWallpaper() {
        for (background in intArrayOf(0xff10202b.toInt(), 0xff808080.toInt(), 0xffdddddd.toInt(), 0xfff6e9d4.toInt(), 0xffeef3e7.toInt(), 0xffffff00.toInt(), 0xffe8f7ff.toInt()))
            assertEquals(0xffffffff.toInt(), ThemeStyle.wallpaperClockInk(background))
        assertEquals(0xffffffff.toInt(), ThemeStyle.wallpaperClockInk(0xfff4f4f4.toInt()))
        assertEquals(0xff11191c.toInt(), ThemeStyle.wallpaperClockInk(0xfff5f5f5.toInt()))
        assertEquals(0xff11191c.toInt(), ThemeStyle.wallpaperClockInk(0xffffffff.toInt()))
    }
    @Test fun headerAlwaysHasAReadableThemeFoundation() {
        assertEquals(224, HeaderAppearance.alpha(0))
        assertEquals(224, HeaderAppearance.alpha(100))
        assertEquals(240, HeaderAppearance.alpha(240))
        assertEquals(255, HeaderAppearance.alpha(255))
    }
    @Test fun compactClockStillHasThreeDifferentUserSizes() {
        assertTrue(HomeGeometry.clockSize(36, true) < HomeGeometry.clockSize(44, true))
        assertTrue(HomeGeometry.clockSize(44, true) < HomeGeometry.clockSize(52, true))
        assertEquals(52, HomeGeometry.clockSize(52, false))
    }
}
