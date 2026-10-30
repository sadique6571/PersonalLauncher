package com.sadique.launcher;

final class WatchNextModel {
    static boolean resumable(int type, long position, long duration) {
        return type == 0 && (duration <= 0 || position < duration);
    }
    static int progress(long position, long duration) {
        if (duration <= 0) return 0;
        return (int) Math.round(1000d * Math.max(0, Math.min(position, duration)) / duration);
    }
}
