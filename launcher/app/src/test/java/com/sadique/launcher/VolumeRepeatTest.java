package com.sadique.launcher;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
public class VolumeRepeatTest {
    private final FakeTimer timer = new FakeTimer();
    private final VolumeKeys.Repeater repeat = new VolumeKeys.Repeater(timer);
    private int volume;
    private void key(int code, long down, long time, int action, int count, boolean consume) {
        repeat.event(5, code, down, time, action, count, false, consume, () -> volume += code == 24 ? 1 : -1);
    }
    @Test public void duplicateServiceEventsDoNotRestartHoldAndReleaseStopsIt() {
        key(24, 100, 100, 0, 0, false); timer.advance(300);
        key(24, 100, 400, 0, 0, false); timer.advance(199); assertEquals(0, volume);
        timer.advance(1); assertEquals(1, volume); timer.advance(120); assertEquals(3, volume);
        key(24, 100, 720, 1, 0, false); timer.advance(1000); assertEquals(3, volume);
    }
    @Test public void nativeRepeatCancelsFallbackButConsumeModeStillRepeats() {
        key(24, 100, 100, 0, 0, false); timer.advance(400);
        key(24, 100, 500, 0, 1, false); timer.advance(1000); assertEquals(0, volume);
        key(25, 2000, 2000, 0, 0, true); key(25, 2000, 2100, 0, 1, true);
        timer.advance(500); assertEquals(-1, volume);
    }
    @Test public void staleReleaseDoesNotStopNewDirectionAndLostReleaseHasFuse() {
        key(24, 100, 100, 0, 0, false); key(25, 110, 110, 0, 0, false);
        key(24, 100, 120, 1, 0, false); timer.advance(500); assertEquals(-1, volume);
        timer.advance(30000); int stopped = volume; timer.advance(1000); assertEquals(stopped, volume);
    }
    private static final class FakeTimer implements VolumeKeys.Timer {
        long now;
        final Map<Runnable,Long> pending = new HashMap<>();
        public void post(Runnable task, long delay) { pending.put(task, now + delay); }
        public void cancel(Runnable task) { pending.remove(task); }
        void advance(long delay) {
            long end = now + delay;
            while (true) {
                Runnable next = null; long when = Long.MAX_VALUE;
                for (Map.Entry<Runnable,Long> entry : pending.entrySet()) if (entry.getValue() < when) { next = entry.getKey(); when = entry.getValue(); }
                if (next == null || when > end) break;
                now = when; pending.remove(next); next.run();
            }
            now = end;
        }
    }
}
