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

    private BootLog() {
    }

    /** A single timestamped event, e.g. {@code BootLog.mark("home onCreate")}. */
    public static void mark(String what) {
        Log.i(TAG, SystemClock.elapsedRealtime() + "ms " + what);
    }

    /** An event plus how long since {@code startMs} (also from elapsedRealtime). */
    public static void mark(String what, long startMs) {
        long now = SystemClock.elapsedRealtime();
        Log.i(TAG, now + "ms " + what + " took=" + (now - startMs) + "ms");
    }
}
