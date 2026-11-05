package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;

/** TV-style quick-setting buttons followed by launcher preferences and secondary tools. */
final class HomeDashboard {
    static final int SETTINGS=0, SEARCH=1, STORE=2, SCREENSAVER=3, CATEGORIES=5,
            WIFI=7, BLUETOOTH=8, INPUTS=9, SYSTEM=10, DISPLAY=11, SOUND=12, IDENTITY=13;
    interface Actions { void open(int action); }
    private HomeDashboard() { }
    static AlertDialog show(Activity host, String network, Runnable rebuild, Actions actions) {
        LinearLayout rows = HomeLibraryUi.rows(host);
        AlertDialog panel = GlassSettings.panel(host, TvGlassStyle.enabled(host.getSharedPreferences("personal_home", 0)) ? "Control Center" : host.getString(R.string.h_dashboard), rows, true);
        rows.addView(GlassSettings.label(host, network, 12, 0xffb5cadd));
        section(host, rows, R.string.h_quick_settings);
        pair(host, rows, panel, actions, R.string.h_wifi, R.drawable.ic_wifi, WIFI,
                R.string.h_bluetooth, android.R.drawable.stat_sys_data_bluetooth, BLUETOOTH);
        pair(host, rows, panel, actions, R.string.h_inputs, R.drawable.ic_input, INPUTS,
                R.string.h_display, R.drawable.ic_picture, DISPLAY);
        pair(host, rows, panel, actions, R.string.h_sound, R.drawable.ic_volume, SOUND,
                R.string.h_screensaver, R.drawable.ic_picture, SCREENSAVER);
        section(host, rows, R.string.h_personalize);
        row(host, rows, R.string.h_launcher_settings, R.string.h_layout_hint, R.drawable.ic_palette, () -> actions.open(SETTINGS));
        row(host, rows, R.string.h_single_identity, 0, R.drawable.ic_person, () -> { panel.dismiss(); actions.open(IDENTITY); });
        boolean appsOnly = host.getSharedPreferences("personal_home", 0).getBoolean("apps_only", false);
        row(host, rows, R.string.h_apps_only, appsOnly ? R.string.h_on : R.string.h_off, R.drawable.ic_apps, () -> {
            host.getSharedPreferences("personal_home", 0).edit().putBoolean("apps_only", !appsOnly).apply();
            panel.dismiss(); rebuild.run();
        });
        row(host, rows, R.string.h_local_pin, R.string.h_local_pin_hint, R.drawable.ic_person, () -> { panel.dismiss(); LauncherPin.settings(host); });
        row(host, rows, R.string.h_sleep_timer, R.string.h_sleep_timer_hint, R.drawable.ic_media_pause, () -> { panel.dismiss(); LauncherSleep.show(host); });
        section(host, rows, R.string.h_more_tools);
        pair(host, rows, panel, actions, R.string.h_search, R.drawable.ic_search, SEARCH,
                R.string.h_store, R.drawable.ic_apps, STORE);
        panel.show(); return panel;
    }
    private static void pair(Activity host, LinearLayout rows, AlertDialog panel, Actions actions,
            int first, int firstIcon, int firstAction, int second, int secondIcon, int secondAction) {
        LinearLayout strip = new LinearLayout(host);
        card(host, strip, first, firstIcon, () -> { panel.dismiss(); actions.open(firstAction); });
        card(host, strip, second, secondIcon, () -> { panel.dismiss(); actions.open(secondAction); });
        rows.addView(strip);
    }
    private static void card(Activity host, LinearLayout strip, int title, int icon, Runnable action) {
        View card = GlassSettings.wideCard(host, host.getString(title), icon, GlassSettings.themeAccent(host), action);
        card.setMinimumHeight(GlassSettings.dp(host, 64));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        int margin = GlassSettings.dp(host, 3); p.setMargins(margin, margin, margin, margin); strip.addView(card, p);
    }
    private static void section(Activity host, LinearLayout rows, int title) {
        android.widget.TextView label = GlassSettings.label(host, host.getString(title), 12, GlassSettings.themeAccent(host));
        label.setPadding(4, GlassSettings.dp(host, 14), 0, GlassSettings.dp(host, 6)); rows.addView(label);
    }
    private static void row(Activity host, LinearLayout rows, int title, int hint, int icon, Runnable action) {
        rows.addView(GlassSettings.row(host, host.getString(title), hint == 0 ? null : host.getString(hint), icon, GlassSettings.themeAccent(host), action));
    }
}
