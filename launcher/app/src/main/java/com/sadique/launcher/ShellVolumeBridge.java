package com.sadique.launcher;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Explicit ADB/app_process entry point; never started by the installed launcher. */
public final class ShellVolumeBridge {
    private ShellVolumeBridge() { }

    @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
    static final class NativeInjector implements FastVolumeController.Injector {
        private final Object manager;
        private final Method inject;
        NativeInjector() throws Exception {
            Object found = null;
            Method method = null;
            Exception failure = null;
            for (String name : new String[]{"android.hardware.input.InputManagerGlobal", "android.hardware.input.InputManager"}) {
                try {
                    Class<?> type = Class.forName(name);
                    Object instance = type.getMethod("getInstance").invoke(null);
                    Method candidate = type.getMethod("injectInputEvent", InputEvent.class, int.class);
                    found = instance; method = candidate; break;
                } catch (ReflectiveOperationException unavailable) { failure = unavailable; }
            }
            if (found == null) throw new IllegalStateException("Native input injection unavailable", failure);
            manager = found; inject = method;
        }
        private void send(KeyEvent event) throws Exception {
            // ASYNC avoids waiting for accessibility/app callbacks; a paired UP is queued immediately.
            if (!Boolean.TRUE.equals(inject.invoke(manager, event, 0))) {
                throw new IllegalStateException("Native input injection rejected");
            }
        }
        @Override public void pair(int key) throws Exception {
            long down = SystemClock.uptimeMillis();
            KeyEvent press = new KeyEvent(down, down, KeyEvent.ACTION_DOWN, key, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
            send(press);
            // Complete the pair even if shutdown/release arrived while DOWN was accepted.
            send(new KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, key, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD));
        }
    }

    public static void main(String[] args) {
        if (android.os.Process.myUid() != 2000) {
            System.err.println("Run this helper with adb shell app_process (shell UID required).");
            System.exit(2); return;
        }
        if (args.length < 2 || args.length > 4 || !args[0].matches("/dev/input/event[0-9]+")) {
            System.err.println("Arguments: /dev/input/eventN readiness-file [repeat-ms: 50..1000] [system|launcher]");
            System.exit(2); return;
        }
        try {
            long repeatMs = args.length >= 3 ? Long.parseLong(args[2]) : FastVolumeController.REPEAT_MS;
            if (repeatMs < 50 || repeatMs > 1000) throw new IllegalArgumentException("Repeat wait must be 50..1000 ms");
            String panel = args.length == 4 ? args[3] : "system";
            if (!panel.equals("system") && !panel.equals("launcher")) throw new IllegalArgumentException("Panel must be system or launcher");
            run(args[0], new File(args[1]), repeatMs, panel.equals("launcher"));
        }
        catch (Exception error) { error.printStackTrace(System.err); System.exit(1); }
    }

    private static void run(String device, File ready, long repeatMs, boolean launcherPanel) throws Exception {
        NativeInjector injector = new NativeInjector();
        ShellVolumePanel panel = launcherPanel ? new ShellVolumePanel() : null;
        Process reader = new ProcessBuilder("getevent", "-lt", device).redirectErrorStream(true).start();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean();
        FastVolumeController controller = new FastVolumeController(SystemClock::uptimeMillis,
                (task, delay) -> {
                    java.util.concurrent.ScheduledFuture<?> future = scheduler.schedule(task, delay, TimeUnit.MILLISECONDS);
                    return () -> future.cancel(false);
                }, injector, error -> { failed.set(true); error.printStackTrace(System.err); reader.destroy(); }, repeatMs);
        Thread shutdown = new Thread(() -> {
            controller.stop(); reader.destroy(); scheduler.shutdownNow(); ready.delete();
            if (panel != null) panel.close();
        }, "volume-helper-stop");
        Runtime.getRuntime().addShutdownHook(shutdown);
        try (BufferedReader events = new BufferedReader(new InputStreamReader(reader.getInputStream(), StandardCharsets.UTF_8))) {
            if (panel != null) panel.install();
            try (FileOutputStream marker = new FileOutputStream(ready)) { marker.write(1); }
            System.out.println("Fast volume helper ready: hold=300ms, requested repeat=" + repeatMs + "ms, step=1, one persistent injector.");
            FastVolumeController.Parser parser = new FastVolumeController.Parser(controller);
            String line;
            while (!failed.get() && (line = events.readLine()) != null) {
                if (line.contains("could not") || line.contains("Permission denied")) System.err.println(line);
                parser.line(line);
            }
        } finally {
            controller.stop(); reader.destroy(); scheduler.shutdownNow(); ready.delete();
            if (panel != null) panel.close();
            try { Runtime.getRuntime().removeShutdownHook(shutdown); }
            catch (IllegalStateException shuttingDown) { /* Shutdown hook is already doing the same cleanup. */ }
        }
        if (failed.get()) throw new IllegalStateException("Volume injection stopped after failure");
    }
}
