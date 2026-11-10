package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class GlassOsMetricsTest {
    @Test public void unavailableOrTruncatedCpuCountersNeverBecomeFakeReadings(){
        assertNull(GlassOsMetrics.cpuTicks(null));assertNull(GlassOsMetrics.cpuTicks("cpu 1 2"));
        assertNull(GlassOsMetrics.cpuTicks("cpu0 1 2 3 4"));assertNull(GlassOsMetrics.cpuTicks("cpu 1 x 3 4"));
        assertNull(GlassOsMetrics.cpuTicks("cpu 1 -2 3 4"));
    }
    @Test public void cpuTotalsIncludeIoWaitAndStealButDoNotDoubleCountGuestTime(){
        assertArrayEquals(new long[]{360,90},GlassOsMetrics.cpuTicks("cpu 10 20 30 40 50 60 70 80 900 900"));
        assertArrayEquals(new long[]{10,4},GlassOsMetrics.cpuTicks("cpu 1 2 3 4"));
    }
    @Test public void unavailableAndRacingMemorySnapshotsAreBounded(){
        assertEquals(0,GlassOsMetrics.percent(100,0));assertEquals(0,GlassOsMetrics.percent(-1,100));
        assertEquals(100,GlassOsMetrics.percent(200,100));assertEquals(50,GlassOsMetrics.percent(2,4));
        assertEquals(100,GlassOsMetrics.percent(Long.MAX_VALUE,Long.MAX_VALUE));
    }
}
