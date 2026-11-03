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
        if (args.length != 2 || !args[0].matches("/dev/input/event[0-9]+")) {
            System.err.println("Arguments: /dev/input/eventN readiness-file");
            System.exit(2); return;
        }
        try { run(args[0], new File(args[1])); }
        catch (Exception error) { error.printStackTrace(System.err); System.exit(1); }
    }

    private static void run(String device, File ready) throws Exception {
        NativeInjector injector = new NativeInjector();
        Process reader = new ProcessBuilder("getevent", "-lt", device).redirectErrorStream(true).start();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean();
        FastVolumeController controller = new FastVolumeController(SystemClock::uptimeMillis,
                (task, delay) -> {
                    java.util.concurrent.ScheduledFuture<?> future = scheduler.schedule(task, delay, TimeUnit.MILLISECONDS);
                    return () -> future.cancel(false);
                }, injector, error -> { failed.set(true); error.printStackTrace(System.err); reader.destroy(); });
        Thread shutdown = new Thread(() -> {
            controller.stop(); reader.destroy(); scheduler.shutdownNow(); ready.delete();
        }, "volume-helper-stop");
        Runtime.getRuntime().addShutdownHook(shutdown);
        try (BufferedReader events = new BufferedReader(new InputStreamReader(reader.getInputStream(), StandardCharsets.UTF_8))) {
            try (FileOutputStream marker = new FileOutputStream(ready)) { marker.write(1); }
            System.out.println("Fast volume helper ready: hold=300ms, requested repeat=10ms, step=1, one persistent injector.");
            FastVolumeController.Parser parser = new FastVolumeController.Parser(controller);
            String line;
            while (!failed.get() && (line = events.readLine()) != null) {
                if (line.contains("could not") || line.contains("Permission denied")) System.err.println(line);
                parser.line(line);
            }
        } finally {
            controller.stop(); reader.destroy(); scheduler.shutdownNow(); ready.delete();
            try { Runtime.getRuntime().removeShutdownHook(shutdown); }
            catch (IllegalStateException shuttingDown) { /* Shutdown hook is already doing the same cleanup. */ }
        }
        if (failed.get()) throw new IllegalStateException("Volume injection stopped after failure");
    }
}
