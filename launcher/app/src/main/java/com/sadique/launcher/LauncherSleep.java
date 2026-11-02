package com.sadique.launcher;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.Toast;

/** An inexact elapsed-time alarm: no polling, foreground service or exact-alarm permission. */
public final class LauncherSleep extends BroadcastReceiver {
    private static PendingIntent alarm(Context context) {
        return PendingIntent.getBroadcast(context, 741, new Intent(context, LauncherSleep.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static long remaining(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("launcher_sleep", 0);
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (boot == -1 || prefs.getInt("boot", -2) != boot) return 0;
        return Math.max(0, prefs.getLong("deadline", 0) - SystemClock.elapsedRealtime());
    }
    static void show(Activity host) {
        LinearLayout rows = HomeLibraryUi.rows(host);
        AlertDialog panel = GlassSettings.panel(host, host.getString(R.string.h_sleep_timer), rows);
        long remain = remaining(host);
        rows.addView(GlassSettings.label(host, remain == 0 ? host.getString(R.string.h_sleep_none)
                : host.getString(R.string.h_sleep_active, (remain + 59999) / 60000), 16, 0xffeeeeee));
        rows.addView(GlassSettings.label(host, host.getString(R.string.h_sleep_note), 12, 0xffb5cadd));
        for (int minutes : new int[]{15, 30, 60, 90, 120}) {
            rows.addView(GlassSettings.row(host, host.getString(R.string.h_minutes, minutes), null, R.drawable.ic_media_pause,
                    GlassSettings.themeAccent(host), () -> {
                        if (!HomeKeyService.isConnected()) { Toast.makeText(host, R.string.h_sleep_need_service, Toast.LENGTH_LONG).show(); return; }
                        AlarmManager manager = (AlarmManager) host.getSystemService(Context.ALARM_SERVICE);
                        if (manager == null) return;
                        long deadline = SystemClock.elapsedRealtime() + minutes * 60000L;
                        manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarm(host));
                        host.getSharedPreferences("launcher_sleep", 0).edit().putLong("deadline", deadline)
                                .putInt("boot", Settings.Global.getInt(host.getContentResolver(), Settings.Global.BOOT_COUNT, -1)).apply();
                        panel.dismiss(); show(host);
                    }));
        }
        rows.addView(GlassSettings.row(host, host.getString(R.string.h_sleep_cancel), null, R.drawable.ic_settings,
                GlassSettings.themeAccent(host), () -> { cancel(host); panel.dismiss(); }));
        panel.show();
    }
    static void cancel(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(alarm(context));
        context.getSharedPreferences("launcher_sleep", 0).edit().clear().apply();
    }
    @Override public void onReceive(Context context, Intent intent) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("launcher_sleep", 0);
        if (prefs.getLong("deadline", 0) == 0 || remaining(context) > 0) return;
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (boot == -1 || prefs.getInt("boot", -2) != boot) { cancel(context); return; }
        cancel(context);
        if (!HomeKeyService.requestSleep()) Toast.makeText(context, R.string.h_sleep_failed, Toast.LENGTH_LONG).show();
    }
}
