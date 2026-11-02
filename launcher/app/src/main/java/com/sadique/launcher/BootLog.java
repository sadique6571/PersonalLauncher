package com.sadique.launcher;

import android.os.SystemClock;
import android.util.Log;

/**
 * Minimal boot-timing logs. Uses {@link SystemClock#elapsedRealtime()}, which is monotonic, so the
 * wall-clock jumps that happen during boot (NTP, timezone) cannot skew the numbers. A handful of
 * lines per boot; cheap enough to leave on.
 */
public final class BootLog {
    private static final String TAG = "LauncherBoot";
    private static final java.util.ArrayDeque<String> EVENTS = new java.util.ArrayDeque<>();
    private static synchronized void remember(String event) {
        if (EVENTS.size() == 32) EVENTS.removeFirst();
        EVENTS.addLast(event);
    }
    static synchronized String snapshot() { return String.join("\n", EVENTS); }

    private BootLog() {
    }

    /** A single timestamped event, e.g. {@code BootLog.mark("home onCreate")}. */
    public static void mark(String what) {
        String event = SystemClock.elapsedRealtime() + "ms " + what;
        remember(event); Log.i(TAG, event);
    }

    /** An event plus how long since {@code startMs} (also from elapsedRealtime). */
    public static void mark(String what, long startMs) {
        long now = SystemClock.elapsedRealtime();
        String event = now + "ms " + what + " took=" + (now - startMs) + "ms";
        remember(event); Log.i(TAG, event);
    }
}
