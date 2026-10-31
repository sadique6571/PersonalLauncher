package com.sadique.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Memory profile for the launcher. Detects the device's RAM class via
 * ActivityManager (not just model-name matching) and maps it to a
 * Light / Balanced / Full performance tier.
 *
 * <p>Haier H40K800FX has ~1.5 GB total RAM shared with Android, so it
 * lands in the Light tier by default. The user can override via
 * Settings → System → Performance.
 */
final class MemoryProfile {

    /** Performance tiers. */
    static final int TIER_LIGHT = 0;    // <= 2 GB RAM: minimal effects, static wallpaper
    static final int TIER_BALANCED = 1; // 2-4 GB: some effects, bounded caches
    static final int TIER_FULL = 2;     // > 4 GB: all effects

    private MemoryProfile() {}

    /** Returns total RAM in bytes, or -1 if unavailable. */
    static long totalRam(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return -1;
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            return info.totalMem;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Auto-detected tier from actual device RAM. */
    static int autoTier(Context ctx) {
        long ram = totalRam(ctx);
        if (ram <= 0) {
            // Fallback: check low-RAM flag.
            try {
                ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null && am.isLowRamDevice()) return TIER_LIGHT;
            } catch (Exception ignored) {}
            return TIER_BALANCED;
        }
        long gb = ram / (1024L * 1024L * 1024L);
        if (gb <= 2) return TIER_LIGHT;
        if (gb <= 4) return TIER_BALANCED;
        return TIER_FULL;
    }

    /**
     * Effective tier: user override if set (0=Auto, 1=Light, 2=Balanced, 3=Full),
     * otherwise the auto-detected tier.
     */
    static int effectiveTier(Context ctx, SharedPreferences prefs) {
        int override = prefs.getInt("perf_mode", 0);
        if (override == 1) return TIER_LIGHT;
        if (override == 2) return TIER_BALANCED;
        if (override == 3) return TIER_FULL;
        return autoTier(ctx);
    }

    /** Max icon cache size in bytes for the tier. */
    static long iconCacheBytes(int tier) {
        switch (tier) {
            case TIER_LIGHT: return 8L * 1024L * 1024L;   // 8 MB
            case TIER_BALANCED: return 24L * 1024L * 1024L; // 24 MB
            default: return 48L * 1024L * 1024L;            // 48 MB
        }
    }

    /** Max wallpaper decode dimension (long edge) for the tier. */
    static int wallpaperMaxDim(int tier) {
        switch (tier) {
            case TIER_LIGHT: return 1280;
            case TIER_BALANCED: return 1600;
            default: return 1920;
        }
    }

    /** Whether crossfade animation is allowed in this tier. */
    static boolean allowCrossfade(int tier) {
        return tier >= TIER_BALANCED;
    }

    /** Whether parallax wallpaper effect is allowed. */
    static boolean allowParallax(int tier) {
        return tier >= TIER_BALANCED;
    }

    /** Whether staggered entrance animation is allowed. */
    static boolean allowEntrance(int tier) {
        return tier >= TIER_BALANCED;
    }

    /** Whether poster auto-scroll is allowed. */
    static boolean allowAutoScroll(int tier) {
        return tier == TIER_FULL;
    }

    /** Slideshow interval multiplier (Light = less frequent). */
    static int slideshowMultiplier(int tier) {
        return tier == TIER_LIGHT ? 2 : 1;
    }
}
