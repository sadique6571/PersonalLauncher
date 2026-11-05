package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Local UI prototype only. No settings writes, hardware calls, saved values or volume changes. */
final class TvControlPreview {
    private TvControlPreview() { }

    static AlertDialog show(Activity host, boolean picture) {
        LinearLayout rows = new LinearLayout(host);
        rows.setOrientation(LinearLayout.VERTICAL);
        TextView notice = GlassSettings.label(host, "PREVIEW · TV settings are unchanged", 14, GlassSettings.themeAccent(host));
        notice.setPadding(0, 0, 0, GlassSettings.dp(host, 8));
        rows.addView(notice);
        TextView help = GlassSettings.label(host,
                "Sample values only. Left / Right adjusts a slider; Up / Down selects a row. Real TV controls will be connected after device testing.", 12, 0xffb7cbdf);
        help.setPadding(0, 0, 0, GlassSettings.dp(host, 12));
        rows.addView(help);
        List<SliderRow> sliders = new ArrayList<>();
        if (picture) {
            add(host, rows, sliders, "Backlight", 0, 100, 50);
            add(host, rows, sliders, "Brightness", 0, 100, 50);
            add(host, rows, sliders, "Contrast", 0, 100, 50);
            add(host, rows, sliders, "Colour", 0, 100, 50);
            add(host, rows, sliders, "Sharpness", 0, 100, 50);
            add(host, rows, sliders, "Hue", -50, 50, 0);
        } else {
            add(host, rows, sliders, "Bass", 0, 100, 50);
            add(host, rows, sliders, "Treble", 0, 100, 50);
            add(host, rows, sliders, "Balance", -50, 50, 0);
        }
        View reset = GlassSettings.row(host, "Reset preview values", "Only the samples on this page", R.drawable.ic_settings,
                GlassSettings.themeAccent(host), () -> {
                    for (SliderRow slider : sliders) slider.reset();
                });
        rows.addView(reset);
        AlertDialog dialog = GlassSettings.panel(host, picture ? "Picture · Preview" : "Sound · Preview", rows);
        View system = GlassSettings.row(host, picture ? "Open TV picture settings" : "Open TV sound settings",
                "Use the working system controls", picture ? R.drawable.ic_picture : R.drawable.ic_volume,
                GlassSettings.themeAccent(host), () -> {
                    try {
                        host.startActivity(SystemSettings.intent(host, picture ? Settings.ACTION_DISPLAY_SETTINGS : Settings.ACTION_SOUND_SETTINGS));
                        dialog.dismiss();
                    } catch (RuntimeException unavailable) {
                        Toast.makeText(host, R.string.not_available, Toast.LENGTH_SHORT).show();
                    }
                });
        rows.addView(system);
        // Deterministic vertical navigation; native Left/Right remains on the selected row.
        for (int i = 0; i < sliders.size(); i++) {
            SliderRow current = sliders.get(i);
            if (i > 0) current.setNextFocusUpId(sliders.get(i - 1).getId());
            current.setNextFocusDownId(i + 1 < sliders.size() ? sliders.get(i + 1).getId() : id(reset));
        }
        reset.setNextFocusUpId(sliders.get(sliders.size() - 1).getId());
        reset.setNextFocusDownId(id(system));
        system.setNextFocusUpId(reset.getId());
        system.setNextFocusDownId(system.getId());
        dialog.show();
        return dialog;
    }

    private static int id(View view) {
        if (view.getId() == View.NO_ID) view.setId(View.generateViewId());
        return view.getId();
    }
    private static void add(Activity host, LinearLayout rows, List<SliderRow> sliders,
            String title, int min, int max, int initial) {
        SliderRow row = new SliderRow(host, title, min, max, initial);
        sliders.add(row);
        rows.addView(row);
    }

    private static final class SliderRow extends LinearLayout {
        private final String title;
        private final int min, max, initial;
        private final SeekBar slider;
        private final TextView number;

        SliderRow(Activity host, String title, int min, int max, int initial) {
            super(host);
            this.title = title; this.min = min; this.max = max; this.initial = initial;
            setId(View.generateViewId());
            setOrientation(VERTICAL);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setPadding(GlassSettings.dp(host, 12), GlassSettings.dp(host, 8),
                    GlassSettings.dp(host, 12), GlassSettings.dp(host, 4));
            setBackground(GlassSettings.background(host, false));
            setOnFocusChangeListener((view, focus) -> setBackground(GlassSettings.background(host, focus)));
            LinearLayout header = new LinearLayout(host);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = GlassSettings.label(host, title, 15, 0xfff1f6ff);
            header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            number = GlassSettings.label(host, "", 15, GlassSettings.themeAccent(host));
            header.addView(number);
            header.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            addView(header);
            slider = new GlassSeekBar(host);
            slider.setMax(max - min);
            slider.setKeyProgressIncrement(1);
            slider.setProgressTintList(ColorStateList.valueOf(GlassSettings.themeAccent(host)));
            slider.setProgressBackgroundTintList(ColorStateList.valueOf(0xff344b61));
            slider.setThumbTintList(ColorStateList.valueOf(GlassSettings.themeAccent(host)));
            slider.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { display(); }
                @Override public void onStartTrackingTouch(SeekBar bar) { requestFocus(); }
                @Override public void onStopTrackingTouch(SeekBar bar) { }
            });
            boolean glass = TvGlassStyle.enabled(host.getSharedPreferences("personal_home", 0));
            LinearLayout.LayoutParams trackPosition = new LinearLayout.LayoutParams(-1, GlassSettings.dp(host, glass ? 36 : 32));
            trackPosition.topMargin = GlassSettings.dp(host, glass ? 6 : 0);
            trackPosition.bottomMargin = GlassSettings.dp(host, glass ? 6 : 0);
            addView(slider, trackPosition);
            LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(-1, -2);
            position.setMargins(0, GlassSettings.dp(host, 3), 0, GlassSettings.dp(host, 3));
            setLayoutParams(position);
            reset();
        }

        private int value() { return slider.getProgress() + min; }
        private void display() {
            int value = value();
            number.setText(value > 0 && min < 0 ? "+" + value : Integer.toString(value));
            setContentDescription(title + ", preview value " + value + ", TV unchanged");
        }
        void reset() { slider.setProgress(initial - min); display(); }
        private void adjust(int step) {
            slider.setProgress(Math.max(0, Math.min(max - min, slider.getProgress() + step)));
        }
        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                adjust(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            return super.onKeyUp(keyCode, event);
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(SeekBar.class.getName());
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, min, max, value()));
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
        }
        @Override public boolean performAccessibilityAction(int action, Bundle args) {
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                adjust(action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : -1);
                return true;
            }
            if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && args != null) {
                float requested = args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value());
                if (Float.isNaN(requested) || Float.isInfinite(requested)) return false;
                slider.setProgress(Math.max(0, Math.min(max - min, Math.round(requested) - min)));
                return true;
            }
            return super.performAccessibilityAction(action, args);
        }
    }
}

