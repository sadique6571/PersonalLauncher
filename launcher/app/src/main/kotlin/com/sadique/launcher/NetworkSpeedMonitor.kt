package com.sadique.launcher

import android.content.Context
import android.content.SharedPreferences
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView

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
    companion object {
        private var daily: DailyNetworkUsage? = null
        private var backgroundReaderStarted = false

        private fun totalBytes(): Long {
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            return if (rx < 0 || tx < 0) -1 else rx + tx
        }

        private fun tracker(context: Context, prefs: SharedPreferences): DailyNetworkUsage {
            val app = context.applicationContext
            val tracker = daily ?: run {
                val store = app.getSharedPreferences("device_daily_usage", Context.MODE_PRIVATE)
                if (!store.contains("net_speed_day") && prefs.contains("net_speed_day")) {
                    val edit = store.edit()
                    for (key in listOf("net_speed_day", "net_day_base", "net_day_accum", "net_day_last")) {
                        if (prefs.contains(key)) edit.putLong(key, prefs.getLong(key, 0L))
                    }
                    edit.apply()
                }
                DailyNetworkUsage(app, store).also { daily = it }
            }
            if (!backgroundReaderStarted) {
                backgroundReaderStarted = true
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed(object : Runnable {
                    override fun run() {
                        tracker.sample(totalBytes())
                        handler.postDelayed(this, 10_000L)
                    }
                }, 10_000L)
            }
            return tracker
        }

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

    private fun sample() {
        val textView = view() ?: return
        val current = totalBytes()
        if (current < 0) {
            textView.visibility = View.GONE
            return
        }
        textView.visibility = View.VISIBLE
        val usage = tracker(textView.context, prefs).sample(current)
        val now = SystemClock.elapsedRealtime()
        val bytesPerSecond = if (lastBytes < 0) 0L else {
            val delta = (current - lastBytes).coerceAtLeast(0L)
            val elapsed = (now - lastTime).coerceAtLeast(1L)
            delta * 1000L / elapsed
        }
        lastBytes = current
        lastTime = now
        textView.text = formatSpeed(bytesPerSecond) + "/" + formatTotal(usage)
    }
}
