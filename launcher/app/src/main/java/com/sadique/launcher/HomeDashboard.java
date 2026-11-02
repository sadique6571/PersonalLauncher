package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;

/** A small dashboard built with native views. The Settings remote action still opens this same popup. */
final class HomeDashboard {
    static final int SETTINGS=0, SEARCH=1, STORE=2, SCREENSAVER=3, PROFILES=4, CATEGORIES=5,
            WATCHLIST=6, WIFI=7, BLUETOOTH=8, INPUTS=9, SYSTEM=10;
    interface Actions { void open(int action); }
    private HomeDashboard() { }
    static AlertDialog show(Activity host, String network, Runnable rebuild, Actions actions) {
        LinearLayout rows = HomeLibraryUi.rows(host);
        AlertDialog panel = GlassSettings.panel(host, host.getString(R.string.h_dashboard), rows);
        String name = host.getSharedPreferences("personal_home", 0).getString("name", "Personal");
        rows.addView(GlassSettings.label(host, name, 24, 0xfff0f7ff));
        rows.addView(GlassSettings.label(host, network, 12, 0xffb5cadd));
        LinearLayout quick = new LinearLayout(host);
        quick(host, quick, R.string.h_search, R.drawable.ic_search, () -> { panel.dismiss(); actions.open(SEARCH); });
        quick(host, quick, R.string.h_store, R.drawable.ic_apps, () -> { panel.dismiss(); actions.open(STORE); });
        quick(host, quick, R.string.h_screensaver, R.drawable.ic_picture, () -> { panel.dismiss(); actions.open(SCREENSAVER); });
        rows.addView(quick);
        section(host, rows, R.string.h_personalize);
        row(host, rows, R.string.h_launcher_settings, R.string.h_layout_hint, R.drawable.ic_palette, () -> actions.open(SETTINGS));
        row(host, rows, R.string.h_profiles, R.string.h_profiles_hint, R.drawable.ic_person, () -> { panel.dismiss(); actions.open(PROFILES); });
        row(host, rows, R.string.h_categories, R.string.h_category_hint, R.drawable.ic_categories, () -> { panel.dismiss(); actions.open(CATEGORIES); });
        row(host, rows, R.string.h_watchlist, R.string.h_watchlist_hint, R.drawable.ic_watchlist, () -> { panel.dismiss(); actions.open(WATCHLIST); });
        section(host, rows, R.string.h_playback);
        row(host, rows, R.string.h_now_playing, R.string.h_media_hint, R.drawable.ic_media_play, () -> { panel.dismiss(); HomeMediaUi.nowPlaying(host); });
        row(host, rows, R.string.h_notifications, R.string.h_optional_access, R.drawable.ic_notifications, () -> { panel.dismiss(); HomeMediaUi.notifications(host); });
        row(host, rows, R.string.h_live_guide, R.string.h_guide_hint, R.drawable.ic_live_guide, () -> { panel.dismiss(); HomeLiveGuide.show(host); });
        section(host, rows, R.string.h_device);
        row(host, rows, R.string.h_remote_setup, R.string.h_remote_setup_hint, R.drawable.ic_settings, () -> { panel.dismiss(); RemoteSetup.show(host); });
        boolean appsOnly = host.getSharedPreferences("personal_home", 0).getBoolean("apps_only", false);
        rows.addView(GlassSettings.row(host, host.getString(R.string.h_apps_only), host.getString(appsOnly ? R.string.h_on : R.string.h_off), R.drawable.ic_apps, GlassSettings.themeAccent(host), () -> {
            host.getSharedPreferences("personal_home", 0).edit().putBoolean("apps_only", !appsOnly).apply();
            panel.dismiss(); rebuild.run();
        }));
        row(host, rows, R.string.h_diagnostics, R.string.h_diagnostics_hint, R.drawable.ic_settings, () -> { panel.dismiss(); LauncherDiagnostics.show(host); });
        row(host, rows, R.string.h_local_pin, R.string.h_local_pin_hint, R.drawable.ic_person, () -> { panel.dismiss(); LauncherPin.settings(host); });
        row(host, rows, R.string.h_sleep_timer, R.string.h_sleep_timer_hint, R.drawable.ic_media_pause, () -> { panel.dismiss(); LauncherSleep.show(host); });
        Runnable release = QuickControls.attach(host, rows);
        row(host, rows, R.string.h_wifi, R.string.h_network_hint, R.drawable.ic_wifi, () -> { panel.dismiss(); actions.open(WIFI); });
        row(host, rows, R.string.h_bluetooth, R.string.h_accessories_hint, android.R.drawable.stat_sys_data_bluetooth, () -> { panel.dismiss(); actions.open(BLUETOOTH); });
        row(host, rows, R.string.h_inputs, R.string.h_inputs_hint, R.drawable.ic_input, () -> { panel.dismiss(); actions.open(INPUTS); });
        row(host, rows, R.string.h_system, R.string.h_system_hint, R.drawable.ic_settings, () -> { panel.dismiss(); actions.open(SYSTEM); });
        panel.setOnDismissListener(d -> release.run()); panel.show(); return panel;
    }
    private static void quick(Activity host, LinearLayout strip, int title, int icon, Runnable action) {
        View card = GlassSettings.wideCard(host, host.getString(title), icon, GlassSettings.themeAccent(host), action);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        int margin = GlassSettings.dp(host, 3); p.setMargins(margin, margin, margin, margin); strip.addView(card, p);
    }
    private static void section(Activity host, LinearLayout rows, int title) {
        android.widget.TextView label = GlassSettings.label(host, host.getString(title), 12, GlassSettings.themeAccent(host));
        label.setPadding(4, GlassSettings.dp(host, 14), 0, GlassSettings.dp(host, 6)); rows.addView(label);
    }
    private static void row(Activity host, LinearLayout rows, int title, int hint, int icon, Runnable action) {
        rows.addView(GlassSettings.row(host, host.getString(title), host.getString(hint), icon, GlassSettings.themeAccent(host), action));
    }
}
