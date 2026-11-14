package com.sadique.launcher

import android.os.SystemClock
import android.util.Log
import java.util.ArrayDeque

/**
 * Minimal boot-timing logs. Uses [SystemClock.elapsedRealtime], which is monotonic, so the
 * wall-clock jumps that happen during boot (NTP, timezone) cannot skew the numbers. A handful of
 * lines per boot; cheap enough to leave on.
 */
object BootLog {
    private const val TAG = "LauncherBoot"
    private val EVENTS = ArrayDeque<String>()

    @Synchronized
    private fun remember(event: String) {
        if (EVENTS.size == 32) EVENTS.removeFirst()
        EVENTS.addLast(event)
    }

    @Synchronized
    @JvmStatic
    fun snapshot(): String {
        return EVENTS.joinToString("\n")
    }

    /** A single timestamped event, e.g. `BootLog.mark("home onCreate")`. */
    @JvmStatic
    fun mark(what: String) {
        val event = "${SystemClock.elapsedRealtime()}ms $what"
        remember(event)
        Log.i(TAG, event)
    }

    /** An event plus how long since `startMs` (also from elapsedRealtime). */
    @JvmStatic
    fun mark(what: String, startMs: Long) {
        val now = SystemClock.elapsedRealtime()
        val event = "${now}ms $what took=${now - startMs}ms"
        remember(event)
        Log.i(TAG, event)
    }
}
