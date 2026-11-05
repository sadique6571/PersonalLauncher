package com.sadique.launcher;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** One non-focusable capsule in Home's own window. No overlay permission, polling or activity retention. */
final class UpdateDownloadUi {
    private final Handler main = new Handler(Looper.getMainLooper());
    private WeakReference<View> capsule = new WeakReference<>(null);
    private WeakReference<TextView> label = new WeakReference<>(null);
    private WeakReference<ProgressBar> bar = new WeakReference<>(null);
    private String message = "Checking GitHub";
    private int percent = -1;
    private boolean posted, closed, completing;
    private final Runnable render = () -> {
        synchronized (this) {
            posted = false;
            TextView text = label.get(); ProgressBar progress = bar.get();
            if (closed || text == null || progress == null) return;
            text.setText(message + (percent < 0 ? "" : " · " + percent + "%"));
            progress.setIndeterminate(percent < 0);
            if (percent >= 0) progress.setProgress(percent);
        }
    };
    void attach(Activity host) {
        if (host.isFinishing() || host.isDestroyed()) return;
        View content = host.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;
        synchronized (this) {
            if (closed || completing) return;
            View old = capsule.get();
            if (old != null && old.getParent() == content) { render.run(); return; }
            remove();
            LinearLayout shell = new LinearLayout(host); shell.setOrientation(LinearLayout.VERTICAL);
            shell.setGravity(Gravity.CENTER); shell.setPadding(dp(host, 16), dp(host, 9), dp(host, 16), dp(host, 10));
            GradientDrawable surface = GlassSettings.background(host, false); surface.setCornerRadius(dp(host, 26));
            shell.setBackground(surface); shell.setClipToOutline(true); shell.setElevation(dp(host, 8));
            shell.setFocusable(false); shell.setClickable(false);
            shell.setKeepScreenOn(true);
            TextView text = GlassSettings.label(host, "", 14, 0xffffffff); text.setGravity(Gravity.CENTER);
            text.setSingleLine(true); text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); shell.addView(text);
            ProgressBar progress = new ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xfff2f6ff));
            progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xfff2f6ff));
            progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x40ffffff));
            progress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams track = new LinearLayout.LayoutParams(-1, dp(host, 4)); track.topMargin = dp(host, 6);
            shell.addView(progress, track);
            int width = Math.min(dp(host, 310), host.getResources().getDisplayMetrics().widthPixels - dp(host, 32));
            FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(width, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            position.topMargin = dp(host, 12); ((FrameLayout) content).addView(shell, position);
            capsule = new WeakReference<>(shell); label = new WeakReference<>(text); bar = new WeakReference<>(progress);
            render.run();
        }
    }
    synchronized void report(String message, int percent) {
        if (closed || completing || (this.message.equals(message) && this.percent == percent)) return;
        this.message = message; this.percent = percent;
        // Coalesce fast network chunks to one UI update, keeping disk writes out of progress reporting.
        if (!posted && label.get() != null) { posted = true; main.postDelayed(render, 80); }
    }
    synchronized void complete(Runnable openInstaller) {
        if (closed || completing) return;
        completing = true; message = "Verified update"; percent = 100;
        main.post(() -> { render.run(); main.postDelayed(() -> {
            synchronized (this) { closed = true; remove(); }
            openInstaller.run();
        }, 180); });
    }
    synchronized void finish() {
        if (completing || closed) return;
        closed = true; main.removeCallbacks(render); main.post(this::remove);
    }
    private void remove() {
        View view = capsule.get();
        if (view != null && view.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) view.getParent()).removeView(view);
        capsule.clear(); label.clear(); bar.clear();
    }
    private static int dp(Activity host, int value) { return GlassSettings.dp(host, value); }
}
