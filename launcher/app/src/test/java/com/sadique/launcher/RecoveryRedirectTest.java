package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecoveryRedirectTest {
    private static final String PKG = "com.google.android.tungsten.setupwraith";
    private static final String CLS = "com.google.android.tungsten.setupwraith.RecoveryActivity";
    private static final long SOON = 45_000L;
    private static final long COOLDOWN = HomeKeyService.RECOVERY_REDIRECT_COOLDOWN_MS;

    @Test public void exactRecoveryWindowMatches() {
        assertTrue(HomeKeyService.isRecoveryWindow(PKG, CLS));
    }

    @Test public void otherSetupWindowsDoNotMatch() {
        assertFalse(HomeKeyService.isRecoveryWindow(PKG, PKG + ".SetupActivity"));
        assertFalse(HomeKeyService.isRecoveryWindow(PKG, PKG + ".SetupWraithActivity"));
    }

    @Test public void otherPackagesDoNotMatch() {
        assertFalse(HomeKeyService.isRecoveryWindow("com.google.android.apps.tv.launcherx", CLS));
        assertFalse(HomeKeyService.isRecoveryWindow("com.android.tv.settings", CLS));
    }

    @Test public void nullsDoNotMatch() {
        assertFalse(HomeKeyService.isRecoveryWindow(null, CLS));
        assertFalse(HomeKeyService.isRecoveryWindow(PKG, null));
        assertFalse(HomeKeyService.isRecoveryWindow(null, null));
    }

    private static boolean redirect(boolean optIn, String pkg, String cls, boolean unavailable,
            int boot, int lastBoot, long now, long lastMs) {
        return HomeKeyService.shouldRedirectRecovery(optIn, pkg, cls, unavailable, boot, lastBoot, now, lastMs);
    }

    @Test public void redirectWhenAllConditionsHold() {
        assertTrue(redirect(true, PKG, CLS, true, 12, -1, SOON, 0));
    }

    @Test public void optOutNeverRedirects() {
        assertFalse(redirect(false, PKG, CLS, true, 12, -1, SOON, 0));
    }

    @Test public void wrongWindowNeverRedirects() {
        assertFalse(redirect(true, PKG, PKG + ".SetupActivity", true, 12, -1, SOON, 0));
    }

    @Test public void availableStockHomeNeverRedirects() {
        assertFalse(redirect(true, PKG, CLS, false, 12, -1, SOON, 0));
    }

    @Test public void noBootCountNeverRedirects() {
        assertFalse(redirect(true, PKG, CLS, true, -1, -1, SOON, 0));
    }

    @Test public void oncePerBootOnly() {
        assertFalse(redirect(true, PKG, CLS, true, 12, 12, SOON + COOLDOWN, SOON));
    }

    @Test public void cooldownGuardsRapidRefire() {
        assertFalse(redirect(true, PKG, CLS, true, 12, 11, SOON, SOON - 1_000L));
    }

    @Test public void newBootAfterCooldownRedirectsAgain() {
        assertTrue(redirect(true, PKG, CLS, true, 13, 12, SOON + COOLDOWN, SOON));
    }

    @Test public void openHomeFirstLaunchGoes() {
        assertTrue(HomeKeyService.shouldOpenHome(SOON, 0));
    }

    @Test public void openHomeDuplicatesInsideWindowAreDropped() {
        assertFalse(HomeKeyService.shouldOpenHome(SOON, SOON - 500L));
        assertFalse(HomeKeyService.shouldOpenHome(SOON, SOON - HomeKeyService.OPEN_HOME_DEDUP_MS + 1));
    }

    @Test public void openHomeAfterWindowGoesAgain() {
        assertTrue(HomeKeyService.shouldOpenHome(SOON, SOON - HomeKeyService.OPEN_HOME_DEDUP_MS));
        assertTrue(HomeKeyService.shouldOpenHome(SOON, SOON - HomeKeyService.OPEN_HOME_DEDUP_MS - 1));
    }
}
