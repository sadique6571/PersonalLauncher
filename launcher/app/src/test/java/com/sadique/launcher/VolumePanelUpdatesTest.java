package com.sadique.launcher;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class VolumePanelUpdatesTest {
    @Test public void heldVolumeKeepsOneUiTaskWithNewestSignal() {
        List<Runnable> queue = new ArrayList<>();
        List<Long> shown = new ArrayList<>();
        VolumePanelUpdates updates = new VolumePanelUpdates(queue::add, shown::add);
        for (long time = 100; time <= 2100; time += 50) updates.offer(time);
        assertEquals(1,queue.size());
        queue.remove(0).run();
        assertEquals(List.of(2100L),shown);
        updates.offer(2150);
        assertEquals(1,queue.size()); queue.remove(0).run();
        assertEquals(List.of(2100L,2150L),shown);
    }
    @Test public void signalDuringDisplaySchedulesNextUpdate() {
        List<Runnable> queue = new ArrayList<>();
        List<Long> shown = new ArrayList<>();
        VolumePanelUpdates[] holder = new VolumePanelUpdates[1];
        holder[0] = new VolumePanelUpdates(queue::add, time -> {
            shown.add(time);
            if (time == 100) holder[0].offer(150);
        });
        holder[0].offer(100); queue.remove(0).run();
        assertEquals(1,queue.size()); queue.remove(0).run();
        assertEquals(List.of(100L,150L),shown);
    }
    @Test public void closeDropsQueuedAndFutureUpdates() {
        List<Runnable> queue = new ArrayList<>();
        List<Long> shown = new ArrayList<>();
        VolumePanelUpdates updates = new VolumePanelUpdates(queue::add, shown::add);
        updates.offer(100); updates.close(); queue.remove(0).run(); updates.offer(150);
        assertTrue(shown.isEmpty()); assertTrue(queue.isEmpty());
    }
    @Test public void delayedAndInvalidSignalsCannotResurrectPanel() {
        assertTrue(VolumePanelUpdates.fresh(100,100));
        assertTrue(VolumePanelUpdates.fresh(100,1100));
        assertFalse(VolumePanelUpdates.fresh(100,1101));
        assertFalse(VolumePanelUpdates.fresh(0,100));
        assertFalse(VolumePanelUpdates.fresh(150,100));
    }
    @Test public void physicalDownSignalsBeforeHoldWithoutAddingTap() {
        List<String> calls = new ArrayList<>();
        FastVolumeController controller = new FastVolumeController(() -> 100,
                (task, delay) -> { calls.add("timer:" + delay); return () -> { }; },
                key -> calls.add("injected:" + key), error -> fail(error.toString()),50,
                () -> calls.add("panel"));
        controller.down(24);
        assertEquals(List.of("panel","timer:300"),calls);
        controller.down(24); // Duplicate DOWN from the remote does not add a tap/signal.
        assertEquals(2,calls.size());
        controller.up(24); controller.down(25);
        assertEquals(List.of("panel","timer:300","panel","timer:300"),calls);
    }
}
