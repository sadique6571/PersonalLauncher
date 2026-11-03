package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class VolumeInputRouterTest {
    long now;
    List<Integer> injected=new ArrayList<>();
    PriorityQueue<Task> tasks=new PriorityQueue<>(Comparator.comparingLong(t->t.time));
    static class Task { long time; Runnable run; boolean cancelled; Task(long time,Runnable run) { this.time=time; this.run=run; } }
    FastVolumeController controller=new FastVolumeController(()->now,(run,delay)-> {
        Task task=new Task(now+delay,run); tasks.add(task); return ()->task.cancelled=true;
    },injected::add,error->{throw new AssertionError(error);});
    VolumeInputRouter router=new VolumeInputRouter(controller);
    void advance(long delta) { long end=now+delta; while(!tasks.isEmpty() && tasks.peek().time<=end) { Task t=tasks.remove(); now=t.time; if(!t.cancelled)t.run.run(); } now=end; }
    void line(int device,String text) { router.line("[ 3211.781963] /dev/input/event"+device+": "+text); }
    void down(int device) { line(device,"EV_MSC MSC_SCAN 000c0080"); line(device,"EV_KEY KEY_UNKNOWN DOWN"); line(device,"EV_SYN SYN_REPORT 00000000"); }
    @Test public void unrelatedDeviceCannotConsumeScanOrReleaseHeldVolume() {
        line(3,"EV_MSC MSC_SCAN 000c0080");
        line(2,"EV_KEY KEY_UNKNOWN UP"); line(3,"EV_KEY KEY_UNKNOWN DOWN");
        line(3,"EV_SYN SYN_REPORT 00000000"); advance(350);
        assertEquals(2,injected.size());
        line(2,"EV_KEY KEY_UNKNOWN UP"); advance(50); assertEquals(3,injected.size());
        line(3,"EV_KEY KEY_UNKNOWN UP"); advance(500); assertEquals(3,injected.size());
    }
    @Test public void remoteCanReconnectWithDifferentEventNumber() {
        down(3); advance(300); assertEquals(1,injected.size());
        router.line("remove device 2: /dev/input/event3"); advance(500); assertEquals(1,injected.size());
        router.line("add device 4: /dev/input/event7"); down(7); advance(300); assertEquals(2,injected.size());
        line(3,"EV_KEY KEY_UNKNOWN UP"); advance(50); assertEquals(3,injected.size());
    }
}
