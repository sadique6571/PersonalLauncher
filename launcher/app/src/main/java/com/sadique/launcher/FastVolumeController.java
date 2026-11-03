package com.sadique.launcher;

/** Timer/state logic shared by the shell injector and plain JVM regression tests. */
final class FastVolumeController {
    static final long HOLD_MS = 300, REPEAT_MS = 10, MAX_HOLD_MS = 30_000;
    interface Clock { long now(); }
    interface Cancel { void cancel(); }
    interface Timer { Cancel post(Runnable task, long delay); }
    interface Injector { void pair(int keyCode) throws Exception; }
    interface Errors { void failed(Exception error); }
    private final Clock clock;
    private final Timer timer;
    private final Injector injector;
    private final Errors errors;
    private Cancel pending;
    private long generation, deadline;
    private int held;

    FastVolumeController(Clock clock, Timer timer, Injector injector, Errors errors) {
        this.clock = clock; this.timer = timer; this.injector = injector; this.errors = errors;
    }
    synchronized void down(int key) {
        if ((key != 24 && key != 25) || held == key) return;
        stop(); held = key; deadline = clock.now() + MAX_HOLD_MS;
        long press = generation;
        pending = timer.post(() -> tick(press), HOLD_MS);
    }
    synchronized void up(int key) { if (held == key) stop(); }
    synchronized void unknownUp() { stop(); }
    synchronized void stop() {
        held = 0; generation++;
        if (pending != null) pending.cancel();
        pending = null;
    }
    private synchronized void tick(long press) {
        if (held == 0 || generation != press) return;
        if (clock.now() >= deadline) { stop(); return; }
        try { injector.pair(held); }
        catch (Exception failure) { stop(); errors.failed(failure); return; }
        if (held != 0 && generation == press) pending = timer.post(() -> tick(press), REPEAT_MS);
    }

    /** Single-device getevent frames: an MSC usage is valid only until the next SYN_REPORT. */
    static final class Parser {
        private final FastVolumeController controller;
        private int scan; // 0=no usage, -1=unrelated usage, otherwise mapped volume key.
        Parser(FastVolumeController controller) { this.controller = controller; }
        void line(String line) {
            String[] words = line.trim().split("\\s+");
            for (int i = 0; i + 2 < words.length; i++) {
                String type = words[i], code = words[i + 1], value = words[i + 2];
                if ("EV_MSC".equals(type) && "MSC_SCAN".equals(code)) {
                    scan = "000c0080".equalsIgnoreCase(value) ? 24
                            : "000c0081".equalsIgnoreCase(value) ? 25 : -1;
                    return;
                }
                if ("EV_SYN".equals(type) && "SYN_REPORT".equals(code)) { scan = 0; return; }
                if (!"EV_KEY".equals(type)) continue;
                int key = "KEY_VOLUMEUP".equals(code) ? 24 : "KEY_VOLUMEDOWN".equals(code) ? 25
                        : "KEY_UNKNOWN".equals(code) ? scan : -1;
                if (key == 24 || key == 25) {
                    if ("DOWN".equals(value)) controller.down(key);
                    else if ("UP".equals(value)) controller.up(key);
                    else if ("REPEAT".equals(value)) controller.stop();
                } else if (key == 0 && "UP".equals(value)) controller.unknownUp();
                else if (key == 0 && "REPEAT".equals(value)) controller.stop();
                scan = 0;
                return;
            }
        }
    }
}
