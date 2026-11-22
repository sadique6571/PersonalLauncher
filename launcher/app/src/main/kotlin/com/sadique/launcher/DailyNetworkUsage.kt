package com.sadique.launcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** All callers sample on the main thread. Each checkpoint is one preference transaction. */
internal class DailyNetworkUsage(context: Context, private val prefs: SharedPreferences) {
    private val boot = if (Build.VERSION.SDK_INT >= 24) {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } else -1

    fun sample(current: Long): Long {
        val wall = System.currentTimeMillis()
        val uptime = SystemClock.elapsedRealtime()
        val clock = Calendar.getInstance().apply { timeInMillis = wall }
        val today = TimeUnit.MILLISECONDS.toDays(wall + clock.timeZone.getOffset(wall))
        val previous = prefs.getLong("net_speed_day", -1L)
        val base = prefs.getLong("net_day_base", current)
        val bank = prefs.getLong("net_day_accum", 0L)
        val last = prefs.getLong("net_day_last", current)
        val saved = bank + (last - base).coerceAtLeast(0)
        // TVs may briefly boot with an old date before network time is available.
        if (today < previous || current < 0) return saved
        val lastBoot = prefs.getInt("net_day_boot", -1)
        val reboot = if (boot >= 0 && lastBoot >= 0) boot != lastBoot
            else uptime < prefs.getLong("net_day_uptime", 0L) || current < last
        val state = if (today != previous) {
            clock.set(Calendar.HOUR_OF_DAY, 0)
            clock.set(Calendar.MINUTE, 0)
            clock.set(Calendar.SECOND, 0)
            clock.set(Calendar.MILLISECOND, 0)
            val start = if (wall - uptime >= clock.timeInMillis) 0L else current
            State(start, 0L)
        } else roll(current, base, bank, last, reboot)
        if (today != previous || current != last || lastBoot != boot || reboot) {
            prefs.edit().putLong("net_speed_day", today).putLong("net_day_base", state.base)
                .putLong("net_day_accum", state.bank).putLong("net_day_last", current)
                .putInt("net_day_boot", boot).putLong("net_day_uptime", uptime).apply()
        }
        return state.bank + (current - state.base).coerceAtLeast(0)
    }

    data class State(val base: Long, val bank: Long)

    companion object {
        fun roll(current: Long, base: Long, bank: Long, last: Long, reboot: Boolean): State =
            if (reboot || current < last) State(0L, bank + (last - base).coerceAtLeast(0))
            else State(base, bank)
    }
}
