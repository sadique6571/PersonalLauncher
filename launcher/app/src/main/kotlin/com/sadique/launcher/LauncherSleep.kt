package com.sadique.launcher

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast

/** Schedules a one-shot, inexact elapsed-time alarm. */
class LauncherSleep : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = preferences(context)
        if (!due(prefs.getLong("deadline", 0), SystemClock.elapsedRealtime(), prefs.getInt("boot", -2), boot(context))) return
        cancel(context)
        if (!HomeKeyService.requestSleep()) {
            Toast.makeText(context, R.string.h_sleep_failed, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private fun preferences(context: Context) = context.getSharedPreferences("launcher_sleep", Context.MODE_PRIVATE)

        private fun boot(context: Context): Int {
            return Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        }

        private fun alarm(context: Context): PendingIntent {
            return PendingIntent.getBroadcast(context, 741, Intent(context, LauncherSleep::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        @JvmStatic
        fun due(deadline: Long, now: Long, savedBoot: Int, currentBoot: Int): Boolean {
            return deadline > 0 && now >= deadline && currentBoot >= 0 && savedBoot == currentBoot
        }

        @JvmStatic
        fun remaining(context: Context): Long {
            val prefs = preferences(context)
            val boot = boot(context)
            if (boot == -1 || prefs.getInt("boot", -2) != boot) return 0
            return maxOf(0, prefs.getLong("deadline", 0) - SystemClock.elapsedRealtime())
        }

        @JvmStatic
        @JvmOverloads
        fun show(host: Activity, closeAfterSetting: Boolean = false) {
            val rows = HomeLibraryUi.rows(host)
            val panel = GlassSettings.panel(host, host.getString(R.string.h_sleep_timer), rows)
            val remain = remaining(host)
            val status = if (remain == 0L) host.getString(R.string.h_sleep_none)
                else host.getString(R.string.h_sleep_active, (remain + 59999) / 60000)
            rows.addView(GlassSettings.label(host, status, 16, 0xffeeeeee.toInt()))
            rows.addView(GlassSettings.label(host, host.getString(R.string.h_sleep_note), 12, 0xffb5cadd.toInt()))
            for (minutes in intArrayOf(15, 30, 60, 90, 120)) {
                rows.addView(GlassSettings.row(host, host.getString(R.string.h_minutes, minutes), null,
                    R.drawable.ic_media_pause, GlassSettings.themeAccent(host)) {
                    if (!HomeKeyService.isConnected()) {
                        Toast.makeText(host, R.string.h_sleep_need_service, Toast.LENGTH_LONG).show()
                        return@row
                    }
                    val manager = host.getSystemService(AlarmManager::class.java) ?: return@row
                    val deadline = SystemClock.elapsedRealtime() + minutes * 60000L
                    manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarm(host))
                    preferences(host).edit().putLong("deadline", deadline).putInt("boot", boot(host)).apply()
                    panel.dismiss()
                    if (!closeAfterSetting) show(host)
                })
            }
            rows.addView(GlassSettings.row(host, host.getString(R.string.h_sleep_cancel), null,
                R.drawable.ic_settings, GlassSettings.themeAccent(host)) {
                cancel(host)
                panel.dismiss()
            })
            panel.show()
        }

        @JvmStatic
        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(alarm(context))
            preferences(context).edit().clear().apply()
        }
    }
}
