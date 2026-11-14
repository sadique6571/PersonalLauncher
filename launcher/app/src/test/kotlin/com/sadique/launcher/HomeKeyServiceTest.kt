package com.sadique.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeKeyServiceTest {
    companion object {
        private const val SOON = 45_000L
        private const val TV = "com.mediatek.wwtv.tvcenter"
    }

    @Test fun aTapIsAShortPress() {
        assertTrue(HomeKeyService.isShortPress(1_000, 1_120, 400))
    }

    @Test fun holdingPastTheTimeoutIsALongPress() {
        assertFalse(HomeKeyService.isShortPress(1_000, 1_400, 400))
        assertFalse(HomeKeyService.isShortPress(1_000, 3_000, 400))
    }

    @Test fun firstBindOfANewBootProbes() {
        assertTrue(HomeKeyService.firstBindAfterBoot(12, 11, SOON))
        assertTrue(HomeKeyService.firstBindAfterBoot(1, -1, SOON))
    }

    @Test fun aRebindInTheSameBootDoesNot() {
        assertFalse(HomeKeyService.firstBindAfterBoot(12, 12, SOON))
    }

    @Test fun aBindLongAfterBootDoesNot() {
        assertFalse(HomeKeyService.firstBindAfterBoot(12, 11, HomeKeyService.BOOT_WINDOW_MS))
    }

    @Test fun noBootCountMeansNoProbe() {
        assertFalse(HomeKeyService.firstBindAfterBoot(-1, -1, SOON))
    }

    @Test fun liveTvBeforeAnyKeyGoesHome() {
        assertTrue(HomeKeyService.powerOnRedirect(TV, TV, SOON, HomeKeyService.BOOT_WINDOW_MS))
    }

    @Test fun otherAppsAndInputsAreLeftAlone() {
        assertFalse(HomeKeyService.powerOnRedirect(TV, "com.netflix.ninja", SOON, HomeKeyService.BOOT_WINDOW_MS))
        assertFalse(HomeKeyService.powerOnRedirect(TV, null, SOON, HomeKeyService.BOOT_WINDOW_MS))
    }

    @Test fun afterAKeyOrTheWindowLiveTvStays() {
        assertFalse(HomeKeyService.powerOnRedirect(TV, TV, SOON, 0))
        assertFalse(HomeKeyService.powerOnRedirect(TV, TV, HomeKeyService.BOOT_WINDOW_MS, HomeKeyService.BOOT_WINDOW_MS))
    }

    @Test fun offMeansNothing() {
        assertFalse(HomeKeyService.powerOnRedirect("", TV, SOON, HomeKeyService.BOOT_WINDOW_MS))
    }

    @Test fun alwaysGoesHomeAfterScreenOnUntilAKey() {
        assertTrue(HomeKeyService.stillArmed(SOON, SOON + HomeKeyService.SCREEN_ON_WINDOW_MS))
        assertFalse(HomeKeyService.stillArmed(SOON, 0))
    }
}
