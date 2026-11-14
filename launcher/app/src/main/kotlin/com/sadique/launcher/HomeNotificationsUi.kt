package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ComponentName
import android.provider.Settings
import android.view.View
import android.widget.Toast

/** Displays alerts. First-run setup owns the notification approval prompt. */
object HomeNotificationsUi {
    @JvmStatic
    fun approved(host: Activity): Boolean {
        val enabled = Settings.Secure.getString(host.contentResolver, "enabled_notification_listeners")
        val listener = ComponentName(host, HomeNotificationService::class.java)
        return enabled?.split(':')?.any { listener == ComponentName.unflattenFromString(it) } == true
    }

    @JvmStatic
    fun show(host: Activity): AlertDialog {
        val rows = HomeLibraryUi.rows(host)
        val panel = GlassSettings.panel(host, host.getString(R.string.h_notifications), rows)
        var initial = true
        val render = Runnable {
            if (host.isDestroyed || !panel.isShowing) return@Runnable
            val oldFocus = rows.findFocus()
            val focusedKey = oldFocus?.tag
            rows.removeAllViews()
            if (!approved(host)) {
                rows.addView(GlassSettings.label(host, "Notification access is off.", 13, 0xffb5cadd.toInt()))
            }
            val entries = HomeNotificationService.snapshot().asReversed()
            if (entries.isEmpty()) {
                rows.addView(GlassSettings.label(host, host.getString(R.string.h_notifications_empty), 14, 0xffb5cadd.toInt()))
            }
            var restore: View? = null
            var first: View? = null
            for (entry in entries) {
                val row = GlassSettings.row(host, entry.title, entry.text, R.drawable.ic_notifications,
                    GlassSettings.themeAccent(host)) {
                    val open = entry.open ?: return@row
                    try {
                        open.send()
                        panel.dismiss()
                    } catch (_: PendingIntent.CanceledException) {
                        Toast.makeText(host, R.string.h_unavailable, Toast.LENGTH_SHORT).show()
                    }
                }
                row.tag = entry.key
                rows.addView(row)
                if (first == null) first = row
                if (entry.key == focusedKey) restore = row
            }
            if (initial) first?.requestFocus()
            else if (oldFocus != null) (restore ?: first)?.requestFocus()
            initial = false
        }
        HomeNotificationService.observe(render)
        panel.setOnDismissListener { HomeNotificationService.unobserve(render) }
        panel.show()
        render.run()
        return panel
    }
}
