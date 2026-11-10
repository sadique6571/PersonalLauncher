package com.sadique.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.LinearLayout;

/** Small TV volume OSD; accessibility overlays need no new overlay permission. */
final class VolumeSlider {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Context owner;
    private static WindowManager window;
    private static LinearLayout panel;
    private static GlassVolumeCapsule capsule;
    private static android.media.AudioDeviceCallback deviceCallback;
    private static AudioManager observedAudio;
    private static boolean observedMute;
    private static long routeChecked;
    private static int routeIcon = R.drawable.ic_volume;
    private static final Runnable HIDE = VolumeSlider::hide;
    private static final java.util.Set<AccessibilityService> SERVICES = new java.util.LinkedHashSet<>();
    private static AccessibilityService observerOwner;
    private static BroadcastReceiver receiver;
    private static VolumePanelUpdates direct;
    private static long directUntil;
    private static int observedVolume = -1;
    private static int maximum;
    private static int drawnStyle;

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
            android.content.SharedPreferences prefs = ThemePreferences.get(context);
            int style = ThemeStyle.id(prefs);
            style = style * 31 + prefs.getInt("app_font", 0);
            style = style * 31 + prefs.getInt("glass_finish", 0);
            style = style * 31 + prefs.getInt("glass_alpha", 70);
            style = style * 31 + prefs.getInt("glass_edge", 1);
            style = style * 31 + (PanelTransparency.enabled(prefs) ? 1 : 0);
            style = style * 31 + prefs.getInt("theme_name", 0);
            style = style * 31 + prefs.getInt("custom_accent", 0);
            if (owner != context || drawnStyle != style) dispose();
            if (panel == null) {
                owner = context; drawnStyle = style;
                window = context.getSystemService(WindowManager.class);
                capsule = new GlassVolumeCapsule(context);panel=capsule;
                panel.setVisibility(View.INVISIBLE);
                WindowManager.LayoutParams position = new WindowManager.LayoutParams(dp(context, 64), dp(context, 218),
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
            update(audio, !first); panel.setVisibility(View.VISIBLE); scheduleHide(); return true;
        } catch (RuntimeException unavailable) {
            Log.w("PersonalVolume", "Volume slider window unavailable", unavailable);
            dispose(); return false;
        }
    }
    private static void update(AudioManager audio, boolean animate) {
        int value=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted=audio.isStreamMute(AudioManager.STREAM_MUSIC);
        observedVolume=value;observedMute=muted;
        int accent=GlassSettings.themeAccent(owner);
        long now=SystemClock.uptimeMillis();
        if(routeChecked==0||now-routeChecked>=500){routeIcon=AudioOutputIcon.resource(audio);routeChecked=now;}
        int icon=muted||value==0?R.drawable.ic_volume_muted:routeIcon;
        int percent=VolumeLevel.percent(value,maximum,muted);
        capsule.state(VolumeLevel.fraction(value,maximum,muted),percent,muted,icon,accent,animate);
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
        if (capsule != null) capsule.stopAnimation();
        if (panel != null) panel.setVisibility(View.INVISIBLE);
    }
    private static void dispose() {
        HANDLER.removeCallbacks(HIDE);
        if (capsule != null) capsule.stopAnimation();
        if (panel != null && window != null) try { window.removeViewImmediate(panel); }
        catch (RuntimeException detached) { /* Window already removed by Android. */ }
        owner = null; panel = null; window = null; capsule = null; maximum = 0; routeChecked = 0;
    }
    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

