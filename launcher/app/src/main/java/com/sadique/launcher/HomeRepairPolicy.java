package com.sadique.launcher;

/** At most two automatic foreground repairs per process, with a one-minute gap. */
final class HomeRepairPolicy {
    private int attempts;
    private long last=-1;
    synchronized boolean allow(long now,boolean alreadyHome) {
        if (alreadyHome || attempts>=2 || (last>=0 && now>=last && now-last<60_000)) return false;
        attempts++; last=now; return true;
    }
}
