package com.sadique.launcher

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuUsageSamplerTest {
    private class Fake : CpuUsageSampler.Source {
        var stat = "cpu 60 0 0 40"
        var blockProc = false
        var blockIdle = false
        var procCalls = 0
        var idleCalls = 0
        var cores = 4
        var idle = 1000000L
        var wall = 0L
        var cpu = 0L

        override fun procStat(): String {
            procCalls++
            if (blockProc) throw IOException()
            return stat
        }

        override fun idleMicros(): LongArray? {
            idleCalls++
            if (blockIdle) throw IOException()
            return longArrayOf(idle, cores.toLong())
        }

        override fun elapsedMillis(): Long = wall
        override fun processCpuMillis(): Long = cpu
        override fun processors(): Int = cores
    }

    @Test fun systemCountersProduceActualDeltaAndResetWithoutInventingBusyTime() {
        val source = Fake()
        val sampler = CpuUsageSampler(source)
        assertFalse(sampler.sample().ready())
        source.stat = "cpu 80 0 0 60"
        val sample = sampler.sample()
        assertTrue(sample.system)
        assertEquals(50.0, sample.percent, 0.001)
        source.stat = "cpu 30 0 0 20"
        assertFalse(sampler.sample().ready())
        source.stat = "cpu 80 0 0 70"
        assertEquals(50.0, sampler.sample().percent, 0.001)
    }

    @Test fun restrictedProcFallsBackToRealIdleCounters() {
        val source = Fake()
        source.blockProc = true
        val sampler = CpuUsageSampler(source)
        assertFalse(sampler.sample().ready())
        source.wall = 1000
        source.idle += 2000000
        val sample = sampler.sample()
        assertTrue(sample.system)
        assertEquals(50.0, sample.percent, 0.001)
        assertEquals(1, source.procCalls)
    }

    @Test fun restrictedSystemSourcesUseAnExplicitlyLabelledProcessMeasurement() {
        val source = Fake()
        source.blockProc = true
        source.blockIdle = true
        val sampler = CpuUsageSampler(source)
        val first = sampler.sample()
        assertFalse(first.ready())
        assertFalse(first.system)
        source.wall = 1000
        source.cpu = 400
        val sample = sampler.sample()
        assertFalse(sample.system)
        assertEquals(10.0, sample.percent, 0.001)
        source.wall = 2000
        source.cpu = 800
        assertEquals(10.0, sampler.sample().percent, 0.001)
        assertEquals(1, source.procCalls)
        assertEquals(1, source.idleCalls)
    }

    @Test fun cpuHotplugResetsTheIdleWindow() {
        val source = Fake()
        source.blockProc = true
        val sampler = CpuUsageSampler(source)
        sampler.sample()
        source.wall = 1000
        source.cores = 2
        source.idle += 500000
        assertFalse(sampler.sample().ready())
        source.wall = 2000
        source.idle += 1000000
        assertEquals(50.0, sampler.sample().percent, 0.001)
    }
}
