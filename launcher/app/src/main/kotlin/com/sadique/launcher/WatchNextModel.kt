package com.sadique.launcher

object WatchNextModel {
    @JvmStatic
    fun resumable(type: Int, position: Long, duration: Long): Boolean {
        return type == 0 && (duration <= 0 || position < duration)
    }

    @JvmStatic
    fun progress(position: Long, duration: Long): Int {
        if (duration <= 0) return 0
        return Math.round(1000.0 * position.coerceIn(0, duration) / duration).toInt()
    }
}
