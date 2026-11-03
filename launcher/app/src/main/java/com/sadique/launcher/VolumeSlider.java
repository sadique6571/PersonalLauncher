package com.sadique.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
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
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small TV volume OSD; accessibility overlays need no new overlay permission. */
final class VolumeSlider {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Context owner;
    private static WindowManager window;
    private static LinearLayout panel;
    private static VolumeBar slider;
    private static ImageView outputIcon;
    private static android.media.AudioDeviceCallback deviceCallback;
    private static AudioManager observedAudio;
    private static boolean observedMute;
    private static long routeChecked;
    private static int routeIcon = R.drawable.ic_volume;
    private static int drawnIcon, drawnAccent, drawnPercent = -1;
    private static TextView level;
    private static final Runnable HIDE = VolumeSlider::hide;
    private static final java.util.Set<AccessibilityService> SERVICES = new java.util.LinkedHashSet<>();
    private static AccessibilityService observerOwner;
    private static BroadcastReceiver receiver;
    private static VolumePanelUpdates direct;
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
        observedMute = audio.isStreamMute(AudioManager.STREAM_MUSIC);
        direct = new VolumePanelUpdates(HANDLER::post, sentAt -> {
            long now = SystemClock.uptimeMillis();
            if (!VolumePanelUpdates.fresh(sentAt, now) || observerOwner != service) return;
            directUntil = now + 2000;
            boolean first = panel == null || panel.getVisibility() != View.VISIBLE;
            boolean shown = show(service, audio);
            if (first && shown) Log.d("PersonalVolume", "direct panel UI applied, age=" + (SystemClock.uptimeMillis() - sentAt) + "ms");
        });
        VolumePanelBinder.attach(direct);
        BroadcastReceiver changes = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!"android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())
                        && !"android.media.STREAM_MUTE_CHANGED_ACTION".equals(intent.getAction())) return;
                if (SystemClock.uptimeMillis() < directUntil) return;
                int stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC);
                if (stream != AudioManager.STREAM_MUSIC) return;
                int value = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                boolean muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
                if (value == observedVolume && muted == observedMute) return;
                observedVolume = value; observedMute = muted;
                Log.d("PersonalVolume", "stream changed: " + value);
                show(service, audio);
            }
        };
        try {
            IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            filter.addAction("android.media.STREAM_MUTE_CHANGED_ACTION");
            // Read AudioManager, not broadcast extras. Privileged vendor audio processes may be senders.
            if (Build.VERSION.SDK_INT >= 33) service.registerReceiver(changes, filter, Context.RECEIVER_EXPORTED);
            else service.registerReceiver(changes, filter);
            observerOwner = service; receiver = changes;
            observedAudio = audio;
            deviceCallback = new android.media.AudioDeviceCallback() {
                private void changed() {
                    routeChecked = 0;
                    if (panel != null && panel.getVisibility() == View.VISIBLE) show(service, audio);
                }
                @Override public void onAudioDevicesAdded(android.media.AudioDeviceInfo[] devices) { changed(); }
                @Override public void onAudioDevicesRemoved(android.media.AudioDeviceInfo[] devices) { changed(); }
            };
            try { audio.registerAudioDeviceCallback(deviceCallback, HANDLER); }
            catch (RuntimeException unavailable) { deviceCallback = null; }
            Log.d("PersonalVolume", "volume observer connected: " + service.getClass().getSimpleName());
        } catch (RuntimeException unavailable) {
            Log.w("PersonalVolume", "Volume observer unavailable", unavailable);
            if (direct != null) { VolumePanelBinder.detach(direct); direct.close(); }
            direct = null;
        }
    }

    static void release(AccessibilityService service) {
        SERVICES.remove(service);
        hide(service);
        if (observerOwner != service) return;
        try { service.unregisterReceiver(receiver); }
        catch (RuntimeException detached) { /* Android may already have removed the service. */ }
        if (observedAudio != null && deviceCallback != null) observedAudio.unregisterAudioDeviceCallback(deviceCallback);
        observedAudio = null; deviceCallback = null;
        if (direct != null) { VolumePanelBinder.detach(direct); direct.close(); }
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
                panel.setPadding(dp(context, 5), dp(context, 5), dp(context, 5), dp(context, 5));
                // Transparent container: only the wide track, output icon and level are drawn.
                slider = new VolumeBar(context);
                slider.setContentDescription(context.getString(R.string.h_sound));
                panel.addView(slider, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 210)));
                outputIcon = new ImageView(context);
                outputIcon.setPadding(0, dp(context, 8), 0, 0);
                panel.addView(outputIcon, new LinearLayout.LayoutParams(dp(context, 28), dp(context, 36)));
                level = new TextView(context); level.setTextColor(Color.WHITE); level.setTextSize(14);
                level.setShadowLayer(dp(context, 2), 0, dp(context, 1), Color.BLACK);
                level.setGravity(Gravity.CENTER);
                panel.addView(level, new LinearLayout.LayoutParams(-1, dp(context, 24)));
                WindowManager.LayoutParams position = new WindowManager.LayoutParams(dp(context, 64), -2,
                        context instanceof AccessibilityService ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                                : WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
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
        int value = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
        observedVolume = value; observedMute = muted;
        int accent = GlassSettings.themeAccent(owner);
        slider.level(VolumeLevel.fraction(value, maximum, muted), accent);
        long now = SystemClock.uptimeMillis();
        if (routeChecked == 0 || now - routeChecked >= 500) {
            routeIcon = AudioOutputIcon.resource(audio); routeChecked = now;
        }
        int icon = muted || value == 0 ? R.drawable.ic_volume_muted : routeIcon;
        if (drawnIcon != icon) { outputIcon.setImageResource(icon); drawnIcon = icon; }
        if (drawnAccent != accent) { outputIcon.setColorFilter(accent); drawnAccent = accent; }
        int percent = VolumeLevel.percent(value, maximum, muted);
        if (drawnPercent != percent) {
            level.setText(percent + "%");
            panel.setContentDescription(owner.getString(R.string.h_sound) + " " + percent + "%");
            drawnPercent = percent;
        }
    }
    /** Show immediately, then read the result after the firmware handles the native mute key. */
    static void nativeMute(Context context) {
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio == null) return;
        show(context, audio);
        HANDLER.postDelayed(() -> { if (owner != null) show(owner, audio); }, 70);
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
        owner = null; panel = null; window = null; slider = null; level = null; outputIcon = null; maximum = 0; routeChecked = 0; drawnIcon = 0; drawnAccent = 0; drawnPercent = -1;
    }
    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
