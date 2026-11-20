package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkSpeedFormatTest {
    @Test fun speedFormatsAsKbpsBelowOneMbps() {
        assertEquals("0 Kbps", NetworkSpeedMonitor.formatSpeed(0L))
        assertEquals("500 Kbps", NetworkSpeedMonitor.formatSpeed(62_500L))
        assertEquals("999 Kbps", NetworkSpeedMonitor.formatSpeed(124_999L))
        assertEquals("0 Kbps", NetworkSpeedMonitor.formatSpeed(-5L))
    }

    @Test fun speedFormatsAsMbpsAtAndAboveOneMbps() {
        assertEquals("1.0 Mbps", NetworkSpeedMonitor.formatSpeed(125_000L))
        assertEquals("1.5 Mbps", NetworkSpeedMonitor.formatSpeed(187_500L))
        assertEquals("10.0 Mbps", NetworkSpeedMonitor.formatSpeed(1_250_000L))
    }

    @Test fun totalFormatsAsMbBelowOneGb() {
        assertEquals("0 MB", NetworkSpeedMonitor.formatTotal(0L))
        assertEquals("30 MB", NetworkSpeedMonitor.formatTotal(30L * 1024L * 1024L))
        assertEquals("1023 MB", NetworkSpeedMonitor.formatTotal(1023L * 1024L * 1024L))
        assertEquals("0 MB", NetworkSpeedMonitor.formatTotal(-1L))
    }

    @Test fun totalFormatsAsGbAtAndAboveOneGb() {
        assertEquals("1.0 GB", NetworkSpeedMonitor.formatTotal(1024L * 1024L * 1024L))
        assertEquals("1.5 GB", NetworkSpeedMonitor.formatTotal(1536L * 1024L * 1024L))
        assertEquals("2.0 GB", NetworkSpeedMonitor.formatTotal(2L * 1024L * 1024L * 1024L))
    }

    @Test fun dayTotalIsCurrentMinusBaseline() {
        val usage = NetworkSpeedMonitor.rollDay(1000L, 100L, 0L, 900L)
        assertEquals(900L, usage.total)
        assertEquals(100L, usage.base)
        assertEquals(0L, usage.accum)
        assertEquals(1000L, usage.last)
    }

    @Test fun dayTotalBanksUsageBeforeReboot() {
        val usage = NetworkSpeedMonitor.rollDay(50L, 100L, 0L, 900L)
        assertEquals(800L, usage.total)
        assertEquals(50L, usage.base)
        assertEquals(800L, usage.accum)
        assertEquals(50L, usage.last)
    }

    @Test fun dayTotalKeepsBankedUsageAfterReboot() {
        val usage = NetworkSpeedMonitor.rollDay(150L, 50L, 800L, 50L)
        assertEquals(900L, usage.total)
        assertEquals(50L, usage.base)
        assertEquals(800L, usage.accum)
        assertEquals(150L, usage.last)
    }

    @Test fun dayTotalNeverGoesNegative() {
        val usage = NetworkSpeedMonitor.rollDay(50L, 100L, 0L, 40L)
        assertEquals(0L, usage.total)
    }
}
