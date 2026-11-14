package com.sadique.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HashSet
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground-only, bounded release checks and verified self-update. No permanent service. */
internal class GitHubUpdates private constructor() {
    companion object {
        private val WORK: ExecutorService = Executors.newSingleThreadExecutor()
        private val MAIN = Handler(Looper.getMainLooper())
        private val BUSY = AtomicBoolean()
        private val NATIVE_PENDING = AtomicBoolean()
        @Volatile
        private var active: Operation? = null
        private var awaitingApproval = false
        private const val SIX_HOURS = 6 * 60 * 60 * 1000L

        private class Operation(host: Activity, manual: Boolean) {
            @Volatile var owner: WeakReference<Activity> = WeakReference(host)
            @Volatile var manual: Boolean = manual
            val progress = UpdateDownloadUi()

            fun follow(host: Activity) {
                owner = WeakReference(host)
                manual = true
                progress.attach(host)
            }
        }

        @JvmStatic
        fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("github_updates", 0)

        @JvmStatic
        fun enabled(c: Context): Boolean = prefs(c).getBoolean("enabled", true)

        @JvmStatic
        fun manualActive(): Boolean {
            val operation = active
            return NATIVE_PENDING.get() || (operation != null && operation.manual)
        }

        @JvmStatic
        fun enabled(c: Context, value: Boolean) {
            prefs(c).edit().putBoolean("enabled", value).apply()
        }

        @JvmStatic
        fun status(c: Context): String = prefs(c).getString("status", "Checks GitHub while Home is open")

        private fun state(c: Context, value: String) {
            prefs(c).edit().putString("status", value).apply()
        }

        @JvmStatic
        fun readyFile(c: Context): File = File(c.filesDir, "verified-launcher-update.apk")

        @JvmStatic
        fun uri(c: Context): Uri = Uri.parse("content://" + c.packageName + ".updates/apk")

        @JvmStatic
        fun check(host: Activity, force: Boolean) {
            if ((!force && !enabled(host)) || host.isFinishing || host.isDestroyed || !host.hasWindowFocus()) return
            if (NATIVE_PENDING.get()) return
            val p = prefs(host)
            if (!force && !readyFile(host).isFile && System.currentTimeMillis() < p.getLong("next_check", 0)) return
            if (!BUSY.compareAndSet(false, true)) {
                val running = active
                if (force && running != null) running.follow(host)
                return
            }
            val c = host.applicationContext
            val operation = Operation(host, force)
            active = operation
            if (force) operation.progress.attach(host)
            WORK.execute {
                try {
                    val ready = readyFile(c)
                    if (ready.isFile) {
                        if (verify(c, ready)) {
                            install(operation, c)
                            return@execute
                        }
                        prefs(c).edit().putLong("checked_asset", prefs(c).getLong("ready_asset", 0)).apply()
                        ready.delete()
                        state(c, "Up to date")
                    }
                    if (!force && System.currentTimeMillis() < prefs(c).getLong("next_check", 0)) return@execute
                    state(c, "Checking GitHub")
                    val release = UpdateRelease.parse(
                        String(
                            fetch("https://api.github.com/repos/" + UpdateRelease.REPO + "/releases/latest", 512 * 1024),
                            StandardCharsets.UTF_8
                        )
                    )
                    prefs(c).edit().putLong("next_check", System.currentTimeMillis() + SIX_HOURS).apply()
                    if (release.id == prefs(c).getLong("checked_asset", 0)) {
                        state(c, "Up to date")
                        return@execute
                    }
                    state(c, "Downloading update from GitHub")
                    val part = File(c.filesDir, "launcher-update.part")
                    try {
                        operation.progress.report("Downloading update", 0)
                        download(release, part, operation.progress)
                        operation.progress.report("Verifying update", 99)
                        if (!verify(c, part)) {
                            prefs(c).edit().putLong("checked_asset", release.id).apply()
                            state(c, "Up to date")
                            return@execute
                        }
                        if (ready.exists() && !ready.delete()) throw IOException("Cannot replace update cache")
                        if (!part.renameTo(ready)) throw IOException("Cannot commit verified APK")
                        prefs(c).edit().putString("ready_hash", release.sha256).putLong("ready_asset", release.id)
                            .putInt("install_attempts", 0).putLong("native_offered", 0).putLong("installing_since", 0).apply()
                        state(c, "Verified update ready")
                        install(operation, c)
                    } finally {
                        part.delete()
                    }
                } catch (failure: Exception) {
                    readyFile(c).delete()
                    state(c, "Update check unavailable · automatic retry later")
                    prefs(c).edit().putLong("next_check", System.currentTimeMillis() + 15 * 60 * 1000L).apply()
                    android.util.Log.w("PersonalUpdates", "Update operation unavailable (" + failure.javaClass.simpleName + ")")
                } finally {
                    finish(operation, c)
                    active = null
                    BUSY.set(false)
                }
            }
        }

        private fun finish(operation: Operation, c: Context) {
            operation.progress.finish()
            if (operation.manual) MAIN.post {
                val host = operation.owner.get()
                val message = status(c)
                if (host != null && !host.isFinishing && !host.isDestroyed && host.hasWindowFocus()
                    && message != "Confirm update in Android installer" && message != "Opening Android installer"
                ) Toast.makeText(host, message, Toast.LENGTH_LONG).show()
            }
        }

        /** Resume only an installation the user explicitly requested and approved in Android settings. */
        @JvmStatic
        fun resume(host: Activity) {
            if (awaitingApproval && host.packageManager.canRequestPackageInstalls() && readyFile(host).isFile) {
                awaitingApproval = false
                MAIN.postDelayed({
                    if (!host.isFinishing && !host.isDestroyed && host.hasWindowFocus()) installReady(host)
                }, 300)
            }
            val operation = active
            if (operation != null && operation.manual) operation.follow(host)
        }

        @JvmStatic
        fun installReady(host: Activity) {
            if (host.isFinishing || host.isDestroyed || !host.hasWindowFocus() || NATIVE_PENDING.get()) return
            if (!readyFile(host).isFile) {
                check(host, true)
                return
            }
            if (!BUSY.compareAndSet(false, true)) {
                val running = active
                if (running != null) running.follow(host)
                return
            }
            val c = host.applicationContext
            val operation = Operation(host, true)
            active = operation
            operation.progress.attach(host)
            WORK.execute {
                try {
                    operation.progress.report("Verifying update", 99)
                    if (!verify(c, readyFile(c))) {
                        readyFile(c).delete()
                        state(c, "Already current")
                        return@execute
                    }
                    prefs(c).edit().putInt("install_attempts", 0).apply()
                    install(operation, c)
                } catch (failure: Exception) {
                    state(c, "Update not installed · retry from Updates")
                } finally {
                    finish(operation, c)
                    active = null
                    BUSY.set(false)
                }
            }
        }

        private fun idle(owner: WeakReference<Activity>, c: Context): Boolean {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                val task = java.util.concurrent.FutureTask<Boolean> { idle(owner, c) }
                MAIN.post(task)
                try {
                    return task.get(2, java.util.concurrent.TimeUnit.SECONDS)
                } catch (unavailable: Exception) {
                    task.cancel(false)
                    return false
                }
            }
            val host = owner.get()
            val audio = c.getSystemService(AudioManager::class.java)
            return host != null && !host.isFinishing && !host.isDestroyed && host.hasWindowFocus()
                && !GlassSettings.showing(host) && (audio == null || !audio.isMusicActive)
        }

