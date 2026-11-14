package com.sadique.launcher

import java.util.ArrayList
import java.util.LinkedHashSet

/** Keeps saved positions across restarts; new apps append and removed packages disappear. */
object AppOrder {
    @JvmStatic
    fun merge(installed: List<String>, saved: List<String>): List<String> {
        val remaining = LinkedHashSet(installed)
        val result = LinkedHashSet<String>()
        for (pkg in saved) {
            if (remaining.remove(pkg)) result.add(pkg)
        }
        result.addAll(remaining)
        return ArrayList(result)
    }

    @JvmStatic
    fun move(original: List<String>, pkg: String, destination: Int): List<String> {
        val result = ArrayList(original)
        if (!result.remove(pkg)) return result
        result.add(Math.max(0, Math.min(result.size, destination)), pkg)
        return result
    }
}
