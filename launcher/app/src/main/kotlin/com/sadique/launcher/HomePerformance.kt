package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences

/** Performance-tier gating for Home effects. Pure move from PersonalHomeActivity. */
internal class HomePerformance(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val sky: () -> HomeWallpaperView?,
) {
    /**
     * Applies the performance profile. In Light mode, disables expensive effects:
     * crossfade, parallax, entrance cascade, clock seconds, auto-scroll, and shadows.
     */
    fun applyProfile() {
        val tier = MemoryProfile.effectiveTier(activity, prefs)
        if (tier == MemoryProfile.TIER_LIGHT) {
            // Light mode defaults: static, minimal.
            // Note: we don't force-overwrite user prefs here; the drawing code
            // checks MemoryProfile.allow*() at draw time. This just ensures
            // the crossfade buffer isn't allocated.
            sky()?.releaseTransient()
        }
    }

    /** Returns true if the named effect is allowed in the current performance tier. */
    fun allows(effect: String): Boolean {
        val tier = MemoryProfile.effectiveTier(activity, prefs)
        return when (effect) {
            "crossfade" -> MemoryProfile.allowCrossfade(tier)
            "parallax" -> MemoryProfile.allowParallax(tier)
            "autoscroll" -> MemoryProfile.allowAutoScroll(tier)
            else -> true
        }
    }
}
