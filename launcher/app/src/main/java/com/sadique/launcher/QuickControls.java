package com.sadique.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Inline media-volume control; display brightness belongs to the TV settings. */
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
    static Runnable attach(Activity host, LinearLayout rows) {
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
