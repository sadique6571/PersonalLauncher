package com.sadique.launcher

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class RecoveryRedirectTest {

    private companion object {
        private const val PKG = "com.google.android.tungsten.setupwraith"
        private const val CLS = "com.google.android.tungsten.setupwraith.RecoveryActivity"
        private const val SOON = 45_000L
        private val COOLDOWN = HomeKeyService.RECOVERY_REDIRECT_COOLDOWN_MS
    }

    @Test fun exactRecoveryWindowMatches() {
        assertTrue(HomeKeyService.isRecoveryWindow(PKG, CLS))
    }

    @Test fun otherSetupWindowsDoNotMatch() {
        assertFalse(HomeKeyService.isRecoveryWindow(PKG, PKG + ".SetupActivity"))
        assertFalse(HomeKeyService.isRecoveryWindow(PKG, PKG + ".SetupWraithActivity"))
    }

    @Test fun otherPackagesDoNotMatch() {
        assertFalse(HomeKeyService.isRecoveryWindow("com.google.android.apps.tv.launcherx", CLS))
        assertFalse(HomeKeyService.isRecoveryWindow("com.android.tv.settings", CLS))
    }

    @Test fun nullsDoNotMatch() {
        assertFalse(HomeKeyService.isRecoveryWindow(null, CLS))
        assertFalse(HomeKeyService.isRecoveryWindow(PKG, null))
        assertFalse(HomeKeyService.isRecoveryWindow(null, null))
    }

    private fun redirect(optIn: Boolean, pkg: String, cls: String, unavailable: Boolean,
            boot: Int, lastBoot: Int, now: Long, lastMs: Long): Boolean {
        return HomeKeyService.shouldRedirectRecovery(optIn, pkg, cls, unavailable, boot, lastBoot, now, lastMs)
    }

    @Test fun redirectWhenAllConditionsHold() {
        assertTrue(redirect(true, PKG, CLS, true, 12, -1, SOON, 0))
    }

    @Test fun optOutNeverRedirects() {
        assertFalse(redirect(false, PKG, CLS, true, 12, -1, SOON, 0))
    }

    @Test fun wrongWindowNeverRedirects() {
        assertFalse(redirect(true, PKG, PKG + ".SetupActivity", true, 12, -1, SOON, 0))
    }

    @Test fun availableStockHomeNeverRedirects() {
        assertFalse(redirect(true, PKG, CLS, false, 12, -1, SOON, 0))
    }

    @Test fun noBootCountNeverRedirects() {
        assertFalse(redirect(true, PKG, CLS, true, -1, -1, SOON, 0))
    }

    @Test fun oncePerBootOnly() {
        assertFalse(redirect(true, PKG, CLS, true, 12, 12, SOON + COOLDOWN, SOON))
    }

    @Test fun cooldownGuardsRapidRefire() {
        assertFalse(redirect(true, PKG, CLS, true, 12, 11, SOON, SOON - 1_000L))
    }

    @Test fun newBootAfterCooldownRedirectsAgain() {
        assertTrue(redirect(true, PKG, CLS, true, 13, 12, SOON + COOLDOWN, SOON))
    }

    @Test fun openHomeFirstLaunchGoes() {
        assertTrue(HomeKeyService.shouldOpenHome(SOON, 0))
    }

    @Test fun openHomeDuplicatesInsideWindowAreDropped() {
        assertFalse(HomeKeyService.shouldOpenHome(SOON, SOON - 500L))
        assertFalse(HomeKeyService.shouldOpenHome(SOON, SOON - HomeKeyService.OPEN_HOME_DEDUP_MS + 1))
    }

    @Test fun openHomeAfterWindowGoesAgain() {
        assertTrue(HomeKeyService.shouldOpenHome(SOON, SOON - HomeKeyService.OPEN_HOME_DEDUP_MS))
        assertTrue(HomeKeyService.shouldOpenHome(SOON, SOON - HomeKeyService.OPEN_HOME_DEDUP_MS - 1))
    }
}
