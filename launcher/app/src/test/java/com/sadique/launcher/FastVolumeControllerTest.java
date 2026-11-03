package com.sadique.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import org.junit.Test;
import static org.junit.Assert.*;

public class FastVolumeControllerTest {
    private long now;
    private final PriorityQueue<Task> tasks = new PriorityQueue<>((a,b) -> Long.compare(a.when, b.when));
    private final List<Integer> keys = new ArrayList<>();
    private final List<Exception> errors = new ArrayList<>();
    private boolean fail;
    private final FastVolumeController controller = new FastVolumeController(() -> now, (task, delay) -> {
        Task next = new Task(now + delay, task); tasks.add(next); return () -> next.cancelled = true;
    }, key -> { if (fail) throw new IOException("injection failed"); keys.add(key); }, errors::add);
    private final FastVolumeController.Parser parser = new FastVolumeController.Parser(controller);
    private static final class Task {
        final long when; final Runnable run; boolean cancelled;
        Task(long when, Runnable run) { this.when = when; this.run = run; }
    }
    private void advance(long delay) {
        long end = now + delay;
        while (!tasks.isEmpty() && tasks.peek().when <= end) {
            Task task = tasks.remove(); now = task.when; if (!task.cancelled) task.run.run();
        }
        now = end;
    }
    private void bluetooth(String usage, String action) {
        parser.line("[ 2958.911284] /dev/input/event3: EV_MSC MSC_SCAN " + usage);
        parser.line("[ 2958.911284] /dev/input/event3: EV_KEY KEY_UNKNOWN " + action + "     \r");
        parser.line("[ 2958.911284] /dev/input/event3: EV_SYN SYN_REPORT 00000000");
    }
    @Test public void ordinarySinglePressAddsNoRepeat() {
        controller.down(24); advance(180); controller.up(24); advance(1000);
        assertTrue(keys.isEmpty());
    }
    @Test public void startsAt300msAndAddsOnePointEvery50ms() {
        controller.down(24); advance(299); assertTrue(keys.isEmpty());
        advance(1); assertEquals(1, keys.size());
        advance(150); assertEquals(4, keys.size());
        assertTrue(keys.stream().allMatch(k -> k == 24));
    }
    @Test public void releaseCancelsAllFutureSteps() {
        controller.down(25); advance(320); controller.up(25);
        int count = keys.size(); advance(1000); assertEquals(count, keys.size());
    }
    @Test public void duplicateDownDoesNotRestartHoldThreshold() {
        controller.down(24); advance(200); controller.down(24); advance(100);
        assertEquals(1, keys.size());
    }
    @Test public void directionChangeRejectsStaleReleaseAndTick() {
        controller.down(24); Task old = tasks.peek(); advance(100); controller.down(25);
        controller.up(24); old.run.run(); assertTrue(keys.isEmpty());
        advance(300); assertEquals(Integer.valueOf(25), keys.get(0));
    }
    @Test public void lostReleaseHasThirtySecondLimit() {
        controller.down(24); advance(30_000); int count = keys.size();
        assertTrue(count > 0); advance(1000); assertEquals(count, keys.size());
    }
    @Test public void injectionFailureStopsAndReportsOnce() {
        fail = true; controller.down(24); advance(300); advance(1000);
        assertEquals(1, errors.size()); assertTrue(keys.isEmpty());
    }
    @Test public void capturedBluetoothFramesControlBothDirections() {
        bluetooth("000c0080", "DOWN"); advance(300); bluetooth("000c0080", "UP");
        bluetooth("000c0081", "DOWN"); advance(300); bluetooth("000c0081", "UP");
        assertEquals(java.util.Arrays.asList(24, 25), keys);
    }
    @Test public void unrelatedUsageAndOldFramesCannotInjectVolume() {
        bluetooth("000c009f", "DOWN"); bluetooth("000c009f", "UP");
        parser.line("EV_MSC MSC_SCAN 000c0080"); parser.line("EV_SYN SYN_REPORT 00000000");
        parser.line("EV_KEY KEY_UNKNOWN DOWN"); advance(1000); assertTrue(keys.isEmpty());
    }
    @Test public void unrelatedReleaseDoesNotCancelHeldVolumeButMissingUsageDoes() {
        bluetooth("000c0080", "DOWN"); bluetooth("000c009f", "UP"); advance(300);
        assertEquals(1, keys.size()); parser.line("EV_KEY KEY_UNKNOWN UP");
        advance(1000); assertEquals(1, keys.size());
    }
    @Test public void standardKeysAndNativeRepeatsAreHandled() {
        parser.line("EV_KEY KEY_VOLUMEDOWN DOWN   "); advance(300);
        assertEquals(Integer.valueOf(25), keys.get(0));
        parser.line("EV_KEY KEY_VOLUMEDOWN REPEAT   "); advance(1000); assertEquals(1, keys.size());
    }
    @Test public void otherKeyCodesCannotStartRepeat() {
        controller.down(83); advance(1000); assertTrue(keys.isEmpty());
    }
    @Test public void configuredRepeatWaitIsUsedWithoutChangingHoldThreshold() {
        FastVolumeController slower = new FastVolumeController(() -> now, (task, delay) -> {
            Task next = new Task(now + delay, task); tasks.add(next); return () -> next.cancelled = true;
        }, keys::add, errors::add, 100);
        slower.down(24); advance(300); assertEquals(1, keys.size());
        advance(99); assertEquals(1, keys.size()); advance(1); assertEquals(2, keys.size());
    }
    @Test(expected=IllegalArgumentException.class) public void repeatWaitBelow50msIsRejected() {
        new FastVolumeController(() -> now, (task, delay) -> () -> {}, keys::add, errors::add, 49);
    }
    @Test(expected=IllegalArgumentException.class) public void repeatWaitAboveOneSecondIsRejected() {
        new FastVolumeController(() -> now, (task, delay) -> () -> {}, keys::add, errors::add, 1001);
    }
}
