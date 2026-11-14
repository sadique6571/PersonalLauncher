package com.sadique.launcher

/** At most two automatic foreground repairs per process, with a one-minute gap. */
class HomeRepairPolicy {
    private var attempts = 0
    private var last = -1L

    @Synchronized
    fun allow(now: Long, alreadyHome: Boolean): Boolean {
        if (alreadyHome || attempts >= 2 || (last >= 0 && now >= last && now - last < 60_000)) return false
        attempts++
        last = now
        return true
    }
}
