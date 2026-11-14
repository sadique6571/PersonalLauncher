package com.sadique.launcher

object HomeWeatherPolicy {
    @JvmStatic
    fun canAttempt(network: Boolean, internet: Boolean, validated: Boolean): Boolean {
        return network && (internet || validated)
    }

    @JvmStatic
    fun fresh(value: String?, storedKey: String?, requestedKey: String, fetchedAt: Long, now: Long, ttl: Long): Boolean {
        return value != null && value.trim { it <= ' ' }.isNotEmpty() && requestedKey == storedKey &&
            fetchedAt > 0 && now >= fetchedAt && now - fetchedAt < ttl
    }
}
