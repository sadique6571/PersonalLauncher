package com.sadique.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import java.io.File

/** Disk cache for processed app-card bitmaps, shared by Home and boot pre-warm. */
class IconCache(context: Context) {
    private val app = context.applicationContext
    private val directory = File(app.cacheDir, "icon_cards").apply { mkdirs() }
    private val prefs = ThemePreferences.get(app)
    private val marked = ThemeStyle.custom(prefs) || ThemeStyle.id(prefs) == ThemeStyle.GLASS
    private val logoSize = prefs.getInt("logo_size", 1).coerceIn(0, 2)

    private fun stem(pkg: String, version: Long) =
        "${safe(pkg)}_${version}_s$logoSize" + if (marked) "_mark_v3" else ""

    private fun file(pkg: String, version: Long) = File(directory, stem(pkg, version) + ".png")
    private fun accentKey(pkg: String, version: Long) = "icon_accent_" + stem(pkg, version)

    fun mark(): Boolean = marked
    fun logoScale(): Float = when (logoSize) {
        0 -> .8f
        2 -> 1.2f
        else -> 1f
    }

    fun load(pkg: String, version: Long): Bitmap? {
        if (version == 0L) return null
        val file = file(pkg, version)
        if (!file.exists()) {
            prune(pkg, version)
            return null
        }
        return BitmapFactory.decodeFile(file.path).also { if (it == null) file.delete() }
    }

    fun accent(pkg: String, version: Long, fallback: Int): Int = prefs.getInt(accentKey(pkg, version), fallback)

    fun save(pkg: String, version: Long, card: Bitmap?, accent: Int) {
        if (version == 0L || card == null) return
        var temporary: File? = null
        try {
            val file = File.createTempFile("artwork-", ".tmp", directory)
            temporary = file
            file.outputStream().use {
                if (!card.compress(Bitmap.CompressFormat.PNG, 100, it)) return
                it.flush()
            }
            if (!file.renameTo(file(pkg, version))) return
            prefs.edit().putInt(accentKey(pkg, version), accent).apply()
            prune(pkg, version)
        } catch (_: Exception) {
            // Failed cache writes must not prevent the live icon from appearing.
        } finally {
            temporary?.delete()
        }
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun prune(pkg: String, version: Long) {
        val prefix = safe(pkg) + "_"
        val keep = prefix + version + "_s"
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.name.startsWith(prefix) && !file.name.startsWith(keep)) file.delete()
        }
        for (key in prefs.all.keys.toSet()) {
            if (key.startsWith("icon_accent_" + prefix) && !key.startsWith("icon_accent_" + keep)) {
                prefs.edit().remove(key).apply()
            }
        }
    }

    companion object {
        private val unsafe = Regex("[^a-zA-Z0-9._-]")
        private fun safe(pkg: String) = pkg.replace(unsafe, "_")

        @JvmStatic
        fun version(pm: PackageManager, pkg: String): Long {
            return try {
                pm.getPackageInfo(pkg, 0).lastUpdateTime
            } catch (_: Exception) {
                0
            }
        }

        @JvmStatic
        fun warm(context: Context) {
            val app = context.applicationContext
            val pm = app.packageManager
            val cache = IconCache(app)
            fun launchables(category: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            val found = launchables(Intent.CATEGORY_LEANBACK_LAUNCHER) + launchables(Intent.CATEGORY_LAUNCHER)
            val packages = HashSet<String>()
            val deadline = System.currentTimeMillis() + 6000
            for (info in found) {
                if (Thread.currentThread().isInterrupted || System.currentTimeMillis() > deadline) break
                val pkg = info.activityInfo.packageName
                if (pkg == app.packageName || !packages.add(pkg)) continue
                val version = version(pm, pkg)
                if (cache.load(pkg, version) != null) continue
                try {
                    val original = info.loadIcon(pm)
                    val logo = when (pkg) {
                        "com.google.android.youtube.tv", "com.google.android.youtube" -> app.getDrawable(R.drawable.logo_youtube) ?: original
                        "com.netflix.ninja", "com.netflix.mediaclient" -> app.getDrawable(R.drawable.logo_netflix) ?: original
                        else -> original
                    }
                    val artwork = AppArtwork.create(app.resources,
                        if (cache.mark()) DemoBrandMark.choose(pkg, logo) else logo, cache.logoScale(), cache.mark())
                    cache.save(pkg, version, (artwork.image as BitmapDrawable).bitmap, artwork.accent)
                } catch (_: RuntimeException) {
                    // The activity retries packages that disappeared while warming.
                }
            }
        }
    }
}
