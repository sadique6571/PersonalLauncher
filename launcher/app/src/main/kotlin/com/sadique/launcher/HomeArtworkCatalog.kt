package com.sadique.launcher

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import java.util.Locale
import java.util.concurrent.Executor

internal class HomeArtworkCatalog(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val tv: Boolean,
    private val appWorker: Executor,
    private val handler: Handler,
    private val fallbackAccent: Int,
    private val appsOnHome: () -> List<PersonalApp>?,
    private val tileVisible: (String) -> Boolean,
    private val renderApps: (List<PersonalApp>) -> Unit,
    private val updateTileIcon: (String, Drawable, Int) -> Unit,
    private val accentColor: () -> Int,
) {
    fun invalidate() { appGeneration++ }

    @Volatile
    private var appGeneration = 0
    fun load() {
        val generation = ++appGeneration
        val prior = appsOnHome()
        appWorker.execute {
            val found = ArrayList<ResolveInfo>()
            if (tv) found.addAll(host.packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0))
            found.addAll(host.packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            val packages = HashSet<String>()
            val apps = ArrayList<PersonalApp>()
            val previous = HashMap<String, PersonalApp>()
            val snapshot = prior
            if (snapshot != null) for (app in snapshot) previous[app.info.activityInfo.packageName] = app
            // Phase 1: labels only — the grid renders immediately with letter placeholders.
            for (info in found) {
                if (generation != appGeneration) return@execute
                val pkg = info.activityInfo.packageName
                if (pkg == host.packageName || !packages.add(pkg)) continue
                try {
                    val label = info.loadLabel(host.packageManager).toString()
                    val old = previous[pkg]
                    if (old != null && old.iconReady && old.name == label
                            && java.util.Objects.equals(old.info.activityInfo.applicationInfo.sourceDir, info.activityInfo.applicationInfo.sourceDir)) {
                        apps.add(PersonalApp(label, info, old.icon, old.accent, true))
                    } else {
                        apps.add(PersonalApp(label, info, letterIcon(label), fallbackAccent, false))
                    }
                } catch (removedDuringQuery: RuntimeException) {
                    // A package can be replaced or removed between queryIntentActivities and loading its resources.
                }
            }
            apps.sortWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
            handler.post {
                if (!host.isDestroyed && generation == appGeneration && !sameApps(appsOnHome(), apps)) renderApps(apps)
            }
        }
    }

    /** Only bound/visible tiles request artwork. Serial work and generation checks bound concurrency. */
    fun requestIcon(app: PersonalApp) {
        if (app.iconReady || app.iconRequest.pending(appGeneration) || host.isDestroyed) return
        val generation = appGeneration
        app.iconRequest.begin(generation)
        appWorker.execute {
            if (generation != appGeneration || host.isDestroyed) return@execute
            val pkg = app.info.activityInfo.packageName
            try {
                if (ThemeStyle.harmony(prefs)) {
                    val nativeIcon = app.info.loadIcon(host.packageManager)
                    handler.post {
                        if (!app.iconRequest.finish(generation)) return@post
                        if (!host.isDestroyed && generation == appGeneration)
                            updateTileIcon(pkg, nativeIcon, ThemeStyle.accent(prefs))
                    }
                    return@execute
                }
                val cache = IconCache(host)
                val pm = host.packageManager
                val version = IconCache.version(pm, pkg)
                val cachedCard = cache.load(pkg, version)
                val finished: Bitmap
                val finishedAccent: Int
                if (cachedCard != null) {
                    finished = cachedCard
                    finishedAccent = cache.accent(pkg, version, fallbackAccent)
                } else {
                    var logo = app.info.loadIcon(pm)
                    if (pkg == "com.google.android.youtube.tv" || pkg == "com.google.android.youtube") logo = host.getDrawable(R.drawable.logo_youtube)!!
                    if (pkg == "com.netflix.ninja" || pkg == "com.netflix.mediaclient") logo = host.getDrawable(R.drawable.logo_netflix)!!
                    if (cache.mark()) logo = DemoBrandMark.choose(pkg, logo)
                    val artwork = AppArtwork.create(host.resources, logo, cache.logoScale(), cache.mark())
                    finished = (artwork.image as BitmapDrawable).bitmap
                    finishedAccent = artwork.accent
                    cache.save(pkg, version, finished, finishedAccent)
                }
                handler.post {
                    if (!app.iconRequest.finish(generation)) return@post
                    if (!host.isDestroyed && generation == appGeneration) {
                        updateTileIcon(pkg, BitmapDrawable(host.resources, finished), finishedAccent)
                        trimIcons()
                    }
                }
            } catch (removed: RuntimeException) { handler.post { app.iconRequest.finish(generation) } }
        }
    }

    fun trimIcons() {
        val apps = appsOnHome() ?: return
        var bytes = 0L
        for (app in apps) {
            val icon = app.icon
            if (icon is BitmapDrawable) bytes += icon.bitmap.allocationByteCount
        }
        val budget = MemoryProfile.iconCacheBytes(MemoryProfile.effectiveTier(host, prefs))
        for (app in apps) {
            if (bytes <= budget) break
            if (!app.iconReady || tileVisible(app.info.activityInfo.packageName)) continue
            val icon = app.icon
            if (icon is BitmapDrawable) bytes -= icon.bitmap.allocationByteCount
            app.icon = null
            app.iconReady = false // Visible tiles will reload from disk; never recycle a drawn bitmap.
        }
    }

    /** Cheap letter placeholder so the grid is useful before icons finish. Runs on the worker thread. */
    private fun letterIcon(name: String): Drawable {
        val square = ThemeStyle.harmony(prefs)
        val bitmap = Bitmap.createBitmap(if (square) 96 else 160, if (square) 96 else 90, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (square) {
            paint.color = 0xff537d9a.toInt()
            canvas.drawRoundRect(5f, 5f, 91f, 91f, 21f, 21f, paint)
            paint.color = 0xfff5fbff.toInt()
            paint.textSize = 47f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            val letter = if (name.isEmpty()) "?" else name.substring(0, 1).uppercase(Locale.ROOT)
            canvas.drawText(letter, 48f, 64f, paint)
        } else {
            if (!ThemeStyle.custom(prefs)) canvas.drawColor(0xff0d2036.toInt())
            paint.color = if (ThemeStyle.custom(prefs)) accentColor() else 0x6640d9ff
            paint.textSize = 44f
            paint.textAlign = Paint.Align.CENTER
            val letter = if (name.isEmpty()) "?" else name.substring(0, 1).uppercase(Locale.ROOT)
            canvas.drawText(letter, 80f, 60f, paint)
        }
        return BitmapDrawable(host.resources, bitmap)
    }


    private fun sameApps(before: List<PersonalApp>?, after: List<PersonalApp>): Boolean {
        if (before == null || before.size != after.size) return false
        for (i in before.indices) {
            val a = before[i]
            val b = after[i]
            if (a.icon !== b.icon || a.name != b.name || a.info.activityInfo.packageName != b.info.activityInfo.packageName
                    || a.info.activityInfo.name != b.info.activityInfo.name) return false
        }
        return true
    }
}
