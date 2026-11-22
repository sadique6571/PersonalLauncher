package com.sadique.launcher

import android.os.SystemClock
import java.io.IOException
import java.net.URL
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/** Verify the calendar day separately from playback and device wall-clock settings. */
internal class VerifiedDayClock {
    private var epoch = 0L
    private var anchor = 0L
    private var retryAt = 0L
    private var checking = false
    private val zone = TimeZone.getTimeZone("Asia/Karachi")

    @Synchronized
    fun now(): Long? {
        val elapsed = SystemClock.elapsedRealtime()
        val estimate = epoch + elapsed - anchor
        if (epoch > 0 && day(epoch) == day(estimate)) return estimate
        if (!checking && elapsed >= retryAt) {
            checking = true
            Thread({
                val remote = try {
                    fetchDate()
                } catch (_: IOException) {
                    0L
                }
                synchronized(this) {
                    anchor = SystemClock.elapsedRealtime()
                    epoch = remote
                    retryAt = anchor + 30_000L
                    checking = false
                }
            }, "usage-date").start()
        }
        return null
    }

    private fun day(time: Long) = TimeUnit.MILLISECONDS.toDays(time + zone.getOffset(time))

    private fun fetchDate(): Long {
        val connection = URL("https://www.google.com/generate_204").openConnection() as HttpsURLConnection
        return try {
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            if (connection.responseCode == 204) connection.date else 0L
        } finally {
            connection.disconnect()
        }
    }
}
