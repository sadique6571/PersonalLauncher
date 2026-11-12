package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;

/** TV-style quick-setting buttons followed by launcher preferences and secondary tools. */
final class HomeDashboard {
    static final int SETTINGS=0, SEARCH=1, STORE=2, SCREENSAVER=3, CATEGORIES=5,
            WIFI=7, BLUETOOTH=8, INPUTS=9, SYSTEM=10, DISPLAY=11, SOUND=12, IDENTITY=13;
    interface Actions {
        void open(int action);
        default boolean keepPanel(int action) { return false; }
    }
    private HomeDashboard() { }
    static AlertDialog show(Activity host, String network, Runnable rebuild, Actions actions) {
        return show(host,network,rebuild,actions,false);
    }
    static AlertDialog show(Activity host, String network, Runnable rebuild, Actions actions, boolean floating) {
        LinearLayout rows = HomeLibraryUi.rows(host);
        AlertDialog panel = GlassSettings.panel(host, TvGlassStyle.enabled(ThemePreferences.get(host)) ? "Control Center" : host.getString(R.string.h_dashboard), rows, true);
        android.content.SharedPreferences prefs=ThemePreferences.get(host);
        View systemGear = floating ? GlassSettings.row(host, host.getString(R.string.tv_settings),
                null, R.drawable.ic_settings, GlassSettings.themeAccent(host), () -> {
                    panel.dismiss();
                    actions.open(SYSTEM);
                }) : null;
        // Harmony keeps its own Control Center hierarchy instead of inheriting
        // the long Glass-OS dashboard with duplicated rows and headers.
        if(ThemeStyle.harmony(prefs)) {
            rows.setId(R.id.controlCenterPanel);
            android.widget.TextView heading=GlassSettings.label(host,"HARMONY CONTROL",18,0xfff5fbff);
            heading.setLetterSpacing(.08f);
            heading.setPadding(GlassSettings.dp(host,6),GlassSettings.dp(host,8),0,GlassSettings.dp(host,4));
            rows.addView(heading);
            if (systemGear != null) rows.addView(systemGear);
            rows.addView(GlassSettings.label(host,network,12,0xffa9ccdf));
            section(host,rows,R.string.h_quick_settings);
            rows.addView(new ControlCenterGrid(host,action->{
                if(!actions.keepPanel(action))panel.dismiss();
                actions.open(action);
            }));
            section(host,rows,R.string.h_personalize);
            row(host,rows,R.string.h_launcher_settings,R.string.h_layout_hint,R.drawable.ic_palette,
                    ()->{panel.dismiss();actions.open(SETTINGS);});
            if(!floating)row(host,rows,R.string.h_single_identity,0,R.drawable.ic_person,
                    ()->{panel.dismiss();actions.open(IDENTITY);});
            row(host,rows,R.string.h_sleep_timer,R.string.h_sleep_timer_hint,R.drawable.ic_media_pause,
                    ()->{if(!floating)panel.dismiss();LauncherSleep.show(host,floating);});
            row(host,rows,R.string.h_local_pin,R.string.h_local_pin_hint,R.drawable.ic_person,
                    ()->{if(!floating)panel.dismiss();LauncherPin.settings(host);});
            if(!floating){
                section(host,rows,R.string.h_more_tools);
                pair(host,rows,panel,actions,R.string.h_search,R.drawable.ic_search,SEARCH,
                        R.string.h_store,R.drawable.ic_apps,STORE);
            }
            panel.show();
            if (systemGear != null) systemGear.requestFocus();
            return panel;
        }
        if(ThemeStyle.id(prefs)==ThemeStyle.GLASS || ThemeStyle.harmony(prefs)) {
            rows.setId(R.id.controlCenterPanel);
            rows.addView(GlassSettings.label(host,ThemeStyle.harmony(prefs)?"Harmony Vision • Android TV":prefs.getString("name","Profile")+" • Android TV",12,0xffa5b4cf));
            if (!floating) rows.addView(GlassSettings.row(host,"Close",null,android.R.drawable.ic_menu_close_clear_cancel,GlassSettings.themeAccent(host),panel::dismiss));
        }
        if (systemGear != null) rows.addView(systemGear);
        rows.addView(GlassSettings.label(host, network, 12, 0xffb5cadd));
        section(host, rows, R.string.h_quick_settings);
        rows.addView(new ControlCenterGrid(host, action -> {
            if (!actions.keepPanel(action)) panel.dismiss();
            actions.open(action);
        }));
        section(host, rows, R.string.h_personalize);
        row(host, rows, R.string.h_launcher_settings, R.string.h_layout_hint, R.drawable.ic_palette, () -> actions.open(SETTINGS));
        if (!floating) row(host, rows, R.string.h_single_identity, 0, R.drawable.ic_person, () -> { panel.dismiss(); actions.open(IDENTITY); });
        boolean appsOnly = ThemePreferences.get(host).getBoolean("apps_only", false);
        if (!floating) row(host, rows, R.string.h_apps_only, appsOnly ? R.string.h_on : R.string.h_off, R.drawable.ic_apps, () -> {
            ThemePreferences.get(host).edit().putBoolean("apps_only", !appsOnly).apply();
            panel.dismiss(); rebuild.run();
        });
        row(host, rows, R.string.h_local_pin, R.string.h_local_pin_hint, R.drawable.ic_person, () -> { if (!floating) panel.dismiss(); LauncherPin.settings(host); });
        row(host, rows, R.string.h_sleep_timer, R.string.h_sleep_timer_hint, R.drawable.ic_media_pause, () -> { if (!floating) panel.dismiss(); LauncherSleep.show(host,floating); });
        section(host, rows, R.string.h_more_tools);
        if (!floating) pair(host, rows, panel, actions, R.string.h_search, R.drawable.ic_search, SEARCH,
                R.string.h_store, R.drawable.ic_apps, STORE);
        else row(host,rows,R.string.h_store,0,R.drawable.ic_apps,()-> { panel.dismiss(); actions.open(STORE); });
        panel.show();
        if (systemGear != null) systemGear.requestFocus();
        return panel;
    }
    private static void pair(Activity host, LinearLayout rows, AlertDialog panel, Actions actions,
            int first, int firstIcon, int firstAction, int second, int secondIcon, int secondAction) {
        LinearLayout strip = new LinearLayout(host);
        card(host, strip, first, firstIcon, () -> { if (!actions.keepPanel(firstAction)) panel.dismiss(); actions.open(firstAction); });
        card(host, strip, second, secondIcon, () -> { if (!actions.keepPanel(secondAction)) panel.dismiss(); actions.open(secondAction); });
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
