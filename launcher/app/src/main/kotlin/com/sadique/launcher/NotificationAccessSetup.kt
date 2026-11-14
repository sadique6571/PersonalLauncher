package com.sadique.launcher

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/** Offers Android notification access when Home first becomes idle after installation. */
object NotificationAccessSetup {
    const val ASKED = "notification_access_prompted"

    @JvmStatic
    fun offer(host: Activity) {
        val prefs = ThemePreferences.get(host)
        if (host.isFinishing || host.isDestroyed || prefs.getBoolean(ASKED, false)) return
        val approved = HomeNotificationsUi.approved(host)
        prefs.edit().putBoolean(ASKED, true).apply()
        if (approved) return

        val dialog = ThemeDialogBuilder(host)
            .setTitle("Show notifications on Home?")
            .setMessage("Allow notification access in Android settings to show alerts in the bell. You can skip this; the launcher still works.")
            .setPositiveButton("Open Android settings") { _, _ ->
                try {
                    host.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: RuntimeException) {
                    Toast.makeText(host, R.string.h_unavailable, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Not now", null)
            .create()
        dialog.show()
        GlassSettings.styleDialog(host, dialog)
    }
}
