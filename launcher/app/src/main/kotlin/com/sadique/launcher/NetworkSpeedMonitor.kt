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
 * Header network speed + today's total usage, e.g. "500 Kbps/30 MB".
 * Speed is the live transfer rate; the total is this device-local day's
 * up + down traffic and resets by itself at midnight. The accumulated
 * total survives a reboot inside the same day; only the rate re-baselines.
 */
internal class NetworkSpeedMonitor(
    private val prefs: SharedPreferences,
    private val view: () -> TextView?,
) {
    companion object {
        private const val DAY_KEY = "net_speed_day"
        private const val TOTAL_KEY = "net_speed_day_bytes"
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
    private var dayTotal = 0L

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
        dayTotal = loadDayTotal()
        handler.removeCallbacks(tick)
        tick.run()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
        persist()
    }

    private fun totalBytes(): Long {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val unsupported = TrafficStats.UNSUPPORTED.toLong()
        if (rx == unsupported && tx == unsupported) return -1L
        return (if (rx < 0) 0L else rx) + (if (tx < 0) 0L else tx)
    }

    private fun loadDayTotal(): Long {
        val today = LocalDate.now().toEpochDay()
        return if (prefs.getLong(DAY_KEY, -1L) == today) prefs.getLong(TOTAL_KEY, 0L) else 0L
    }

    private fun persist() {
        prefs.edit().putLong(DAY_KEY, LocalDate.now().toEpochDay()).putLong(TOTAL_KEY, dayTotal).apply()
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
        if (prefs.getLong(DAY_KEY, -1L) != today) {
            dayTotal = 0L
            prefs.edit().putLong(DAY_KEY, today).putLong(TOTAL_KEY, 0L).apply()
        }
        val now = SystemClock.elapsedRealtime()
        if (lastBytes < 0) {
            lastBytes = current
            lastTime = now
            textView.text = formatSpeed(0L) + "/" + formatTotal(dayTotal)
            return
        }
        var delta = current - lastBytes
        if (delta < 0) delta = 0L
        val elapsed = (now - lastTime).coerceAtLeast(1L)
        lastBytes = current
        lastTime = now
        dayTotal += delta
        prefs.edit().putLong(TOTAL_KEY, dayTotal).apply()
        val bytesPerSecond = delta * 1000L / elapsed
        textView.text = formatSpeed(bytesPerSecond) + "/" + formatTotal(dayTotal)
    }
}
