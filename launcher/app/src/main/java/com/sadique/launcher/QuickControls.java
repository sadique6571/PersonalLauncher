package com.sadique.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.function.IntConsumer;

/** Inline controls; optional device brightness uses Android's user-granted special access. */
final class QuickControls {
    private static TextView label(Activity host, String value) {
        return GlassSettings.label(host, value, 13, 0xffe0ecf7);
    }
    private static void sliderCard(Activity host, LinearLayout rows, TextView label, SeekBar slider, int symbol) {
        LinearLayout card = new LinearLayout(host); card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setBackground(GlassSettings.background(host, false));
        card.setPadding(GlassSettings.dp(host, 12), GlassSettings.dp(host, 7), GlassSettings.dp(host, 10), GlassSettings.dp(host, 7));
        android.widget.ImageView icon = new android.widget.ImageView(host); icon.setImageResource(symbol); icon.setColorFilter(0xffb9d5e8);
        card.addView(icon, new LinearLayout.LayoutParams(GlassSettings.dp(host, 24), GlassSettings.dp(host, 24)));
        LinearLayout controls = new LinearLayout(host); controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(GlassSettings.dp(host, 12), 0, 0, 0); controls.addView(label);
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff55baff));
        slider.setThumbTintList(android.content.res.ColorStateList.valueOf(0xffd4efff));
        slider.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff2a3d56));
        controls.addView(slider, new LinearLayout.LayoutParams(-1, GlassSettings.dp(host, 27)));
        slider.setOnFocusChangeListener((v, focus) -> card.setBackground(GlassSettings.background(host, focus)));
        card.addView(controls, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(-1, -2);
        position.setMargins(0, GlassSettings.dp(host, 3), 0, GlassSettings.dp(host, 3)); rows.addView(card, position);
    }
    static Runnable attach(Activity host, LinearLayout rows, SharedPreferences prefs, IntConsumer homeBrightness, Runnable leavePanel) {
        boolean deviceBrightness = Settings.System.canWrite(host);
        TextView lightLabel = label(host, deviceBrightness ? "Device brightness" : "Home brightness");
        int percent = prefs.getInt("brightness", 80);
        if (deviceBrightness) {
            try { percent = Settings.System.getInt(host.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 204) * 100 / 255; }
            catch (RuntimeException unavailable) { /* The local Home value remains usable. */ }
        }
        SeekBar light = new SeekBar(host); light.setMax(90); light.setProgress(Math.max(0, Math.min(90, percent - 10)));
        light.setContentDescription("Brightness");
        lightLabel.setText((deviceBrightness ? "Device brightness · " : "Home brightness · ") + percent + "%");
        sliderCard(host, rows, lightLabel, light, R.drawable.ic_brightness);
        light.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int value = progress + 10;
                lightLabel.setText((Settings.System.canWrite(host) ? "Device brightness · " : "Home brightness · ") + value + "%");
                if (!fromUser) return;
                prefs.edit().putInt("brightness", value).apply(); homeBrightness.accept(value);
                if (Settings.System.canWrite(host)) {
                    try {
                        boolean mode = Settings.System.putInt(host.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                        boolean saved = Settings.System.putInt(host.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.round(value * 2.55f));
                        if (!mode || !saved) throw new IllegalStateException("Brightness rejected");
                    } catch (RuntimeException rejected) {
                        lightLabel.setText("Home brightness · " + value + "%");
                        Toast.makeText(host, "TV device brightness is unavailable; Home brightness was adjusted", Toast.LENGTH_LONG).show();
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        if (!deviceBrightness) {
            Button grant = new Button(host); grant.setAllCaps(false); grant.setText("Enable device brightness control");
            grant.setTextSize(11); grant.setTextColor(0xffb7cede); grant.setBackground(GlassSettings.background(host, false));
            grant.setOnFocusChangeListener((v, focus) -> grant.setBackground(GlassSettings.background(host, focus)));
            grant.setOnClickListener(v -> {
                leavePanel.run();
                try { host.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + host.getPackageName()))); }
                catch (RuntimeException unavailable) { Toast.makeText(host, "This TV only supports Home-window brightness here", Toast.LENGTH_LONG).show(); }
            });
            rows.addView(grant);
        }
        AudioManager audio = host.getSystemService(AudioManager.class);
        int maximum = audio == null ? 0 : audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        boolean fixed = audio == null || maximum <= 0 || audio.isVolumeFixed();
        TextView volumeLabel = label(host, fixed ? "Media volume · fixed by device" : "Media volume");
        SeekBar volume = new SeekBar(host); volume.setMax(Math.max(1, maximum)); volume.setEnabled(!fixed);
        volume.setContentDescription("Media volume");
        if (audio != null) volume.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        if (!fixed) volumeLabel.setText("Media volume · " + Math.round(volume.getProgress() * 100f / maximum) + "%");
        sliderCard(host, rows, volumeLabel, volume, R.drawable.ic_volume);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fixed) return;
                volumeLabel.setText("Media volume · " + Math.round(value * 100f / maximum) + "%");
                if (fromUser) try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0); }
                catch (RuntimeException unavailable) { Toast.makeText(host, "Volume control is unavailable on this TV", Toast.LENGTH_SHORT).show(); }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        BroadcastReceiver changed = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (audio != null && !fixed) volume.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            }
        };
        boolean registered;
        try {
            IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            if (Build.VERSION.SDK_INT >= 33) host.registerReceiver(changed, filter, Context.RECEIVER_NOT_EXPORTED);
            else host.registerReceiver(changed, filter);
            registered = true;
        } catch (RuntimeException unavailable) { registered = false; }
        boolean listening = registered;
        return () -> { if (listening) try { host.unregisterReceiver(changed); } catch (RuntimeException ignored) { } };
    }
}
