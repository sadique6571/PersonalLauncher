package com.sadique.launcher

/** Reserve 100 percent for a verified APK. */
object UpdateProgress {
    @JvmStatic
    fun percent(bytes: Long, total: Long, verified: Boolean): Int {
        if (verified) return 100
        if (total <= 0 || bytes <= 0) return 0
        return minOf(99L, minOf(bytes, total) * 100 / total).toInt()
    }
}
