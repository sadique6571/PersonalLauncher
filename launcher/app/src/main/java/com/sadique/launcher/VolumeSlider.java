package com.sadique.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Small TV volume OSD; accessibility overlays need no new overlay permission. */
final class VolumeSlider {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Context owner;
    private static WindowManager window;
    private static LinearLayout panel;
    private static SeekBar slider;
    private static TextView level;
    private static final Runnable HIDE = VolumeSlider::hide;
    private static final java.util.Set<AccessibilityService> SERVICES = new java.util.LinkedHashSet<>();
    private static AccessibilityService observerOwner;
    private static BroadcastReceiver receiver;
    private static VolumePanelSocket direct;
    private static long directUntil;
    private static int observedVolume = -1;
    private static int maximum;

    private VolumeSlider() { }

    /** Firmware can apply a volume key without delivering it to the key filter. Observe the actual
     * stream change as well; both enabled helpers share one receiver and one window. */
    static void observe(AccessibilityService service) {
        SERVICES.add(service);
        if (receiver != null) return;
        AudioManager audio = service.getSystemService(AudioManager.class);
        if (audio == null) return;
        observedVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        try {
            direct = new VolumePanelSocket(new VolumePanelUpdates(HANDLER::post, sentAt -> {
                long now = SystemClock.uptimeMillis();
                if (!VolumePanelUpdates.fresh(sentAt, now) || observerOwner != service) return;
                directUntil = now + 2000;
                boolean first = panel == null || panel.getVisibility() != View.VISIBLE;
                show(service, audio);
                if (first) Log.d("PersonalVolume", "direct panel UI applied, age=" + (SystemClock.uptimeMillis() - sentAt) + "ms");
            }));
        } catch (java.io.IOException unavailable) {
            Log.w("PersonalVolume", "Direct panel listener unavailable; volume broadcast fallback remains", unavailable);
        }
        BroadcastReceiver changes = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!"android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) return;
                if (SystemClock.uptimeMillis() < directUntil) return;
                int stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC);
                if (stream != AudioManager.STREAM_MUSIC) return;
                int value = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (value == observedVolume) return;
                observedVolume = value;
                Log.d("PersonalVolume", "stream changed: " + value);
                show(service, audio);
            }
        };
        try {
            IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            // Read AudioManager, not broadcast extras. Privileged vendor audio processes may be senders.
            if (Build.VERSION.SDK_INT >= 33) service.registerReceiver(changes, filter, Context.RECEIVER_EXPORTED);
            else service.registerReceiver(changes, filter);
            observerOwner = service; receiver = changes;
            Log.d("PersonalVolume", "volume observer connected: " + service.getClass().getSimpleName());
        } catch (RuntimeException unavailable) {
            Log.w("PersonalVolume", "Volume observer unavailable", unavailable);
            if (direct != null) direct.close();
            direct = null;
        }
    }

    static void release(AccessibilityService service) {
        SERVICES.remove(service);
        hide(service);
        if (observerOwner != service) return;
        try { service.unregisterReceiver(receiver); }
        catch (RuntimeException detached) { /* Android may already have removed the service. */ }
        if (direct != null) direct.close();
        direct = null; directUntil = 0;
        observerOwner = null; receiver = null;
        if (!SERVICES.isEmpty()) observe(SERVICES.iterator().next());
    }

    static boolean show(Context context, AudioManager audio) {
        if (observerOwner != null) context = observerOwner;
        if (!(context instanceof AccessibilityService) && !(context instanceof Activity)) return false;
        try {
            if (owner != context) dispose();
            if (panel == null) {
                owner = context;
                window = context.getSystemService(WindowManager.class);
                panel = new LinearLayout(context); panel.setOrientation(LinearLayout.VERTICAL);
                panel.setGravity(Gravity.CENTER_HORIZONTAL);
                panel.setPadding(dp(context, 12), dp(context, 16), dp(context, 12), dp(context, 12));
                int accent = GlassSettings.themeAccent(context);
                GradientDrawable background = new GradientDrawable(); background.setColor(0xf21a293c);
                background.setCornerRadius(dp(context, 18)); background.setStroke(dp(context, 1), accent);
                panel.setBackground(background);
                ImageView icon = new ImageView(context); icon.setImageResource(R.drawable.ic_volume);
                icon.setColorFilter(accent);
                panel.addView(icon, new LinearLayout.LayoutParams(dp(context, 26), dp(context, 26)));
                slider = new SeekBar(context); slider.setContentDescription(context.getString(R.string.h_sound));
                slider.setFocusable(false); slider.setProgressTintList(ColorStateList.valueOf(accent));
                slider.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
                FrameLayout track = new FrameLayout(context);
                track.setClipChildren(false); track.setClipToPadding(false);
                slider.setRotation(-90);
                track.addView(slider, new FrameLayout.LayoutParams(dp(context, 180), dp(context, 44), Gravity.CENTER));
                panel.addView(track, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 180)));
                level = new TextView(context); level.setTextColor(Color.WHITE); level.setTextSize(16);
                level.setGravity(Gravity.CENTER);
                panel.addView(level, new LinearLayout.LayoutParams(-1, dp(context, 28)));
                slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                        if (!fromUser) return;
                        try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0); update(audio); }
                        catch (RuntimeException unavailable) { hide(); }
                        scheduleHide();
                    }
                    public void onStartTrackingTouch(SeekBar bar) { HANDLER.removeCallbacks(HIDE); }
                    public void onStopTrackingTouch(SeekBar bar) { scheduleHide(); }
                });
                WindowManager.LayoutParams position = new WindowManager.LayoutParams(dp(context, 80), -2,
                        context instanceof AccessibilityService ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                                : WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT);
                position.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL; position.x = dp(context, 20);
                if (context instanceof Activity) position.token = ((Activity) context).getWindow().getDecorView().getWindowToken();
                window.addView(panel, position);
            }
            boolean first = panel.getVisibility() != View.VISIBLE;
            if (maximum == 0 || first) maximum = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            update(audio); panel.setVisibility(View.VISIBLE); scheduleHide(); return true;
        } catch (RuntimeException unavailable) {
            Log.w("PersonalVolume", "Volume slider window unavailable", unavailable);
            dispose(); return false;
        }
    }
    private static void update(AudioManager audio) {
        int max = Math.max(1, maximum);
        int value = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        observedVolume = value;
        if (slider.getMax() != max) slider.setMax(max);
        slider.setProgress(value);
        level.setText(Math.round(value * 100f / max) + "%");
    }
    private static void scheduleHide() { HANDLER.removeCallbacks(HIDE); HANDLER.postDelayed(HIDE, 1600); }
    static void hide(Context context) { if (owner == context) dispose(); }
    private static void hide() {
        HANDLER.removeCallbacks(HIDE);
        if (panel != null) panel.setVisibility(View.INVISIBLE);
    }
    private static void dispose() {
        HANDLER.removeCallbacks(HIDE);
        if (panel != null && window != null) try { window.removeViewImmediate(panel); }
        catch (RuntimeException detached) { /* Window already removed by Android. */ }
        owner = null; panel = null; window = null; slider = null; level = null; maximum = 0;
    }
    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
