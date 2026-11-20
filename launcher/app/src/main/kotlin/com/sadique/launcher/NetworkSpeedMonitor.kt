package com.sadique.launcher

import android.content.SharedPreferences
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import java.time.LocalDate

/**
 * Header network speed + today's TOTAL device usage, e.g. "500 Kbps/30 MB".
 * Speed is the live transfer rate. The total is the whole device's up + down
 * traffic for the local day — every app, not just the launcher — because it is
 * computed from the device-wide TrafficStats counters against a baseline saved
 * at the start of the day. Traffic used while Home is closed still counts: the
 * next tick compares the current counter against the saved baseline instead of
 * only adding up ticks seen while running. The total resets by itself at
 * midnight, and a reboot inside the day banks the usage counted so far instead
 * of losing it.
 */
internal class NetworkSpeedMonitor(
    private val prefs: SharedPreferences,
    private val view: () -> TextView?,
) {
    /** Persisted day state plus the total derived from it. */
    data class DayUsage(val base: Long, val accum: Long, val last: Long, val total: Long)

    companion object {
        private const val DAY_KEY = "net_speed_day"
        private const val BASE_KEY = "net_day_base"
        private const val ACCUM_KEY = "net_day_accum"
        private const val LAST_KEY = "net_day_last"
        private const val INTERVAL_MS = 1000L
        private const val MB = 1024L * 1024L
        private const val GB = MB * 1024L

        /** Bits per second as "500 Kbps" below 1 Mbps, else "1.5 Mbps". */
        @JvmStatic
        fun formatSpeed(bytesPerSecond: Long): String {
            val bits = (if (bytesPerSecond < 0) 0 else bytesPerSecond) * 8L
            if (bits < 1_000_000L) return "${bits / 1000L} Kbps"
            val whole = bits / 1_000_000L
            val tenth = (bits % 1_000_000L) / 100_000L
            return "$whole.$tenth Mbps"
        }

        /** Bytes as "30 MB" below 1 GB, else "1.5 GB". */
        @JvmStatic
        fun formatTotal(bytes: Long): String {
            val total = if (bytes < 0) 0 else bytes
            if (total < GB) return "${total / MB} MB"
            val tenths = total * 10L / GB
            return "${tenths / 10L}.${tenths % 10L} GB"
        }

        /**
         * Rolls the persisted day state forward for one [current] device-wide
         * counter reading. A reading below the last one means the counters
         * reset (reboot), so the usage counted before the reboot is banked
         * into [DayUsage.accum] and the baseline restarts at [current].
         */
        @JvmStatic
        fun rollDay(current: Long, base: Long, accum: Long, last: Long): DayUsage {
            var newBase = base
            var newAccum = accum
            if (current < last) {
                newAccum += (last - newBase).coerceAtLeast(0L)
                newBase = current
            }
            val total = newAccum + (current - newBase).coerceAtLeast(0L)
            return DayUsage(newBase, newAccum, current, total)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastBytes = -1L
    private var lastTime = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            sample()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        lastBytes = -1L
        lastTime = 0L
        handler.removeCallbacks(tick)
        tick.run()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun totalBytes(): Long {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val unsupported = TrafficStats.UNSUPPORTED.toLong()
        if (rx == unsupported && tx == unsupported) return -1L
        return (if (rx < 0) 0L else rx) + (if (tx < 0) 0L else tx)
    }

    private fun sample() {
        val textView = view() ?: return
        val current = totalBytes()
        if (current < 0) {
            textView.visibility = View.GONE
            return
        }
        textView.visibility = View.VISIBLE
        val today = LocalDate.now().toEpochDay()
        val usage = if (prefs.getLong(DAY_KEY, -1L) != today) {
            val fresh = DayUsage(current, 0L, current, 0L)
            prefs.edit().putLong(DAY_KEY, today).putLong(BASE_KEY, fresh.base)
                .putLong(ACCUM_KEY, 0L).putLong(LAST_KEY, current).apply()
            fresh
        } else {
            val rolled = rollDay(
                current,
                prefs.getLong(BASE_KEY, current),
                prefs.getLong(ACCUM_KEY, 0L),
                prefs.getLong(LAST_KEY, current),
            )
            prefs.edit().putLong(BASE_KEY, rolled.base).putLong(ACCUM_KEY, rolled.accum)
                .putLong(LAST_KEY, rolled.last).apply()
            rolled
        }
        val now = SystemClock.elapsedRealtime()
        val bytesPerSecond = if (lastBytes < 0) 0L else {
            val delta = (current - lastBytes).coerceAtLeast(0L)
            val elapsed = (now - lastTime).coerceAtLeast(1L)
            delta * 1000L / elapsed
        }
        lastBytes = current
        lastTime = now
        textView.text = formatSpeed(bytesPerSecond) + "/" + formatTotal(usage.total)
    }
}
