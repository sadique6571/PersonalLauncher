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
        TextView text = new TextView(host); text.setText(value); text.setTextColor(0xffc9dff2); text.setTextSize(14);
        int padding = Math.round(12 * host.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, 0); return text;
    }
    static Runnable attach(Activity host, LinearLayout rows, SharedPreferences prefs, IntConsumer homeBrightness, Runnable leavePanel) {
        boolean deviceBrightness = Settings.System.canWrite(host);
        TextView lightLabel = label(host, deviceBrightness ? "Device brightness" : "Home brightness"); rows.addView(lightLabel);
        int percent = prefs.getInt("brightness", 80);
        if (deviceBrightness) {
            try { percent = Settings.System.getInt(host.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 204) * 100 / 255; }
            catch (RuntimeException unavailable) { /* The local Home value remains usable. */ }
        }
        SeekBar light = new SeekBar(host); light.setMax(90); light.setProgress(Math.max(0, Math.min(90, percent - 10)));
        light.setContentDescription("Brightness"); rows.addView(light);
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
        TextView volumeLabel = label(host, fixed ? "Media volume · fixed by device" : "Media volume"); rows.addView(volumeLabel);
        SeekBar volume = new SeekBar(host); volume.setMax(Math.max(1, maximum)); volume.setEnabled(!fixed);
        volume.setContentDescription("Media volume");
        if (audio != null) volume.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        rows.addView(volume);
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
