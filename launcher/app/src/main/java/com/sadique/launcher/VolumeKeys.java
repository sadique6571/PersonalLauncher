package com.sadique.launcher;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;

/** Shared held-volume controller for the remote helpers and the launcher volume slider. */
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
        if (!canHandle(event)) return false;
        process(context, event, true); return true;
    }
    /** Own the tap and held repeat, suppressing the system OSD in favour of our slider. */
    static boolean control(Context context, KeyEvent event) {
        if (!canHandle(event)) return false;
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio == null || audio.isVolumeFixed()) return false;
        process(context, event, true, true);
        return true;
    }
    private static boolean canHandle(KeyEvent event) {
        // The shell helper injects these pairs through Android so its controller owns their panel.
        return direction(event.getKeyCode()) != 0
                && !(event.getDeviceId() == KeyCharacterMap.VIRTUAL_KEYBOARD && event.getScanCode() == 0);
    }
    private static void process(Context context, KeyEvent event, boolean consume) {
        process(context, event, consume, false);
    }
    private static void process(Context context, KeyEvent event, boolean consume, boolean initialStep) {
        android.util.Log.d("PersonalVolume", context.getClass().getSimpleName()
                + " key=" + event.getKeyCode() + " scan=" + event.getScanCode()
                + " action=" + event.getAction() + " repeat=" + event.getRepeatCount()
                + " initialStep=" + initialStep);
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
                event.getAction(), event.getRepeatCount(), event.isCanceled(), consume, initialStep,
                () -> {
                    int flags = initialStep ? 0 : AudioManager.FLAG_SHOW_UI;
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction(event.getKeyCode()), flags);
                    if (initialStep && !VolumeSlider.show(context, audio)) {
                        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
                    }
                },
                () -> audio.getStreamVolume(AudioManager.STREAM_MUSIC));
    }
    static void stop() { if (repeater != null) repeater.stop(); }
    interface Timer { void post(Runnable task, long delay); void cancel(Runnable task); }
    /** Process-wide state deduplicates both accessibility services, including repeated DOWN(count=0). */
    static final class Repeater {
        private final Timer timer;
        private int device, key;
        private long down;
        private boolean active;
        private int lastVolume;
        private Runnable tick, limit;
        Repeater(Timer timer) { this.timer = timer; }
        void event(int device, int key, long down, long time, int action, int repeat, boolean canceled,
                boolean consume, Runnable adjust, java.util.function.IntSupplier readVolume) {
            event(device, key, down, time, action, repeat, canceled, consume, false, adjust, readVolume);
        }
        void event(int device, int key, long down, long time, int action, int repeat, boolean canceled,
                boolean consume, boolean initialStep, Runnable adjust, java.util.function.IntSupplier readVolume) {
            boolean same = active && this.device == device && this.key == key && this.down == down;
            if (action == KeyEvent.ACTION_UP || canceled) { if (same) stop(); return; }
            if (action != KeyEvent.ACTION_DOWN) return;
            // A repeat event alone is not proof the firmware applied it after accessibility filtering.
            if (repeat > 0) return;
            if (same) return; // The second helper must not restart the 500ms delay.
            stop(); active = true;
            if (initialStep) adjust.run();
            lastVolume = readVolume.getAsInt();
            this.device = device; this.key = key; this.down = down;
            tick = new Runnable() {
                public void run() {
                    if (!active || tick != this) return;
                    try {
                        int currentVolume = readVolume.getAsInt();
                        if (consume || currentVolume == lastVolume) adjust.run();
                        lastVolume = readVolume.getAsInt();
                    }
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
