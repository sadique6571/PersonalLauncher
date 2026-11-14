package com.sadique.launcher

import android.os.Process
import android.os.SystemClock
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

/** Invoked only on the telemetry worker. Discover bounded idle-counter paths once per visible session. */
internal class AndroidCpuSource : CpuUsageSampler.Source {
    private class Core(val online: File) {
        val counters = mutableListOf<File>()
    }

    private var paths: MutableList<Core>? = null

    @Throws(IOException::class)
    override fun procStat(): String {
        return line(File("/proc/stat"))
    }

    @Throws(IOException::class)
    override fun idleMicros(): LongArray {
        var currentPaths = paths
        if (currentPaths == null) {
            currentPaths = mutableListOf()
            for (cpu in 0 until 32) {
                val directory = File("/sys/devices/system/cpu/cpu$cpu/cpuidle")
                val states = directory.listFiles { f -> f.isDirectory && f.name.matches(Regex("state[0-9]+")) }
                if (states.isNullOrEmpty()) continue
                val parentFile = directory.parentFile ?: continue
                val core = Core(File(parentFile, "online"))
                for (state in 0 until Math.min(16, states.size)) {
                    val counter = File(states[state], "time")
                    line(counter)
                    core.counters.add(counter)
                }
                currentPaths.add(core)
            }
            if (currentPaths.isEmpty()) {
                throw IOException("Idle counters restricted")
            }
            paths = currentPaths
        }

        var idle: Long = 0
        var online = 0
        try {
            for (core in currentPaths) {
                if (core.online.exists() && "0" == line(core.online).trim()) continue
                for (path in core.counters) {
                    idle += line(path).trim().toLong()
                }
                online++
            }
        } catch (invalid: NumberFormatException) {
            throw IOException("Invalid idle counters", invalid)
        }

        if (online < processors()) {
            throw IOException("Partial CPU counters")
        }
        return longArrayOf(idle, online.toLong())
    }

    override fun elapsedMillis(): Long {
        return SystemClock.elapsedRealtime()
    }

    override fun processCpuMillis(): Long {
        return Process.getElapsedCpuTime()
    }

    override fun processors(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    companion object {
        @Throws(IOException::class)
        private fun line(file: File): String {
            BufferedReader(FileReader(file)).use { reader ->
                val value = reader.readLine()
                if (value == null) {
                    throw IOException("Empty counter")
                }
                return value
            }
        }
    }
}
