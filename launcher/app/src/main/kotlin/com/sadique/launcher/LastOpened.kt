package com.sadique.launcher

import android.content.Context
import android.text.format.DateUtils
import java.util.Locale

/** Tracks app launches initiated from Home for the cinematic shelf. */
object LastOpened {
    private const val PREFS = "last_opened"

    @JvmStatic
    fun record(context: Context, pkg: String?) {
        prefs(context).edit().putLong(pkg, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    fun get(context: Context, pkg: String?): Long {
        return if (pkg == null) 0L else prefs(context).getLong(pkg, 0L)
    }

    @JvmStatic
    fun describe(context: Context, timestamp: Long): String {
        val now = System.currentTimeMillis()
        val since = minOf(timestamp, now - DateUtils.MINUTE_IN_MILLIS)
        if (now - since < DateUtils.HOUR_IN_MILLIS) {
            return DateUtils.getRelativeTimeSpanString(since, now, DateUtils.MINUTE_IN_MILLIS).toString()
        }
        var day = DateUtils.getRelativeTimeSpanString(since, now, DateUtils.DAY_IN_MILLIS).toString()
        if (Locale.getDefault().language == "en" && (day.startsWith("Yesterday") || day.startsWith("Today"))) {
            day = day.replaceFirstChar { it.lowercaseChar() }
        }
        return day + ", " + DateUtils.formatDateTime(context, since, DateUtils.FORMAT_SHOW_TIME)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