        private fun install(operation: Operation, c: Context) {
            val owner = operation.owner
            val manual = operation.manual
            val p = prefs(c)
            if (!manual && (!enabled(c) || !idle(owner, c))) {
                state(c, "Verified update ready · installs when Home is idle")
                return
            }
            val since = p.getLong("installing_since", 0)
            if (System.currentTimeMillis() - since < 120_000L && since > 0) {
                state(c, "Update installer already opened")
                return
            }
            val attempts = p.getInt("install_attempts", 0)
            if (!manual && attempts >= 2) {
                state(c, "Update ready · automatic attempts paused; use Install update")
                return
            }
            val hash = hash(readyFile(c))
            if (hash != p.getString("ready_hash", "")) throw IOException("Verified APK changed")
            if (manual) {
                if (!NATIVE_PENDING.compareAndSet(false, true)) return
                state(c, "Opening Android installer")
                operation.progress.complete {
                    try {
                        openNative(owner, c, true)
                    } finally {
                        NATIVE_PENDING.set(false)
                    }
                }
                return
            }
            if (VolumeAutoStart.paired(c) && !VolumeAutoStart.busy()) {
                p.edit().putInt("install_attempts", attempts + 1).apply()
                c.grantUriPermission("com.android.shell", uri(c), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    state(c, "Installing verified update through saved pairing")
                    val queued = VolumeAutoStart.queueUpdate(c, hash) { idle(owner, c) && (manual || enabled(c)) }
                    if (queued) {
                        p.edit().putLong("installing_since", System.currentTimeMillis()).apply()
                        state(c, "Update installer started")
                        return
                    }
                } catch (unavailable: Exception) {
                    state(c, "Local pairing unavailable · Android installer fallback")
                } finally {
                    c.revokeUriPermission("com.android.shell", uri(c), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else if (VolumeAutoStart.busy()) {
                state(c, "Verified update ready · waiting for Home recovery")
                return
            }
            MAIN.post { openNative(owner, c, false) }
        }

        /** Called on main after the manual capsule has been removed. */
        private fun openNative(owner: WeakReference<Activity>, c: Context, manual: Boolean) {
            val host = owner.get()
            val p = prefs(c)
            if (host == null || host.isFinishing || host.isDestroyed || !host.hasWindowFocus()
                || (!manual && !enabled(c))
            ) {
                state(c, "Verified update ready · open Updates → Install update")
                return
            }
            val asset = p.getLong("ready_asset", 0)
            if (!manual && p.getLong("native_offered", 0) == asset) return
            if (!c.packageManager.canRequestPackageInstalls()) {
                state(c, "Update ready · approve Android installation")
                if (manual) try {
                    awaitingApproval = true
                    host.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.packageName)))
                } catch (unavailable: RuntimeException) {
                    awaitingApproval = false
                    Toast.makeText(host, "Approve this app in TV settings", Toast.LENGTH_LONG).show()
                }
                return
            }
            try {
                host.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri(c), "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
                p.edit().putLong("native_offered", asset).putLong("installing_since", System.currentTimeMillis()).apply()
                state(c, "Confirm update in Android installer")
            } catch (unavailable: RuntimeException) {
                state(c, "Android installer unavailable")
            }
        }

