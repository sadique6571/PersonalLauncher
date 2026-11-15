package com.sadique.launcher

import android.content.Context
import android.content.res.Resources
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

internal class LauncherStats(
    private val context: Context,
    private val handler: Handler,
    private val view: () -> TextView?,
    private val enabled: () -> Boolean,
) {
    /** Background reader. Started only while Home is resumed and show_stats is on. */
    private var statsThread: HandlerThread? = null
    /** The current run's handler; a run whose handler is no longer this one stops (volatile: read off-thread). */
    @Volatile
    private var statsBg: Handler? = null

    fun start() {
        if (statsThread != null) return
        view()?.visibility = View.VISIBLE
        cpuTotalPrev = -1
        idlePrev = -1
        procStatDead = false
        val thread = HandlerThread("stats")
        statsThread = thread
        thread.start()
        // Each start gets its own handler, held by its task: stopStats on the main thread can clear the field
        // at any moment without the reader tripping over it.
        val bg = Handler(thread.looper)
        statsBg = bg
        bg.post(object : Runnable {
            override fun run() {
                if (statsBg != bg) return
                val line = readStatsLine()
                handler.post {
                    val s = view()
                    if (enabled() && s != null && statsBg == bg) s.text = line
                }
                if (statsBg == bg) bg.postDelayed(this, STATS_MS)
            }
        })
    }

    fun stop() {
        if (statsThread == null) return
        statsBg!!.removeCallbacksAndMessages(null)
        statsThread!!.quit()
        statsThread = null
        statsBg = null
        // Paused (the quick panel or an app on top): keep the last line on screen. Taking it away moved the grid up
        // under a glass highlight that stayed put.
        if (!enabled()) view()?.visibility = View.GONE
    }

    private fun readStatsLine(): String {
        var cpu = cpuPercent()
        if (cpu < 0) {
            try {
                Thread.sleep(200)             // second sample, so the first line already has a CPU figure
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return ""
            }
            cpu = cpuPercent()
        }
        // Android 8 and later hide /proc/stat from apps on many TVs: then the line leaves CPU out rather than
        // showing a dash that never fills in.
        val line = StringBuilder()
        if (cpu >= 0) line.append(context.getString(R.string.stats_cpu, cpu))
        val mem = memUsedOfTotalMb()
        if (mem != null) appendStat(line, context.getString(R.string.stats_memory, mem[0], mem[1]))
        val free = formatGb(context.resources, Environment.getDataDirectory().freeSpace)
        if (free.isNotEmpty()) appendStat(line, context.getString(R.string.stats_storage, free))
        return StatsText.forLocale(line.toString(), context.resources.configuration.locales[0])
    }

    private var cpuTotalPrev = -1L
    private var cpuIdlePrev = -1L
    private var procStatDead = false
    private var idlePrev = -1L
    private var wallPrev = -1L

    private fun cpuPercent(): Int {
        if (!procStatDead) {
            val fromStat = cpuFromProcStat()
            if (fromStat != CPU_UNREADABLE) return fromStat
            procStatDead = true
            idlePrev = -1
        }
        return cpuFromIdle()
    }

    private fun cpuFromProcStat(): Int {
        val s = readFirstLine("/proc/stat")
        if (s == null || !s.startsWith("cpu")) return CPU_UNREADABLE
        val t = s.trim().split("\\s+".toRegex())
        if (t.size < 5) return CPU_UNREADABLE
        try {
            val user = java.lang.Long.parseLong(t[1])
            val nice = java.lang.Long.parseLong(t[2])
            val sys = java.lang.Long.parseLong(t[3])
            val idle = java.lang.Long.parseLong(t[4])
            val iowait = if (t.size > 5) java.lang.Long.parseLong(t[5]) else 0
            val irq = if (t.size > 6) java.lang.Long.parseLong(t[6]) else 0
            val soft = if (t.size > 7) java.lang.Long.parseLong(t[7]) else 0
            val total = user + nice + sys + idle + iowait + irq + soft
            val idleAll = idle + iowait
            if (cpuTotalPrev < 0) {
                cpuTotalPrev = total
                cpuIdlePrev = idleAll
                return -1
            }
            val dt = total - cpuTotalPrev
            val di = idleAll - cpuIdlePrev
            cpuTotalPrev = total
            cpuIdlePrev = idleAll
            if (dt <= 0) return -1
            val busy = Math.round((dt - di) * 100.0 / dt).toInt()
            return Math.max(0, Math.min(100, busy))
        } catch (_: NumberFormatException) {
            return CPU_UNREADABLE
        }
    }

    /** Busy percent from cpuidle time (microseconds) across the cores. -1 until the second sample. */
    private fun cpuFromIdle(): Int {
        var idle = 0L
        var cores = 0
        for (i in 0 until 8) {
            var any = false
            for (state in 0 until 8) {
                val t = readFirstLine("/sys/devices/system/cpu/cpu" + i
                        + "/cpuidle/state" + state + "/time")
                if (t == null) break
                try {
                    idle += java.lang.Long.parseLong(t.trim())
                    any = true
                } catch (_: NumberFormatException) {
                    break
                }
            }
            if (!any) break
            cores++
        }
        if (cores == 0) return -1
        val wall = SystemClock.elapsedRealtime() * 1000L
        if (idlePrev < 0) {
            idlePrev = idle
            wallPrev = wall
            return -1
        }
        val di = idle - idlePrev
        val dw = (wall - wallPrev) * cores
        idlePrev = idle
        wallPrev = wall
        if (dw <= 0) return -1
        val busy = Math.round((dw - di) * 100.0 / dw).toInt()
        return Math.max(0, Math.min(100, busy))
    }

    /** RAM usage as {used, total} in MB, used = MemTotal - MemAvailable, e.g. "945 / 1536 MB". Used over
     *  free so the line reads as a load against a known whole; null when /proc/meminfo cannot be read. */
    private fun memUsedOfTotalMb(): LongArray? {
        var available = -1L
        var total = -1L
        try {
            BufferedReader(FileReader("/proc/meminfo")).use { r ->
                var line = r.readLine()
                while (line != null) {
                    if (line.startsWith("MemAvailable:")) {
                        val t = line.split("\\s+".toRegex())
                        if (t.size >= 2) available = java.lang.Long.parseLong(t[1]) / 1024
                    } else if (line.startsWith("MemTotal:")) {
                        val t = line.split("\\s+".toRegex())
                        if (t.size >= 2) total = java.lang.Long.parseLong(t[1]) / 1024
                    }
                    line = r.readLine()
                }
            }
        } catch (_: IOException) {
        } catch (_: NumberFormatException) {
        }
        if (available < 0 || total <= 0) return null
        return longArrayOf(Math.max(0, total - available), total)
    }

    private companion object {
        private const val STATS_MS = 10_000L
        private const val CPU_UNREADABLE = -2

        /** One quiet line, e.g. "CPU 8%  ·  Memory 430 MB free  ·  Storage 3.1 GB free". Runs off the main thread. */
        private fun appendStat(line: StringBuilder, part: String) {
            if (line.isNotEmpty()) line.append("  ·  ")
            line.append(part)
        }

        private fun readFirstLine(path: String): String? {
            try {
                BufferedReader(FileReader(path)).use { r -> return r.readLine() }
            } catch (_: IOException) {
                return null
            }
        }

        private fun formatGb(res: Resources, bytes: Long): String {
            if (bytes <= 0) return ""
            val gb = bytes / (1024.0 * 1024 * 1024)
            return if (gb >= 10) res.getString(R.string.storage_gb_whole, gb)
            else res.getString(R.string.storage_gb_decimal, gb)
        }
    }
}
