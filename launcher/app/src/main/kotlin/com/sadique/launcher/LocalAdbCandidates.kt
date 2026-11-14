package com.sadique.launcher

import java.io.IOException
import java.util.concurrent.TimeUnit

/** Collects already discovered local endpoints during a short quiet period. */
class LocalAdbCandidates {
    private val ports = LinkedHashSet<Int>()
    private val lock = java.lang.Object()
    private var changed = 0L
    private var finished = false

    fun add(port: Int) = synchronized(lock) {
        if (port in 1..65535 && ports.size < 16 && ports.add(port)) {
            changed = System.nanoTime()
            lock.notifyAll()
        }
    }

    fun finish() = synchronized(lock) {
        finished = true
        lock.notifyAll()
    }

    @Throws(IOException::class, InterruptedException::class)
    fun await(timeoutMs: Long, quietMs: Long): List<Int> = synchronized(lock) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        val quiet = TimeUnit.MILLISECONDS.toNanos(quietMs)
        while (!finished) {
            val now = System.nanoTime()
            var remaining = deadline - now
            if (ports.isNotEmpty()) remaining = minOf(remaining, changed + quiet - now)
            if (remaining <= 0) break
            TimeUnit.NANOSECONDS.timedWait(lock, remaining)
        }
        if (ports.isEmpty()) throw IOException("No local wireless-debugging endpoint")
        ArrayList(ports)
    }
}
