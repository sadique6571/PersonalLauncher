package com.sadique.launcher

import java.util.HashMap
import java.util.Map
import org.junit.Test
import org.junit.Assert.*

class VolumeRepeatTest {
    private val timer = FakeTimer()
    private val repeat = VolumeKeys.Repeater(timer)
    private var volume = 0
    private fun key(code: Int, down: Long, time: Long, action: Int, count: Int, consume: Boolean) {
        repeat.event(5, code, down, time, action, count, false, consume,
                { volume += if (code == 24) 1 else -1 }, { volume })
    }
    @Test fun duplicateServiceEventsDoNotRestartHoldAndReleaseStopsIt() {
        key(24, 100, 100, 0, 0, false); timer.advance(300)
        key(24, 100, 400, 0, 0, false); timer.advance(199); assertEquals(0, volume)
        timer.advance(1); assertEquals(1, volume); timer.advance(120); assertEquals(3, volume)
        key(24, 100, 720, 1, 0, false); timer.advance(1000); assertEquals(3, volume)
    }
    @Test fun nativeVolumeChangesSkipFallbackButConsumeModeStillRepeats() {
        key(24, 100, 100, 0, 0, false); timer.advance(400)
        key(24, 100, 500, 0, 1, false); volume++
        timer.advance(100); assertEquals(1, volume)
        volume++; timer.advance(60); assertEquals(2, volume)
        key(25, 2000, 2000, 0, 0, true); key(25, 2000, 2100, 0, 1, true)
        timer.advance(500); assertEquals(1, volume)
    }
    @Test fun nativeRepeatEventsWithoutVolumeChangesDoNotDisableRepair() {
        key(24, 100, 100, 0, 0, false); timer.advance(400)
        key(24, 100, 500, 0, 1, false)
        timer.advance(100); assertEquals(1, volume)
        key(24, 100, 660, 0, 2, false)
        timer.advance(120); assertEquals(3, volume)
        key(24, 100, 720, 1, 0, false)
        timer.advance(1000); assertEquals(3, volume)
    }
    @Test fun staleReleaseDoesNotStopNewDirectionAndLostReleaseHasFuse() {
        key(24, 100, 100, 0, 0, false); key(25, 110, 110, 0, 0, false)
        key(24, 100, 120, 1, 0, false); timer.advance(500); assertEquals(-1, volume)
        timer.advance(30000); val stopped = volume; timer.advance(1000); assertEquals(stopped, volume)
    }
    @Test fun ownedTapAdjustsOnceAcrossBothHelpersAndReleaseCancelsHold() {
        val adjust = Runnable { volume++ }
        repeat.event(5, 24, 100, 100, 0, 0, false, true, true, adjust) { volume }
        repeat.event(5, 24, 100, 100, 0, 0, false, true, true, adjust) { volume }
        assertEquals(1, volume)
        key(24, 100, 200, 1, 0, true)
        timer.advance(1000); assertEquals(1, volume)
    }
    @Test fun ownedHoldRepeatsOnTimerAndIgnoresNativeRepeatEvents() {
        repeat.event(5, 24, 100, 100, 0, 0, false, true, true, { volume++ }) { volume }
        key(24, 100, 400, 0, 1, true)
        timer.advance(500); assertEquals(2, volume)
        timer.advance(120); assertEquals(4, volume)
        key(24, 100, 720, 1, 0, true)
        timer.advance(1000); assertEquals(4, volume)
    }
    private class FakeTimer : VolumeKeys.Timer {
        var now: Long = 0
        val pending: MutableMap<Runnable, Long> = HashMap()
        override fun post(task: Runnable, delay: Long) { pending[task] = now + delay }
        override fun cancel(task: Runnable) { pending.remove(task) }
        fun advance(delay: Long) {
            val end = now + delay
            while (true) {
                var next: Runnable? = null; var `when` = Long.MAX_VALUE
                for (entry in pending.entries) if (entry.value < `when`) { next = entry.key; `when` = entry.value }
                if (next == null || `when` > end) break
                now = `when`; pending.remove(next); next.run()
            }
            now = end
        }
    }
}
