package com.sadique.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerShapeTest {
    @Test
    fun wideBannersAreKept() {
        assertTrue(AppAdapter.isBannerShaped(320, 180))
        assertTrue(AppAdapter.isBannerShaped(1920, 1080))
    }

    @Test
    fun squareAndTallPicturesAreNotBanners() {
        assertFalse(AppAdapter.isBannerShaped(64, 64))
        assertFalse(AppAdapter.isBannerShaped(256, 256))
        assertFalse(AppAdapter.isBannerShaped(100, 160))
    }

    @Test
    fun drawablesWithoutAnIntrinsicSizeStillFillTheTile() {
        assertTrue(AppAdapter.isBannerShaped(-1, -1))
        assertTrue(AppAdapter.isBannerShaped(0, 0))
    }
}
