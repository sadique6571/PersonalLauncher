package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One-shot local status read. Never executes shell commands or collects hardware/account identifiers. */
final class LauncherDiagnostics {
    private LauncherDiagnostics() { }
    static void show(Activity host) {
        LinearLayout rows = HomeLibraryUi.rows(host);
        TextView text = GlassSettings.label(host, host.getString(R.string.h_di_loading), 14, 0xffeeeeee);
        text.setTextIsSelectable(true); rows.addView(text);
        rows.addView(GlassSettings.label(host, host.getString(R.string.h_di_note), 12, 0xffb5cadd));
        AlertDialog panel = GlassSettings.panel(host, host.getString(R.string.h_diagnostics), rows); panel.show();
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            StringBuilder status = new StringBuilder();
            try {
                ResolveInfo home = host.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
                status.append("Android HOME: ").append(home != null && home.activityInfo != null ? new ComponentName(home.activityInfo.packageName, home.activityInfo.name).flattenToShortString() : "unresolved");
                Config config = Config.load(host);
                status.append("\nStock Home redirect: ").append(config.stockHomeRedirect.isEmpty() ? "off" : config.stockHomeRedirect);
                status.append("\nHome service connected: ").append(HomeKeyService.isConnected());
                String services = Settings.Secure.getString(host.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                for (Class<?> cls : new Class<?>[]{HomeKeyService.class, InputKeyService.class}) {
                    ComponentName component = new ComponentName(host, cls);
                    boolean enabled = false;
                    if (services != null) for (String item : services.split(":")) if (component.equals(ComponentName.unflattenFromString(item))) enabled = true;
                    status.append("\n").append(cls.getSimpleName()).append(" enabled: ").append(enabled);
                }
                status.append("\nKey filter configured: ").append(config.keyFilter);
                status.append("\nPower-on Home: ").append(config.powerOnHomeAlways);
                status.append("\nAndroid uptime: ").append(SystemClock.elapsedRealtime() / 1000).append("s");
                String events = BootLog.snapshot();
                status.append("\n\nLauncherBoot (this process):\n").append(events.isEmpty() ? "No timing events retained" : events);
            } catch (RuntimeException unavailable) { status.append("\nSome status is unavailable on this firmware."); }
            host.runOnUiThread(() -> { if (!host.isDestroyed() && panel.isShowing()) text.setText(status.toString()); });
        }); worker.shutdown();
    }
}
