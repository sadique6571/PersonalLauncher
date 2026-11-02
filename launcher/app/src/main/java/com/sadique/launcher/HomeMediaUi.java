package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.Collections;
import java.util.List;

/** Opt-in media controls through public APIs; unsupported apps have an explicit empty state. */
final class HomeMediaUi {
    private HomeMediaUi() { }
    static ComponentName listener(Activity host) { return new ComponentName(host, HomeNotificationService.class); }
    static boolean approved(Activity host) {
        String enabled = Settings.Secure.getString(host.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        for (String component : enabled.split(":")) if (listener(host).equals(ComponentName.unflattenFromString(component))) return true;
        return false;
    }
    private static void request(Activity host) {
        new AlertDialog.Builder(host).setTitle(R.string.h_access_title).setMessage(R.string.h_access_hint)
                .setPositiveButton(R.string.h_open_access, (d, which) -> {
                    try { host.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                    catch (RuntimeException unavailable) { Toast.makeText(host, R.string.h_unavailable, Toast.LENGTH_LONG).show(); }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
    private static AlertDialog page(Activity host, int title, LinearLayout rows) {
        AlertDialog dialog = GlassSettings.panel(host, host.getString(title), rows);
        rows.addView(GlassSettings.row(host, host.getString(R.string.h_refresh), null, R.drawable.ic_apps, GlassSettings.themeAccent(host), () -> {
            dialog.dismiss(); if (title == R.string.h_now_playing) nowPlaying(host); else notifications(host);
        }));
        rows.addView(GlassSettings.row(host, host.getString(R.string.h_access_title), host.getString(R.string.h_optional_access), R.drawable.ic_settings,
                GlassSettings.themeAccent(host), () -> request(host)));
        return dialog;
    }
    static void notifications(Activity host) {
        LinearLayout rows = HomeLibraryUi.rows(host); AlertDialog dialog = page(host, R.string.h_notifications, rows);
        if (!approved(host)) rows.addView(GlassSettings.label(host, host.getString(R.string.h_access_hint), 14, 0xffb5cadd));
        else {
            List<HomeNotificationService.Entry> entries = HomeNotificationService.snapshot();
            Collections.reverse(entries);
            if (entries.isEmpty()) rows.addView(GlassSettings.label(host, host.getString(R.string.h_notifications_empty), 14, 0xffb5cadd));
            for (HomeNotificationService.Entry entry : entries) rows.addView(GlassSettings.row(host, entry.title,
                    entry.text, R.drawable.ic_apps, GlassSettings.themeAccent(host), () -> {
                        if (entry.open == null) return;
                        try { entry.open.send(); dialog.dismiss(); }
                        catch (android.app.PendingIntent.CanceledException unavailable) { Toast.makeText(host, R.string.h_unavailable, Toast.LENGTH_SHORT).show(); }
                    }));
        }
        dialog.show();
    }
    static void nowPlaying(Activity host) {
        LinearLayout rows = HomeLibraryUi.rows(host); AlertDialog dialog = page(host, R.string.h_now_playing, rows);
        if (!approved(host)) {
            rows.addView(GlassSettings.label(host, host.getString(R.string.h_access_hint), 14, 0xffb5cadd)); dialog.show(); return;
        }
        MediaSessionManager manager = host.getSystemService(MediaSessionManager.class);
        List<MediaController> controllers;
        try { controllers = manager == null ? Collections.emptyList() : manager.getActiveSessions(listener(host)); }
        catch (SecurityException unavailable) { controllers = Collections.emptyList(); }
        if (controllers.isEmpty()) rows.addView(GlassSettings.label(host, host.getString(R.string.h_media_empty), 14, 0xffb5cadd));
        int count = 0;
        for (MediaController controller : controllers) {
            if (count++ >= 3) break;
            MediaMetadata data = controller.getMetadata(); PlaybackState state = controller.getPlaybackState();
            CharSequence title = data == null ? null : data.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            if (title == null && data != null) title = data.getText(MediaMetadata.METADATA_KEY_TITLE);
            rows.addView(GlassSettings.label(host, title == null ? controller.getPackageName() : title.toString(), 18, 0xfff0f7ff));
            if (data != null) {
                android.graphics.Bitmap artwork = null;
                try {
                    artwork = data.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    if (artwork == null) artwork = data.getBitmap(MediaMetadata.METADATA_KEY_ART);
                } catch (RuntimeException unavailable) { /* Malformed metadata cannot break the dashboard. */ }
                if (artwork != null && !artwork.isRecycled()) {
                    int edge = Math.max(artwork.getWidth(), artwork.getHeight());
                    if (edge > 240) artwork = android.graphics.Bitmap.createScaledBitmap(artwork,
                            Math.max(1, artwork.getWidth() * 240 / edge), Math.max(1, artwork.getHeight() * 240 / edge), true);
                    android.widget.ImageView image = new android.widget.ImageView(host); image.setImageBitmap(artwork);
                    image.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                    rows.addView(image, new LinearLayout.LayoutParams(-1, GlassSettings.dp(host, 110)));
                }
            }
            long actions = state == null ? 0 : state.getActions();
            LinearLayout strip = new LinearLayout(host);
            control(host, strip, R.string.h_previous, (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0, () -> controller.getTransportControls().skipToPrevious());
            boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
            boolean supported = (actions & (playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY)) != 0 || (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0;
            control(host, strip, playing ? R.string.h_pause : R.string.h_play, supported, () -> {
                if (playing) controller.getTransportControls().pause(); else controller.getTransportControls().play();
                strip.postDelayed(() -> { if (dialog.isShowing() && !host.isDestroyed()) { dialog.dismiss(); nowPlaying(host); } }, 200);
            });
            control(host, strip, R.string.h_next, (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0, () -> controller.getTransportControls().skipToNext());
            rows.addView(strip);
            if (controller.getSessionActivity() != null) rows.addView(GlassSettings.row(host, host.getString(R.string.h_open_app), controller.getPackageName(), R.drawable.ic_apps,
                    GlassSettings.themeAccent(host), () -> {
                        try { controller.getSessionActivity().send(); dialog.dismiss(); }
                        catch (Exception unavailable) { Toast.makeText(host, R.string.h_unavailable, Toast.LENGTH_SHORT).show(); }
                    }));
        }
        dialog.show();
    }
    private static void control(Activity host, LinearLayout row, int label, boolean enabled, Runnable action) {
        int icon = label == R.string.h_previous ? R.drawable.ic_media_previous : label == R.string.h_next ? R.drawable.ic_media_next
                : label == R.string.h_pause ? R.drawable.ic_media_pause : R.drawable.ic_media_play;
        View card = GlassSettings.wideCard(host, host.getString(label), icon, GlassSettings.themeAccent(host), () -> {
            try { action.run(); } catch (RuntimeException unavailable) { Toast.makeText(host, R.string.h_unavailable, Toast.LENGTH_SHORT).show(); }
        });
        card.setEnabled(enabled); card.setFocusable(enabled); card.setAlpha(enabled ? 1f : .35f);
        row.addView(card, new LinearLayout.LayoutParams(0, -2, 1));
    }
}
