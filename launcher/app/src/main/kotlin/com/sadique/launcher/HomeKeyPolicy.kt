package com.sadique.launcher

import com.sadique.launcher.HomeKeyService.Companion.RECOVERY_PACKAGE
import com.sadique.launcher.HomeKeyService.Companion.RECOVERY_ACTIVITY
import com.sadique.launcher.HomeKeyService.Companion.RECOVERY_REDIRECT_COOLDOWN_MS
import com.sadique.launcher.HomeKeyService.Companion.OPEN_HOME_DEDUP_MS
import com.sadique.launcher.HomeKeyService.Companion.BOOT_WINDOW_MS

internal object HomeKeyPolicy {
    /** Exact component match: the recovery window, and nothing else from setupwraith or elsewhere. */
    fun isRecoveryWindow(pkg: CharSequence?, cls: CharSequence?): Boolean {
        if (pkg == null || cls == null) return false
        return RECOVERY_PACKAGE.contentEquals(pkg) && RECOVERY_ACTIVITY == cls.toString()
    }

    /**
     * Whether the recovery fallback may redirect right now. Pure for tests: every input is a parameter.
     * Redirects at most once per boot (Android reopens recovery on its own, so without this the service
     * would steal focus in a loop), with a cooldown as a second guard.
     */
    fun shouldRedirectRecovery(
        optIn: Boolean, pkg: CharSequence?, cls: CharSequence?,
        stockHomeUnavailable: Boolean, boot: Int, lastRedirectBoot: Int, now: Long, lastRedirectMs: Long
    ): Boolean {
        if (!optIn || !isRecoveryWindow(pkg, cls) || !stockHomeUnavailable) return false
        if (boot < 0 || boot == lastRedirectBoot) return false
        return now - lastRedirectMs >= RECOVERY_REDIRECT_COOLDOWN_MS
    }

    /** Duplicate openHome() triggers inside the window are dropped (window event + key + power-on can coincide). */
    fun shouldOpenHome(now: Long, lastOpenMs: Long): Boolean {
        return now - lastOpenMs >= OPEN_HOME_DEDUP_MS
    }

    fun stillArmed(now: Long, armedUntil: Long): Boolean {
        return now < armedUntil
    }


    fun powerOnRedirect(liveTv: String, pkg: CharSequence?, now: Long, armedUntil: Long): Boolean {
        return liveTv.isNotEmpty() && pkg != null && liveTv.contentEquals(pkg) && now < armedUntil
    }

    fun firstBindAfterBoot(boot: Int, lastBoot: Int, uptimeMs: Long): Boolean {
        return boot >= 0 && boot != lastBoot && uptimeMs < BOOT_WINDOW_MS
    }

    fun isShortPress(downTime: Long, upTime: Long, longPressTimeout: Long): Boolean {
        return upTime - downTime < longPressTimeout
    }
}
