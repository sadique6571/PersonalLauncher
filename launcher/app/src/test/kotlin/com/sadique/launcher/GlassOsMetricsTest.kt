package com.sadique.launcher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlassOsMetricsTest {
    @Test fun unavailableOrTruncatedCpuCountersNeverBecomeFakeReadings() {
        assertNull(GlassOsMetrics.cpuTicks(null))
        assertNull(GlassOsMetrics.cpuTicks("cpu 1 2"))
        assertNull(GlassOsMetrics.cpuTicks("cpu0 1 2 3 4"))
        assertNull(GlassOsMetrics.cpuTicks("cpu 1 x 3 4"))
        assertNull(GlassOsMetrics.cpuTicks("cpu 1 -2 3 4"))
    }

    @Test fun cpuTotalsIncludeIoWaitAndStealButDoNotDoubleCountGuestTime() {
        assertArrayEquals(longArrayOf(360, 90), GlassOsMetrics.cpuTicks("cpu 10 20 30 40 50 60 70 80 900 900"))
        assertArrayEquals(longArrayOf(10, 4), GlassOsMetrics.cpuTicks("cpu 1 2 3 4"))
    }

    @Test fun unavailableAndRacingMemorySnapshotsAreBounded() {
        assertEquals(0, GlassOsMetrics.percent(100, 0))
        assertEquals(0, GlassOsMetrics.percent(-1, 100))
        assertEquals(100, GlassOsMetrics.percent(200, 100))
        assertEquals(50, GlassOsMetrics.percent(2, 4))
        assertEquals(100, GlassOsMetrics.percent(Long.MAX_VALUE, Long.MAX_VALUE))
    }
}
