package com.sadique.launcher;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Allow a short quiet period for duplicate/stale mDNS records; never scan ports. */
final class LocalAdbCandidates {
    private final LinkedHashSet<Integer> ports=new LinkedHashSet<>();
    private long changed;
    private boolean finished;
    synchronized void add(int port) {
        if (port>0 && port<=65535 && ports.size()<16 && ports.add(port)) {
            changed=System.nanoTime(); notifyAll();
        }
    }
    synchronized void finish() { finished=true; notifyAll(); }
    synchronized List<Integer> await(long timeoutMs,long quietMs) throws IOException,InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (!finished) {
            long now=System.nanoTime(), remaining=deadline-now;
            if (!ports.isEmpty()) remaining=Math.min(remaining,changed+TimeUnit.MILLISECONDS.toNanos(quietMs)-now);
            if (remaining<=0) break;
            TimeUnit.NANOSECONDS.timedWait(this,remaining);
        }
        if (ports.isEmpty()) throw new IOException("No local wireless-debugging endpoint");
        return new ArrayList<>(ports);
    }
}
