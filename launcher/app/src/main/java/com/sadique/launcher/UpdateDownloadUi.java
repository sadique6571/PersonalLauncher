package com.sadique.launcher;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;

/** One non-focusable capsule in Home's own window. No overlay permission, polling or activity retention. */
final class UpdateDownloadUi {
    private final Handler main = new Handler(Looper.getMainLooper());
    private WeakReference<View> capsule = new WeakReference<>(null);
    private WeakReference<DownloadCapsule> body = new WeakReference<>(null);
    private String message = "Checking GitHub";
    private int percent = -1;
    private boolean posted, closed, completing;
    private final Runnable render = () -> {
        synchronized (this) {
            posted = false;
            DownloadCapsule progress = body.get();
            if (closed || progress == null) return;
            progress.progress(message,percent);
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
            DownloadCapsule shell = new DownloadCapsule(host);
            shell.setKeepScreenOn(true);
            int width = Math.min(dp(host, 310), host.getResources().getDisplayMetrics().widthPixels - dp(host, 32));
            FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(width, dp(host, 48), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            position.topMargin = dp(host, 12); ((FrameLayout) content).addView(shell, position);
            capsule = new WeakReference<>(shell); body = new WeakReference<>(shell);
            render.run();
        }
    }
    synchronized void report(String message, int percent) {
        if (closed || completing || (this.message.equals(message) && this.percent == percent)) return;
        this.message = message; this.percent = percent;
        // Coalesce fast network chunks to one UI update, keeping disk writes out of progress reporting.
        if (!posted && body.get() != null) { posted = true; main.postDelayed(render, 80); }
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
        capsule.clear(); body.clear();
    }
    private static int dp(Activity host, int value) { return GlassSettings.dp(host, value); }
}

