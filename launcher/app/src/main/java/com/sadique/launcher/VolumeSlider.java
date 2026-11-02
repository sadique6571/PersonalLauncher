package com.sadique.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;
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
    private VolumeSlider() { }

    static boolean show(Context context, AudioManager audio) {
        if (!(context instanceof AccessibilityService) && !(context instanceof Activity)) return false;
        try {
            if (owner != context) hide();
            if (panel == null) {
                owner = context;
                window = context.getSystemService(WindowManager.class);
                panel = new LinearLayout(context); panel.setGravity(Gravity.CENTER_VERTICAL);
                panel.setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 12));
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
                panel.addView(slider, new LinearLayout.LayoutParams(0, dp(context, 36), 1));
                level = new TextView(context); level.setTextColor(Color.WHITE); level.setTextSize(16);
                level.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                panel.addView(level, new LinearLayout.LayoutParams(dp(context, 58), -1));
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
                WindowManager.LayoutParams position = new WindowManager.LayoutParams(dp(context, 360), -2,
                        context instanceof AccessibilityService ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                                : WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT);
                position.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL; position.y = dp(context, 30);
                if (context instanceof Activity) position.token = ((Activity) context).getWindow().getDecorView().getWindowToken();
                window.addView(panel, position);
            }
            update(audio); scheduleHide(); return true;
        } catch (RuntimeException unavailable) { hide(); return false; }
    }
    private static void update(AudioManager audio) {
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int value = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        slider.setMax(max); slider.setProgress(value);
        level.setText(Math.round(value * 100f / max) + "%");
    }
    private static void scheduleHide() { HANDLER.removeCallbacks(HIDE); HANDLER.postDelayed(HIDE, 1600); }
    static void hide(Context context) { if (owner == context) hide(); }
    private static void hide() {
        HANDLER.removeCallbacks(HIDE);
        if (panel != null && window != null) try { window.removeViewImmediate(panel); }
        catch (RuntimeException detached) { /* Window already removed by Android. */ }
        owner = null; panel = null; window = null; slider = null; level = null;
    }
    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
