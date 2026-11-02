package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.Collections;
import java.util.List;

/** The bell opens alerts with an always-available notification access shortcut. */
final class HomeNotificationsUi {
    private HomeNotificationsUi() { }
    static boolean approved(Activity host) {
        String enabled = Settings.Secure.getString(host.getContentResolver(), "enabled_notification_listeners");
        ComponentName listener = new ComponentName(host, HomeNotificationService.class);
        if (enabled != null) for (String component : enabled.split(":"))
            if (listener.equals(ComponentName.unflattenFromString(component))) return true;
        return false;
    }
    static AlertDialog show(Activity host) {
        LinearLayout rows = HomeLibraryUi.rows(host);
        AlertDialog panel = GlassSettings.panel(host, host.getString(R.string.h_notifications), rows);
        boolean[] initial = {true};
        Runnable render = () -> {
            if (host.isDestroyed() || !panel.isShowing()) return;
            View oldFocus = rows.findFocus(); Object focusedKey = oldFocus == null ? null : oldFocus.getTag();
            rows.removeAllViews();
            View access = GlassSettings.row(host, host.getString(R.string.h_access_title),
                    host.getString(R.string.h_open_access), R.drawable.ic_settings,
                    GlassSettings.themeAccent(host), () -> {
                        try { host.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); panel.dismiss(); }
                        catch (RuntimeException unavailable) { Toast.makeText(host, R.string.h_unavailable, Toast.LENGTH_LONG).show(); }
                    });
            access.setTag("notification_access"); rows.addView(access);
            if (!approved(host)) rows.addView(GlassSettings.label(host,
                    host.getString(R.string.h_notification_access_hint), 13, 0xffb5cadd));
            List<HomeNotificationService.Entry> entries = HomeNotificationService.snapshot(); Collections.reverse(entries);
            if (entries.isEmpty()) rows.addView(GlassSettings.label(host, host.getString(R.string.h_notifications_empty), 14, 0xffb5cadd));
            View restore = "notification_access".equals(focusedKey) ? access : null, first = access;
            for (HomeNotificationService.Entry entry : entries) {
                View row = GlassSettings.row(host, entry.title, entry.text, R.drawable.ic_notifications,
                        GlassSettings.themeAccent(host), () -> {
                            if (entry.open == null) return;
                            try { entry.open.send(); panel.dismiss(); }
                            catch (android.app.PendingIntent.CanceledException unavailable) { Toast.makeText(host, R.string.h_unavailable, Toast.LENGTH_SHORT).show(); }
                        });
                row.setTag(entry.key); rows.addView(row);
                if (first == null) first = row;
                if (entry.key.equals(focusedKey)) restore = row;
            }
            // Preserve focus on updates; do not steal it from the header settings button.
            if (initial[0] && first != null) first.requestFocus();
            else if (oldFocus != null) { if (restore != null) restore.requestFocus(); else if (first != null) first.requestFocus(); }
            initial[0] = false;
        };
        HomeNotificationService.observe(render);
        panel.setOnDismissListener(d -> HomeNotificationService.unobserve(render));
        panel.show(); render.run(); return panel;
    }
}