        private fun code(info: PackageInfo): Long {
            return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        }

        private fun signers(info: PackageInfo): Set<String> {
            val signatures: Array<Signature>? =
                if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) info.signingInfo.apkContentsSigners else info.signatures
            val result = HashSet<String>()
            if (signatures != null) for (signature in signatures) result.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())))
            return result
        }

        private fun verify(c: Context, apk: File): Boolean {
            val pm = c.packageManager
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val local = pm.getPackageInfo(c.packageName, flags)
            val archive: PackageInfo? = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            if (archive == null || c.packageName != archive.packageName) throw IOException("Wrong update package")
            val localSigners = signers(local)
            val archiveSigners = signers(archive)
            if (localSigners != archiveSigners || localSigners.isEmpty()) throw IOException("Wrong update signer")
            return UpdatePackagePolicy.accepts(local.packageName, archive.packageName, code(local), code(archive), localSigners, archiveSigners)
        }

        private fun hex(bytes: ByteArray): String {
            val out = StringBuilder()
            for (b in bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b.toInt() and 255))
            return out.toString()
        }

        @JvmStatic
        fun hash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(32768)
            FileInputStream(file).use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n == -1) break
                    digest.update(buffer, 0, n)
                }
            }
            return hex(digest.digest())
        }

        private fun connection(location: String): HttpURLConnection {
            var current = location
            for (redirects in 0 until 6) {
                val url = URL(current)
                val host = url.host
                if ("https" != url.protocol || url.userInfo != null || (url.port != -1 && url.port != 443)
                    || !(host == "api.github.com" || host == "github.com" || host == "release-assets.githubusercontent.com" || host == "objects.githubusercontent.com")
                ) throw IOException("Untrusted download host")
                val conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.setRequestProperty("User-Agent", "PersonalLauncher/7.0")
                conn.setRequestProperty("Accept", if (host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
                val code = conn.responseCode
                if (code == 200) return conn
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (code in 300..399 && next != null) {
                    current = URL(url, next).toString()
                    continue
                }
                throw IOException("GitHub response $code")
            }
            throw IOException("Too many redirects")
        }

        private fun fetch(location: String, limit: Int): ByteArray {
            val conn = connection(location)
            try {
                return conn.inputStream.use { input ->
                    ByteArrayOutputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            if (output.size() + n > limit) throw IOException("Response limit")
                            output.write(buffer, 0, n)
                        }
                        output.toByteArray()
                    }
                }
            } finally {
                conn.disconnect()
            }
        }

        private fun download(release: UpdateRelease, target: File, progress: UpdateDownloadUi) {
            val conn = connection(release.url)
            var size = 0L
            val until = System.currentTimeMillis() + 180_000
            try {
                conn.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            size += n
                            if (size > release.size || size > UpdateRelease.MAX_APK || System.currentTimeMillis() > until) throw IOException("APK limit")
                            output.write(buffer, 0, n)
                            progress.report("Downloading update", UpdateProgress.percent(size, release.size, false))
                        }
                        output.fd.sync()
                    }
                }
            } finally {
                conn.disconnect()
            }
            if (size != release.size || release.sha256 != hash(target)) throw IOException("APK integrity mismatch")
        }
    }
}
