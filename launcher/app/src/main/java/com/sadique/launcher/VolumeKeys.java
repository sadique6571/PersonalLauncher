package com.sadique.launcher;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

/** Repair missing held-volume repeat without changing the initial native tap. */
final class VolumeKeys {
    static final long REPEAT_DELAY_MS = 500;
    static final long REPEAT_INTERVAL_MS = 60;
    static final long MAX_HOLD_MS = 30000;
    private static Handler handler;
    private static Repeater repeater;
    private VolumeKeys() { }
    static int direction(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) return AudioManager.ADJUST_RAISE;
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return AudioManager.ADJUST_LOWER;
        return 0;
    }
    static boolean changesPress(int action, int repeatCount) { return action != KeyEvent.ACTION_DOWN || repeatCount == 0; }
    /** Explicit consume_volume_keys compatibility mode keeps the firmware's initial step. */
    static boolean handle(Context context, KeyEvent event) {
        if (direction(event.getKeyCode()) == 0) return false;
        process(context, event, true); return true;
    }
    /** Normal mode passes the tap through; native repeat events cancel our fallback. */
    static void observe(Context context, KeyEvent event) {
        if (direction(event.getKeyCode()) != 0) process(context, event, false);
    }
    private static void process(Context context, KeyEvent event, boolean consume) {
        if (repeater == null) {
            handler = new Handler(Looper.getMainLooper());
            repeater = new Repeater(new Timer() {
                public void post(Runnable task, long delay) { handler.postDelayed(task, delay); }
                public void cancel(Runnable task) { handler.removeCallbacks(task); }
            });
        }
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio == null || audio.isVolumeFixed()) { stop(); return; }
        repeater.event(event.getDeviceId(), event.getKeyCode(), event.getDownTime(), event.getEventTime(),
                event.getAction(), event.getRepeatCount(), event.isCanceled(), consume,
                () -> audio.adjustVolume(direction(event.getKeyCode()), AudioManager.FLAG_SHOW_UI));
    }
    static void stop() { if (repeater != null) repeater.stop(); }
    interface Timer { void post(Runnable task, long delay); void cancel(Runnable task); }
    /** Process-wide state deduplicates both accessibility services, including repeated DOWN(count=0). */
    static final class Repeater {
        private final Timer timer;
        private int device, key;
        private long down;
        private boolean active, nativeRepeat;
        private Runnable tick, limit;
        Repeater(Timer timer) { this.timer = timer; }
        void event(int device, int key, long down, long time, int action, int repeat, boolean canceled,
                boolean consume, Runnable adjust) {
            boolean same = active && this.device == device && this.key == key && this.down == down;
            if (action == KeyEvent.ACTION_UP || canceled) { if (same) stop(); return; }
            if (action != KeyEvent.ACTION_DOWN) return;
            if (repeat > 0) {
                if (same && !consume) {
                    nativeRepeat = true;
                    if (tick != null) timer.cancel(tick);
                }
                return;
            }
            if (same) return; // The second helper must not restart the 500ms delay.
            stop(); active = true; nativeRepeat = false;
            this.device = device; this.key = key; this.down = down;
            tick = new Runnable() {
                public void run() {
                    if (!active || nativeRepeat || tick != this) return;
                    try { adjust.run(); }
                    catch (RuntimeException unavailable) { stop(); return; }
                    timer.post(this, REPEAT_INTERVAL_MS);
                }
            };
            limit = this::stop;
            timer.post(tick, Math.max(0, REPEAT_DELAY_MS - Math.max(0, time - down)));
            timer.post(limit, MAX_HOLD_MS);
        }
        void stop() {
            active = false;
            if (tick != null) timer.cancel(tick);
            if (limit != null) timer.cancel(limit);
            tick = null; limit = null;
        }
    }
}
