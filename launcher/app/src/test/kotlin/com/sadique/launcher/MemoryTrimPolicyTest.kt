package com.sadique.launcher

import android.content.ComponentCallbacks2

import org.junit.Test

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class MemoryTrimPolicyTest {
    @Test
    fun ordinaryBackgroundTransitionKeepsWarmResources() {
        assertFalse(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, false))
        assertFalse(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, true))
    }

    @Test
    fun backgroundPressureReleasesResourcesOnlyWhenHomeIsStopped() {
        assertTrue(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, false))
        assertTrue(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, false))
        assertFalse(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, true))
        assertFalse(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, true))
    }

    @Test
    fun lowMemorySignalsReleaseResourcesWhenHomeIsStopped() {
        assertTrue(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, false))
        assertTrue(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, false))
    }
}
