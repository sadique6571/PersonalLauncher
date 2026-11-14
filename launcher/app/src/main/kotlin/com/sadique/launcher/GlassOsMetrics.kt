package com.sadique.launcher

/** Measurement parsing is independent of views, sampling and platform availability. */
object GlassOsMetrics {
    @JvmStatic
    fun percent(used: Long, total: Long): Int {
        return if (total <= 0) 0 else Math.max(0, Math.min(100, Math.round(100.0 * used / total).toInt()))
    }

    @JvmStatic
    fun cpuTicks(line: String?): LongArray? {
        if (line == null) return null
        val f = line.trim().split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (f.size < 5 || "cpu" != f[0]) return null
        return try {
            var total = 0L
            for (i in 1 until Math.min(f.size, 9)) {
                val n = f[i].toLong()
                if (n < 0) return null
                total += n
            }
            val idle = f[4].toLong() + (if (f.size > 5) f[5].toLong() else 0L)
            longArrayOf(total, idle)
        } catch (_: NumberFormatException) {
            null
        }
    }
}
