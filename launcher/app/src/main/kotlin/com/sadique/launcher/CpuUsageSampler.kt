package com.sadique.launcher

import java.io.IOException

/** Prefer real system counters; a process-only fallback is explicitly identified as Launcher CPU. */
class CpuUsageSampler(private val source: Source) {

    interface Source {
        @Throws(IOException::class)
        fun procStat(): String
        @Throws(IOException::class)
        fun idleMicros(): LongArray? // {summed idle microseconds, sampled core count}
        fun elapsedMillis(): Long
        fun processCpuMillis(): Long
        fun processors(): Int
    }

    class Sample(@JvmField val percent: Double, @JvmField val system: Boolean) {
        fun ready(): Boolean = percent >= 0
    }

    private var procBlocked = false
    private var idleBlocked = false
    private var totalPrev = -1L
    private var idlePrev = 0L
    private var wallPrev = -1L
    private var processPrev = -1L
    private var coresPrev = 0

    fun sample(): Sample {
        if (!procBlocked) {
            try {
                val ticks = GlassOsMetrics.cpuTicks(source.procStat())
                    ?: throw IOException("No counters")
                val total = ticks[0]
                val idle = ticks[1]
                var value = -1.0
                if (totalPrev >= 0 && total > totalPrev && idle >= idlePrev && idle - idlePrev <= total - totalPrev) {
                    value = clamp(100.0 * (total - totalPrev - (idle - idlePrev)) / (total - totalPrev))
                }
                totalPrev = total
                idlePrev = idle
                return Sample(value, true)
            } catch (blocked: Exception) {
                if (blocked is IOException || blocked is SecurityException) {
                    procBlocked = true
                    totalPrev = -1
                    wallPrev = -1
                } else throw blocked
            }
        }
        if (!idleBlocked) {
            try {
                val idle = source.idleMicros()
                if (idle == null || idle.size < 2 || idle[0] < 0 || idle[1] <= 0) throw IOException("No idle counters")
                val wall = source.elapsedMillis()
                val cores = idle[1].toInt()
                var value = -1.0
                if (wallPrev >= 0 && wall > wallPrev && cores == coresPrev && idle[0] >= idlePrev) {
                    val capacity = (wall - wallPrev) * 1000.0 * cores
                    val elapsedIdle = (idle[0] - idlePrev).toDouble()
                    if (elapsedIdle <= capacity) value = clamp(100.0 * (capacity - elapsedIdle) / capacity)
                }
                idlePrev = idle[0]
                wallPrev = wall
                coresPrev = cores
                return Sample(value, true)
            } catch (blocked: Exception) {
                if (blocked is IOException || blocked is SecurityException) {
                    idleBlocked = true
                    wallPrev = -1
                    processPrev = -1
                } else throw blocked
            }
        }
        val wall = source.elapsedMillis()
        val cpu = source.processCpuMillis()
        val cores = Math.max(1, source.processors())
        var value = -1.0
        if (wallPrev >= 0 && wall > wallPrev && processPrev >= 0 && cpu >= processPrev && cores == coresPrev) {
            value = clamp(100.0 * (cpu - processPrev) / ((wall - wallPrev) * cores.toDouble()))
        }
        wallPrev = wall
        processPrev = cpu
        coresPrev = cores
        return Sample(value, false)
    }

    companion object {
        private fun clamp(value: Double): Double = Math.max(0.0, Math.min(100.0, value))
    }
}
