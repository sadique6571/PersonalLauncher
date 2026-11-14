package com.sadique.launcher

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BooleanSupplier

/** Opt-in boot recovery. No root, remote hosts, port scanning or permanent background polling. */
internal class VolumeAutoStart private constructor() {
    companion object {
        private const val PREFS = "volume_auto"
        private val MAIN = Handler(Looper.getMainLooper())
        private val WORK: ExecutorService = Executors.newSingleThreadExecutor()
        private val LIMITS: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
        private val BUSY = AtomicBoolean()
        private val HOME_PENDING = AtomicBoolean()
        private val GENERATION = AtomicInteger()
        private var done = false
        private var attempts = 0

        private fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(PREFS, 0)

        @JvmStatic
        fun status(c: Context): String = prefs(c).getString("status", "Off · one-time pairing required")

        private fun state(c: Context, value: String) {
            prefs(c).edit().putString("status", value).apply()
        }

        @JvmStatic
        fun enabled(c: Context): Boolean = prefs(c).getBoolean("enabled", false)

        @JvmStatic
        fun paired(c: Context): Boolean = prefs(c).getBoolean("paired", false)

        @JvmStatic
        fun busy(): Boolean = BUSY.get()

        @JvmStatic
        fun requestHome(c: Context) {
            HOME_PENDING.set(true)
            request(c, true, true)
        }

        @JvmStatic
        fun disable(c: Context) {
            prefs(c).edit().putBoolean("enabled", false).apply()
            GENERATION.incrementAndGet()
            state(c, "Auto-start off · current volume helper can keep running")
        }

        @JvmStatic
        fun enable(c: Context) {
            if (!paired(c)) {
                state(c, "Complete one-time pairing first")
                return
            }
            prefs(c).edit().putBoolean("enabled", true).apply()
            request(c, true)
        }

        @JvmStatic
        fun request(c: Context) {
            request(c, false)
        }

        private fun request(c: Context, force: Boolean) {
            request(c, force, false)
        }

        private fun request(source: Context, force: Boolean, homeOnly: Boolean) {
            // Same lock as the Java static synchronized method: the class object, shared with
            // the synchronized block that sets done below.
            synchronized(VolumeAutoStart::class.java) {
                val c = source.applicationContext
                if ((!enabled(c) && !HomeAutomation.enabled(c)) || !paired(c) || BUSY.get()) return
                if (homeOnly && !HomeAutomation.enabled(c)) return
                if (!force && (done || attempts >= 2)) return
                if (!BUSY.compareAndSet(false, true)) return
                HOME_PENDING.set(false)
                val volumeJob = enabled(c) && !homeOnly
                attempts++
                val generation = GENERATION.get()
                phase(c, volumeJob, "Waiting for startup/network readiness")
                MAIN.postDelayed({
                    WORK.execute {
                        try {
                            start(c, generation, volumeJob)
                            synchronized(VolumeAutoStart::class.java) { done = true }
                        } catch (error: Exception) {
                            if (generation == GENERATION.get() && (volumeJob || HomeAutomation.enabled(c))) {
                                val stage = if (volumeJob) status(c) else HomeAutomation.status(c)
                                android.util.Log.w("PersonalVolume", "Auto-start failed at $stage (${error.javaClass.simpleName})")
                                phase(c, volumeJob, "Auto-start failed at $stage (${error.javaClass.simpleName})")
                            }
                        } catch (error: LinkageError) {
                            if (generation == GENERATION.get() && (volumeJob || HomeAutomation.enabled(c))) {
                                val stage = if (volumeJob) status(c) else HomeAutomation.status(c)
                                android.util.Log.w("PersonalVolume", "Auto-start failed at $stage (${error.javaClass.simpleName})")
                                phase(c, volumeJob, "Auto-start failed at $stage (${error.javaClass.simpleName})")
                            }
                        } finally {
                            BUSY.set(false)
                            if (HOME_PENDING.getAndSet(false) && HomeAutomation.enabled(c)) requestHome(c)
                        }
                    }
                }, if (force) 0L else 10000L)
            }
        }

        @JvmStatic
        fun setup(source: Context, argument: String?): Bundle {
            val c = source.applicationContext
            HomeAutomation.enabled(c) // Initialize fresh-install consent before pairing changes the migration inputs.
            if (Build.VERSION.SDK_INT < 30) throw IllegalStateException("Local wireless pairing requires Android 11 or newer")
            val port: Int
            val code: String
            try {
                val parts = argument?.split(":") ?: emptyList()
                if (parts.size != 2 || !parts[0].matches("[0-9]{1,5}".toRegex()) || !parts[1].matches("[0-9]{6}".toRegex())) throw IllegalArgumentException()
                port = parts[0].toInt()
                if (port < 1 || port > 65535) throw IllegalArgumentException()
                code = parts[1]
            } catch (invalid: RuntimeException) {
                throw IllegalArgumentException("Use pairing-port:six-digit-code")
            }
            if (!BUSY.compareAndSet(false, true)) throw IllegalStateException("Setup already running; wait and retry")
            val generation = GENERATION.get()
            state(c, "Pairing this TV · keep its pairing dialog open")
            WORK.execute {
                try {
                    val manager = Manager(c)
                    try {
                        val relay = PairingRelay(port)
                        try {
                            if (!manager.pair("127.0.0.1", relay.port(), code)) throw IOException("Pairing rejected")
                            check(c, generation, false)
                            prefs(c).edit().putBoolean("paired", true).putBoolean("enabled", true).apply()
                            state(c, "Paired · auto-start enabled")
                        } finally {
                            relay.close()
                        }
                    } finally {
                        manager.close()
                    }
                } catch (error: Exception) {
                    if (generation == GENERATION.get()) state(c, "Pairing failed · keep the TV pairing dialog open and retry with its current code")
                } catch (error: LinkageError) {
                    if (generation == GENERATION.get()) state(c, "Pairing failed · keep the TV pairing dialog open and retry with its current code")
                } finally {
                    BUSY.set(false)
                    if (generation == GENERATION.get() && enabled(c) && paired(c)) request(c, true)
                }
            }
            val result = Bundle()
            result.putString("status", "Pairing started; no pairing code is saved")
            return result
        }

        private fun check(c: Context, generation: Int, requireEnabled: Boolean) {
            if (generation != GENERATION.get() || (requireEnabled && !enabled(c) && !HomeAutomation.enabled(c))) throw IOException("Cancelled")
        }

        private fun phase(c: Context, volumeJob: Boolean, value: String) {
            if (volumeJob) state(c, value) else HomeAutomation.state(c, value)
        }

        private fun start(c: Context, generation: Int, volumeJob: Boolean) {
            check(c, generation, true)
            val grant = c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
            if (!grant && !HomeAutomation.enabled(c))
                throw IOException("Existing settings grant is missing")
            val network = c.getSystemService(ConnectivityManager::class.java)
            for (i in 0 until 30) {
                if (network != null && network.activeNetwork != null) break
                check(c, generation, true)
                Thread.sleep(1000)
            }
            check(c, generation, true)
            phase(c, volumeJob, "Requesting wireless debugging on")
            // Only request ON. Never repeat the off/on toggle which disconnected this TV.
            if (grant && !Settings.Global.putInt(c.contentResolver, "adb_wifi_enabled", 1)) throw IOException("Wireless debugging request refused")
            for (attempt in 0 until 2) {
                check(c, generation, true)
                try {
                    phase(c, volumeJob, "Finding this TV's current connection port")
                    val ports = LocalAdbPort.discover(c)
                    check(c, generation, true)
                    var last: Exception? = null
                    for (port in ports) {
                        check(c, generation, true)
                        try {
                            val manager = Manager(c)
                            try {
                                phase(c, volumeJob, "Connecting to a discovered local TLS endpoint")
                                val tls = PairingRelay(port, true)
                                try {
                                    if (!manager.connect("127.0.0.1", tls.port())) throw IOException("Local TLS connection failed")
                                    check(c, generation, true)
                                    if (HomeAutomation.enabled(c)) {
                                        HomeAutomation.state(c, "Applying Home and remote setup")
                                        try {
                                            check(c, generation, true)
                                            val verified = homeShell(manager, tls, HomeSetupCommand.build(c.packageName, android.os.Process.myUid() / 100000, Build.VERSION.SDK_INT))
                                            if (HomeAutomation.enabled(c)) HomeAutomation.state(c, if (verified) "Default Home verified · permissions configured" else "System did not confirm Home setup · check the setup guide")
                                        } catch (failedHome: Exception) {
                                            if (HomeAutomation.enabled(c)) HomeAutomation.state(c, "Home setup failed (" + failedHome.javaClass.simpleName + ") · retry from setup")
                                            android.util.Log.w("PersonalHomeSetup", "Automatic Home setup failed", failedHome)
                                        }
                                    }
                                    if (volumeJob) {
                                        state(c, "Starting volume helper")
                                        val script = c.assets.open("fast-volume-bridge.sh").use { read(it, 32768) }
                                        val encoded = Base64.encodeToString(script.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                                        val command = VolumeStartupCommand.build(encoded, c.applicationInfo.sourceDir)
                                        shell(c, manager, command)
                                        state(c, "Started · automatic startup enabled")
                                    }
                                    return
                                } finally {
                                    tls.close()
                                }
                            } finally {
                                manager.close()
                            }
                        } catch (failure: Exception) {
                            last = failure
                            android.util.Log.w("PersonalVolume", "Local endpoint failed at " + status(c) + " (" + failure.javaClass.simpleName + "); trying next discovered endpoint")
                        }
                    }
                    if (last != null) throw last
                    throw IOException("No usable local TLS endpoint")
                } catch (failure: Exception) {
                    if (attempt == 1) throw failure
                    Thread.sleep(3000)
                }
            }
        }

        /** Only called after APK identity/version/digest verification; does not restart the volume helper. */
        @JvmStatic
        fun queueUpdate(c: Context, hash: String, allowed: BooleanSupplier): Boolean {
            if (!paired(c) || !BUSY.compareAndSet(false, true)) throw IOException("Recovery busy or pairing unavailable")
            try {
                val command = UpdateInstallCommand.build(c.packageName, android.os.Process.myUid() / 100000, hash)
                if (c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
                    Settings.Global.putInt(c.contentResolver, "adb_wifi_enabled", 1)
                var last: Exception? = null
                for (port in LocalAdbPort.discover(c)) {
                    try {
                        val manager = Manager(c)
                        try {
                            val relay = PairingRelay(port, true)
                            try {
                                if (!manager.connect("127.0.0.1", relay.port())) throw IOException("Local TLS unavailable")
                                if (!allowed.asBoolean) throw IOException("Home is no longer idle")
                                val deadline = LIMITS.schedule({ relay.close() }, 25, TimeUnit.SECONDS)
                                try {
                                    val stream = manager.openStream("shell:$command")
                                    try {
                                        val input = stream.openInputStream()
                                        try {
                                            return UpdateInstallCommand.OK == VolumeStartupResponse.awaitResult(input, 32768, UpdateInstallCommand.OK, UpdateInstallCommand.FAILED)
                                        } finally {
                                            input.close()
                                        }
                                    } finally {
                                        stream.close()
                                    }
                                } finally {
                                    deadline.cancel(false)
                                }
                            } finally {
                                relay.close()
                            }
                        } finally {
                            manager.close()
                        }
                    } catch (failed: Exception) {
                        last = failed
                    }
                }
                if (last != null) throw last
                return false
            } finally {
                BUSY.set(false)
                if (HOME_PENDING.getAndSet(false) && HomeAutomation.enabled(c)) requestHome(c)
            }
        }

        private fun homeShell(manager: Manager, relay: PairingRelay, command: String): Boolean {
            val deadline = LIMITS.schedule({ relay.close() }, 20, TimeUnit.SECONDS)
            try {
                val stream = manager.openStream("shell:$command")
                try {
                    val input = stream.openInputStream()
                    try {
                        return HomeSetupCommand.MARKER == VolumeStartupResponse.awaitResult(input, 32768, HomeSetupCommand.MARKER, HomeSetupCommand.FAILED)
                    } finally {
                        input.close()
                    }
                } finally {
                    stream.close()
                }
            } finally {
                deadline.cancel(false)
            }
        }

        private fun shell(c: Context, manager: Manager, command: String) {
            val deadline = LIMITS.schedule({
                try {
                    manager.disconnect()
                } catch (ignored: IOException) {
                }
            }, 20, TimeUnit.SECONDS)
            state(c, "Opening volume startup shell")
            try {
                val stream = manager.openStream("shell:$command")
                try {
                    val input = stream.openInputStream()
                    try {
                        state(c, "Waiting for helper readiness confirmation")
                        VolumeStartupResponse.awaitReady(input, 32768)
                        state(c, "Helper confirmed; closing startup channel")
                        android.util.Log.d("PersonalVolume", "Automatic helper readiness confirmed")
                    } finally {
                        input.close()
                    }
                } finally {
                    stream.close()
                }
            } finally {
                deadline.cancel(false)
            }
        }

        private fun read(input: InputStream, limit: Int): String {
            val out = ByteArrayOutputStream()
            val data = ByteArray(2048)
            while (true) {
                val count = input.read(data)
                if (count == -1) break
                if (out.size() + count > limit) throw IOException("Response too large")
                out.write(data, 0, count)
            }
            return out.toString(StandardCharsets.UTF_8.name())
        }
    }

    private class Manager(c: Context) : AbsAdbConnectionManager() {
        private val identity: VolumeAdbIdentity

        init {
            identity = VolumeAdbIdentity.load(c)
            setApi(Build.VERSION.SDK_INT)
            setTimeout(8, TimeUnit.SECONDS)
            setThrowOnUnauthorised(true)
        }

        override fun getPrivateKey(): PrivateKey = identity.key
        override fun getCertificate(): Certificate = identity.certificate
        override fun getDeviceName(): String = "PersonalLauncherVolume"

        // The library caches its TLS KeyManager. Keep that identity valid for future sessions.
        override fun close() {
            disconnect()
        }
    }
}
