package com.sadique.launcher

import java.util.ArrayList
import java.util.List
import org.junit.Test
import org.junit.Assert.*

class VolumePanelUpdatesTest {
    @Test fun heldVolumeKeepsOneUiTaskWithNewestSignal() {
        val queue: MutableList<Runnable> = ArrayList()
        val shown: MutableList<Long> = ArrayList()
        val updates = VolumePanelUpdates(queue::add, shown::add)
        for (time in 100L..2100L step 50) updates.offer(time)
        assertEquals(1, queue.size)
        queue.removeAt(0).run()
        assertEquals(List.of(2100L), shown)
        updates.offer(2150)
        assertEquals(1, queue.size); queue.removeAt(0).run()
        assertEquals(List.of(2100L, 2150L), shown)
    }
    @Test fun signalDuringDisplaySchedulesNextUpdate() {
        val queue: MutableList<Runnable> = ArrayList()
        val shown: MutableList<Long> = ArrayList()
        val holder = arrayOfNulls<VolumePanelUpdates>(1)
        holder[0] = VolumePanelUpdates(queue::add) { time ->
            shown.add(time)
            if (time == 100L) holder[0]!!.offer(150)
        }
        holder[0]!!.offer(100); queue.removeAt(0).run()
        assertEquals(1, queue.size); queue.removeAt(0).run()
        assertEquals(List.of(100L, 150L), shown)
    }
    @Test fun closeDropsQueuedAndFutureUpdates() {
        val queue: MutableList<Runnable> = ArrayList()
        val shown: MutableList<Long> = ArrayList()
        val updates = VolumePanelUpdates(queue::add, shown::add)
        updates.offer(100); updates.close(); queue.removeAt(0).run(); updates.offer(150)
        assertTrue(shown.isEmpty()); assertTrue(queue.isEmpty())
    }
    @Test fun delayedAndInvalidSignalsCannotResurrectPanel() {
        assertTrue(VolumePanelUpdates.fresh(100, 100))
        assertTrue(VolumePanelUpdates.fresh(100, 1100))
        assertFalse(VolumePanelUpdates.fresh(100, 1101))
        assertFalse(VolumePanelUpdates.fresh(0, 100))
        assertFalse(VolumePanelUpdates.fresh(150, 100))
    }
    @Test fun physicalDownSignalsBeforeHoldWithoutAddingTap() {
        val calls: MutableList<String> = ArrayList()
        val controller = FastVolumeController({ 100L },
                { _, delay -> calls.add("timer:" + delay); FastVolumeController.Cancel { } },
                { key -> calls.add("injected:" + key) }, { error -> fail(error.toString()) }, 50,
                { calls.add("panel") })
        controller.down(24)
        assertEquals(List.of("panel", "timer:300"), calls)
        controller.down(24) // Duplicate DOWN from the remote does not add a tap/signal.
        assertEquals(2, calls.size)
        controller.up(24); controller.down(25)
        assertEquals(List.of("panel", "timer:300", "panel", "timer:300"), calls)
    }
}
