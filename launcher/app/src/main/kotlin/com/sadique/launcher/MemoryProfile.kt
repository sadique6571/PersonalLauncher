package com.sadique.launcher

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences

/** Picks a performance tier using Android's memory information. */
object MemoryProfile {
    const val TIER_LIGHT = 0
    const val TIER_BALANCED = 1
    const val TIER_FULL = 2

    @JvmStatic
    fun totalRam(ctx: Context): Long {
        return try {
            val manager = ctx.getSystemService(ActivityManager::class.java) ?: return -1
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            info.totalMem
        } catch (_: Exception) {
            -1
        }
    }

    @JvmStatic
    fun autoTier(ctx: Context): Int {
        val ram = totalRam(ctx)
        if (ram <= 0) {
            val lowRam = try {
                ctx.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
            } catch (_: Exception) {
                false
            }
            return if (lowRam) TIER_LIGHT else TIER_BALANCED
        }
        val gb = ram / (1024L * 1024L * 1024L)
        return when {
            gb <= 2 -> TIER_LIGHT
            gb <= 4 -> TIER_BALANCED
            else -> TIER_FULL
        }
    }

    @JvmStatic
    fun effectiveTier(ctx: Context, prefs: SharedPreferences): Int = when (prefs.getInt("perf_mode", 0)) {
        1 -> TIER_LIGHT
        2 -> TIER_BALANCED
        3 -> TIER_FULL
        else -> autoTier(ctx)
    }

    @JvmStatic
    fun iconCacheBytes(tier: Int): Long = when (tier) {
        TIER_LIGHT -> 8L * 1024 * 1024
        TIER_BALANCED -> 24L * 1024 * 1024
        else -> 48L * 1024 * 1024
    }

    @JvmStatic
    fun wallpaperMaxDim(tier: Int): Int = when (tier) {
        TIER_LIGHT -> 1280
        TIER_BALANCED -> 1600
        else -> 1920
    }

    @JvmStatic fun allowCrossfade(tier: Int): Boolean = tier >= TIER_BALANCED
    @JvmStatic fun allowParallax(tier: Int): Boolean = tier >= TIER_BALANCED
    @JvmStatic fun allowEntrance(tier: Int): Boolean = tier >= TIER_BALANCED
    @JvmStatic fun allowAutoScroll(tier: Int): Boolean = tier == TIER_FULL
    @JvmStatic fun slideshowMultiplier(tier: Int): Int = if (tier == TIER_LIGHT) 2 else 1
}
