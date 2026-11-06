package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.widget.Toast;

/** One-time, explicit Android approval request when Home first becomes idle after installation. */
final class NotificationAccessSetup {
    static final String ASKED = "notification_access_prompted";
    private NotificationAccessSetup() { }
    static void offer(Activity host) {
        SharedPreferences prefs = ThemePreferences.get(host);
        if (host.isFinishing() || host.isDestroyed() || prefs.getBoolean(ASKED, false)) return;
        if (HomeNotificationsUi.approved(host)) { prefs.edit().putBoolean(ASKED, true).apply(); return; }
        prefs.edit().putBoolean(ASKED, true).apply();
        AlertDialog dialog = new ThemeDialogBuilder(host).setTitle("Show notifications on Home?")
                .setMessage("Allow notification access in Android settings to show alerts in the bell. You can skip this; the launcher still works.")
                .setPositiveButton("Open Android settings", (d, which) -> {
                    try { host.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                    catch (RuntimeException unavailable) { Toast.makeText(host, R.string.h_unavailable, Toast.LENGTH_LONG).show(); }
                }).setNegativeButton("Not now", null).create();
        dialog.show();
        GlassSettings.styleDialog(host, dialog);
    }
}
