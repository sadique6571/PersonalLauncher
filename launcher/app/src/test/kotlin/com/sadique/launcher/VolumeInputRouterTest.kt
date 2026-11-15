package com.sadique.launcher

import org.junit.Test
import org.junit.Assert.*
import java.util.*

class VolumeInputRouterTest {
    var now: Long = 0
    val injected: MutableList<Int> = ArrayList()
    val tasks: PriorityQueue<Task> = PriorityQueue(Comparator.comparingLong { t: Task -> t.time })
    class Task(var time: Long, var run: Runnable) { var cancelled = false }
    private val controller = FastVolumeController({ now }, { run, delay ->
        val task = Task(now + delay, run); tasks.add(task); FastVolumeController.Cancel { task.cancelled = true }
    }, { key -> injected.add(key) }, { error -> throw AssertionError(error) })
    private val router = VolumeInputRouter(controller)
    fun advance(delta: Long) {
        val end = now + delta
        while (tasks.isNotEmpty() && tasks.peek().time <= end) {
            val t = tasks.remove()
            now = t.time
            if (!t.cancelled) t.run.run()
        }
        now = end
    }
    fun line(device: Int, text: String) { router.line("[ 3211.781963] /dev/input/event" + device + ": " + text) }
    fun down(device: Int) { line(device, "EV_MSC MSC_SCAN 000c0080"); line(device, "EV_KEY KEY_UNKNOWN DOWN"); line(device, "EV_SYN SYN_REPORT 00000000") }
    @Test fun unrelatedDeviceCannotConsumeScanOrReleaseHeldVolume() {
        line(3, "EV_MSC MSC_SCAN 000c0080")
        line(2, "EV_KEY KEY_UNKNOWN UP"); line(3, "EV_KEY KEY_UNKNOWN DOWN")
        line(3, "EV_SYN SYN_REPORT 00000000"); advance(350)
        assertEquals(2, injected.size)
        line(2, "EV_KEY KEY_UNKNOWN UP"); advance(50); assertEquals(3, injected.size)
        line(3, "EV_KEY KEY_UNKNOWN UP"); advance(500); assertEquals(3, injected.size)
    }
    @Test fun remoteCanReconnectWithDifferentEventNumber() {
        down(3); advance(300); assertEquals(1, injected.size)
        router.line("remove device 2: /dev/input/event3"); advance(500); assertEquals(1, injected.size)
        router.line("add device 4: /dev/input/event7"); down(7); advance(300); assertEquals(2, injected.size)
        line(3, "EV_KEY KEY_UNKNOWN UP"); advance(50); assertEquals(3, injected.size)
    }
}
