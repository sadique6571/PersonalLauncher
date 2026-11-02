package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Local keycode overrides. Never maps shared Linux scan codes such as Haier's scan 240. */
final class RemoteSetup {
    private static IntConsumer learning;
    private static int learnedCode = -1;
    private static int consumedDevice = -1, consumedCode = -1;
    private static long consumedDownTime = -1;
    private RemoteSetup() { }
    static boolean allowed(int code) {
        return code > 0 && code != KeyEvent.KEYCODE_HOME && code != KeyEvent.KEYCODE_BACK
                && code != KeyEvent.KEYCODE_POWER && code != KeyEvent.KEYCODE_SLEEP && code != KeyEvent.KEYCODE_WAKEUP
                && code != KeyEvent.KEYCODE_ENTER && code != KeyEvent.KEYCODE_NUMPAD_ENTER
                && !(code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_CENTER)
                && !(code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9)
                && !(code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9)
                && code != KeyEvent.KEYCODE_PERIOD && code != KeyEvent.KEYCODE_NUMPAD_DOT;
    }
    static boolean capture(KeyEvent event) {
        if (event.getDeviceId() == consumedDevice && event.getKeyCode() == consumedCode
                && event.getDownTime() == consumedDownTime) return true;
        if (learning == null || !allowed(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && learnedCode == -1) {
            learnedCode = event.getKeyCode();
            consumedCode = event.getKeyCode(); consumedDevice = event.getDeviceId(); consumedDownTime = event.getDownTime();
            IntConsumer session = learning;
            // Leave the session installed until the action chooser is opened; duplicate service delivery is swallowed.
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                IntConsumer callback = learning;
                int code = learnedCode;
                if (callback == session) callback.accept(code);
            });
        }
        return true;
    }
    static boolean handle(Context context, KeyEvent event) {
        if (capture(event)) return true;
        String action = context.getSharedPreferences("remote_button_overrides", 0)
                .getString(Integer.toString(event.getKeyCode()), null);
        return action != null && allowed(event.getKeyCode()) && RemoteKeys.handle(context, event, action, null);
    }
    static void show(Activity host) {
        LinearLayout rows = HomeLibraryUi.rows(host);
        AlertDialog panel = GlassSettings.panel(host, host.getString(R.string.h_remote_setup), rows);
        rows.addView(GlassSettings.row(host, host.getString(R.string.h_remote_setup), host.getString(R.string.h_learn_key),
                R.drawable.ic_settings, GlassSettings.themeAccent(host), () -> { panel.dismiss(); learn(host); }));
        SharedPreferences prefs = host.getSharedPreferences("remote_button_overrides", 0);
        for (String key : new java.util.TreeSet<>(prefs.getAll().keySet())) {
            int code;
            try { code = Integer.parseInt(key); } catch (NumberFormatException bad) { continue; }
            final int selected = code;
            rows.addView(GlassSettings.row(host, KeyEvent.keyCodeToString(code), prefs.getString(key, ""),
                    R.drawable.ic_settings, GlassSettings.themeAccent(host), () -> { panel.dismiss(); choose(host, selected); }));
        }
        panel.show();
    }
    private static void learn(Activity host) {
        AlertDialog dialog = new AlertDialog.Builder(host).setTitle(R.string.h_remote_setup).setMessage(R.string.h_learn_key)
                .setNegativeButton(android.R.string.cancel, null).create();
        learnedCode = -1;
        IntConsumer session = code -> { dialog.dismiss(); if (!host.isDestroyed()) choose(host, code); };
        learning = session;
        dialog.setOnKeyListener((d, code, event) -> capture(event));
        dialog.setOnDismissListener(d -> { if (learning == session) { learning = null; learnedCode = -1; } });
        dialog.show();
    }
    private static void choose(Activity host, int code) {
        String[] actions = {"home_settings", "wifi", "inputs", "mute", "screensaver", "ignore"};
        String[] labels = {host.getString(R.string.h_settings_action), host.getString(R.string.h_wifi_action),
                host.getString(R.string.h_inputs_action), host.getString(R.string.h_mute_action), host.getString(R.string.h_screensaver_action),
                host.getString(R.string.h_ignore_action), host.getString(R.string.h_choose_app), host.getString(R.string.h_restore_key)};
        new AlertDialog.Builder(host).setTitle(host.getString(R.string.h_choose_action) + " · " + KeyEvent.keyCodeToString(code))
                .setItems(labels, (d, which) -> {
                    if (which < actions.length) save(host, code, actions[which]);
                    else if (which == actions.length) apps(host, code);
                    else save(host, code, null);
                }).show();
    }
    private static void apps(Activity host, int code) {
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            List<ResolveInfo> apps = host.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
            apps.sort((a, b) -> a.loadLabel(host.getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(host.getPackageManager()).toString()));
            List<String> labels = new ArrayList<>(), targets = new ArrayList<>();
            for (ResolveInfo app : apps) {
                labels.add(app.loadLabel(host.getPackageManager()).toString());
                targets.add(app.activityInfo.packageName + "/" + app.activityInfo.name);
            }
            host.runOnUiThread(() -> { if (!host.isDestroyed()) new AlertDialog.Builder(host).setTitle(R.string.h_choose_app)
                    .setItems(labels.toArray(new String[0]), (d, which) -> save(host, code, targets.get(which))).show(); });
        }); worker.shutdown();
    }
    private static void save(Activity host, int code, String action) {
        SharedPreferences.Editor edit = host.getSharedPreferences("remote_button_overrides", 0).edit();
        if (action == null) edit.remove(Integer.toString(code)); else edit.putString(Integer.toString(code), action);
        edit.apply(); Toast.makeText(host, R.string.h_mapping_saved, Toast.LENGTH_LONG).show();
    }
}
