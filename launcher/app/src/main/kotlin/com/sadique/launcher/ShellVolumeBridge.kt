package com.sadique.launcher

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Explicit ADB/app_process entry point; never started by the installed launcher. */
class ShellVolumeBridge private constructor() {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (android.os.Process.myUid() != 2000) {
                System.err.println("Run this helper with adb shell app_process (shell UID required).")
                System.exit(2)
                return
            }
            if (args.size < 2 || args.size > 4 || (args[0] != "auto" && !args[0].matches("/dev/input/event[0-9]+".toRegex()))) {
                System.err.println("Arguments: auto|/dev/input/eventN readiness-file [repeat-ms: 50..1000] [system|launcher]")
                System.exit(2)
                return
            }
            try {
                val repeatMs = if (args.size >= 3) args[2].toLong() else FastVolumeController.REPEAT_MS
                if (repeatMs < 50 || repeatMs > 1000) throw IllegalArgumentException("Repeat wait must be 50..1000 ms")
                val panel = if (args.size == 4) args[3] else "system"
                if (panel != "system" && panel != "launcher") throw IllegalArgumentException("Panel must be system or launcher")
                run(args[0], File(args[1]), repeatMs, panel == "launcher")
            } catch (error: Exception) {
                error.printStackTrace(System.err)
                System.exit(1)
            }
        }

        private fun run(device: String, ready: File, repeatMs: Long, launcherPanel: Boolean) {
            val injector = NativeInjector()
            val panel = if (launcherPanel) ShellVolumePanel() else null
            val input = if (device == "auto") ProcessBuilder("getevent", "-lt") else ProcessBuilder("getevent", "-lt", device)
            val reader = input.redirectErrorStream(true).start()
            val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
            val stopRequest = File(ready.parentFile, "stop-request")
            val graceful = File(ready.parentFile, "graceful-stop")
            val restoreFailure = File(ready.parentFile, "restore-failed")
            val failed = java.util.concurrent.atomic.AtomicBoolean()
            val controller = FastVolumeController(
                { SystemClock.uptimeMillis() },
                { task, delay ->
                    val future = scheduler.schedule(task, delay, TimeUnit.MILLISECONDS)
                    FastVolumeController.Cancel { future.cancel(false) }
                },
                injector,
                { error ->
                    failed.set(true)
                    error.printStackTrace(System.err)
                    reader.destroy()
                },
                repeatMs
            )
            // Android SIGTERM need not execute Java shutdown hooks. Exit through the reader/finally
            // path when the wrapper requests stop, so the saved volume controller is restored first.
            scheduler.scheduleWithFixedDelay({
                if (stopRequest.exists()) {
                    controller.stop()
                    reader.destroy()
                }
            }, 100, 100, TimeUnit.MILLISECONDS)
            val shutdown = Thread({
                controller.stop()
                reader.destroy()
                scheduler.shutdownNow()
                ready.delete()
                panel?.close()
                graceful.delete()
            }, "volume-helper-stop")
            Runtime.getRuntime().addShutdownHook(shutdown)
            try {
                BufferedReader(InputStreamReader(reader.inputStream, StandardCharsets.UTF_8)).use { events ->
                    panel?.install()
                    FileOutputStream(graceful).use { marker -> marker.write(1) }
                    FileOutputStream(ready).use { marker -> marker.write(1) }
                    println("Fast volume helper ready: hold=300ms, requested repeat=" + repeatMs + "ms, step=1, one persistent injector.")
                    val parser = FastVolumeController.Parser(controller)
                    val router = if (device == "auto") VolumeInputRouter(controller) else null
                    while (true) {
                        if (failed.get()) break
                        val line = events.readLine() ?: break
                        if (line.contains("could not") || line.contains("Permission denied")) System.err.println(line)
                        if (router != null) router.line(line) else parser.line(line)
                    }
                }
            } finally {
                controller.stop()
                reader.destroy()
                scheduler.shutdownNow()
                ready.delete()
                panel?.close()
                if (panel != null && panel.restoreFailed()) {
                    FileOutputStream(restoreFailure).use { marker -> marker.write(1) }
                }
                graceful.delete()
                stopRequest.delete()
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdown)
                } catch (shuttingDown: IllegalStateException) {
                    /* Shutdown hook is already doing the same cleanup. */
                }
            }
            if (failed.get()) throw IllegalStateException("Volume injection stopped after failure")
        }
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    internal class NativeInjector : FastVolumeController.Injector {
        private val manager: Any
        private val inject: Method

        init {
            var found: Any? = null
            var method: Method? = null
            var failure: Exception? = null
            for (name in arrayOf("android.hardware.input.InputManagerGlobal", "android.hardware.input.InputManager")) {
                try {
                    val type = Class.forName(name)
                    val instance = type.getMethod("getInstance").invoke(null)
                    val candidate = type.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
                    found = instance
                    method = candidate
                    break
                } catch (unavailable: ReflectiveOperationException) {
                    failure = unavailable
                }
            }
            if (found == null || method == null) throw IllegalStateException("Native input injection unavailable", failure)
            manager = found
            inject = method
        }

        private fun send(event: KeyEvent) {
            // ASYNC avoids waiting for accessibility/app callbacks; a paired UP is queued immediately.
            if (inject.invoke(manager, event, 0) != true) {
                throw IllegalStateException("Native input injection rejected")
            }
        }

        @Throws(Exception::class)
        override fun pair(key: Int) {
            val down = SystemClock.uptimeMillis()
            val press = KeyEvent(
                down, down, KeyEvent.ACTION_DOWN, key, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD
            )
            send(press)
            // Complete the pair even if shutdown/release arrived while DOWN was accepted.
            send(
                KeyEvent(
                    down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, key, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD
                )
            )
        }
    }
}
