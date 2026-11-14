package com.sadique.launcher

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer

/** Read-only, shared Android TV Watch Next entries. Never scrapes an app's private history. */
class ContinueWatchingView(private val host: Activity, private val focusTitle: Consumer<String>) : LinearLayout(host) {
    companion object {
        const val PERMISSION = "android.permission.READ_TV_LISTINGS"
        const val PERMISSION_REQUEST = 31
    }

    private val prefs: SharedPreferences = ThemePreferences.get(host)
    private val handler = Handler(Looper.getMainLooper())
    private val cards: LinearLayout
    private val heading: TextView
    private val explanation: TextView
    private val allow: Button
    private var worker: ExecutorService? = null
    private var artworkWorker: ExecutorService? = null
    private var query: CancellationSignal? = null
    private var running = false
    private var observing = false
    private val compact: Boolean = ThemeStyle.id(prefs) == ThemeStyle.GLASS
    private var focusedEntry: String? = null
    private var generation = 0
    private val refresh: Runnable = Runnable { reload() }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(refresh)
            handler.postDelayed(refresh, 750)
        }
    }

    init {
        orientation = VERTICAL
        setPadding(0, dp(if (compact) 8 else 22), 0, dp(if (compact) 8 else 12))
        heading = label("Continue watching", if (compact) 18 else 23)
        addView(heading)
        val scroll = TvHorizontalShelf(host)
        scroll.isHorizontalScrollBarEnabled = false
        cards = LinearLayout(host).apply {
            setPadding(0, dp(if (compact) 6 else 10), 0, dp(if (compact) 6 else 10))
        }
        scroll.addView(cards, LayoutParams(-2, -2))
        addView(scroll, LayoutParams(-1, -2))
        explanation = label("Only resume entries shared by supported TV apps appear here.", if (compact) 11 else 14)
        explanation.isFocusable = true
        addView(explanation)
        allow = Button(host).apply {
            text = "Allow TV Watch Next access"
            isAllCaps = false
            setOnClickListener { requestAccess() }
        }
        addView(allow, LayoutParams(-2, -2))
    }

    private fun dp(n: Int): Int = Math.round(n * resources.displayMetrics.density)

    private fun label(value: String, size: Int): TextView {
        return TextView(host).apply {
            text = value
            setTextSize(size.toFloat())
            setTextColor(ThemeStyle.foreground(ThemePreferences.get(host)))
            if (!ThemeStyle.light(ThemePreferences.get(host))) {
                setShadowLayer(dp(5).toFloat(), 0f, dp(2).toFloat(), 0xE6000000.toInt())
            }
        }
    }

    fun start() {
        if (running) {
            reload()
            return
        }
        running = true
        worker = Executors.newFixedThreadPool(2)
        artworkWorker = Executors.newFixedThreadPool(2)
        try {
            host.contentResolver.registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, observer)
            observing = true
        } catch (unavailable: RuntimeException) {
            observing = false
        }
        reload()
    }

    fun stop() {
        running = false
        generation++
        query?.cancel()
        if (observing) {
            try {
                host.contentResolver.unregisterContentObserver(observer)
            } catch (ignored: RuntimeException) {
            }
            observing = false
        }
        worker?.shutdownNow()
        artworkWorker?.shutdownNow()
        handler.removeCallbacksAndMessages(null)
    }

    fun requestAccess() {
        if (host.packageManager.resolveContentProvider(TvContract.AUTHORITY, 0) == null) {
            Toast.makeText(host, "This device has no TV Watch Next provider", Toast.LENGTH_LONG).show()
            return
        }
        if (host.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            reload()
            return
        }
        try {
            host.packageManager.getPermissionInfo(PERMISSION, 0)
            ThemeDialogBuilder(host).setTitle("Continue watching access")
                .setMessage("Allow reading TV listings so this launcher can show Watch Next entries shared by your apps. Private app history is not accessed.")
                .setPositiveButton("Continue") { _, _ -> host.requestPermissions(arrayOf(PERMISSION), PERMISSION_REQUEST) }
                .setNegativeButton(android.R.string.cancel, null).show()
        } catch (unavailable: Exception) {
            Toast.makeText(host, "This device does not expose shared TV Watch Next access", Toast.LENGTH_LONG).show()
        }
    }

    fun reload() {
        val enabled = prefs.getBoolean("continue_watching", true)
        visibility = if (enabled) VISIBLE else GONE
        val request = ++generation
        query?.cancel()
        if (!running || !enabled) {
            cards.removeAllViews()
            return
        }
        query = CancellationSignal()
        val cancellation = query
        val hadFocus = hasFocus()
        explanation.isFocusable = true
        if (host.packageManager.resolveContentProvider(TvContract.AUTHORITY, 0) == null) {
            cards.removeAllViews()
            allow.visibility = GONE
            explanation.text = "Resume is available on Android TVs with a shared Watch Next provider."
            if (hadFocus) focusEntry()
            return
        }
        val permitted = host.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
        allow.visibility = if (permitted) GONE else VISIBLE
        if (!permitted) {
            cards.removeAllViews()
            explanation.text = "Allow TV listings access to read shared Watch Next entries. Some TVs restrict this feature."
            explanation.isFocusable = false
            if (hadFocus) focusEntry()
            return
        }
        if (cards.childCount == 0) explanation.text = "Loading shared resume entries…"
        worker?.execute {
            val entries = ArrayList<Item>()
            var error: String? = null
            val columns = arrayOf(
                TvContract.WatchNextPrograms.COLUMN_TITLE, TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI, TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI, TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI
            )
            val hidden = prefs.getStringSet("hidden_apps", Collections.emptySet())
            try {
                val cursor: Cursor? = host.contentResolver.query(
                    TvContract.WatchNextPrograms.CONTENT_URI, columns, null, null,
                    TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC", cancellation
                )
                cursor?.use { c ->
                    var scanned = 0
                    while (c.moveToNext() && scanned++ < 200 && entries.size < 16) {
                        val title = c.getString(0)
                        val pkg = c.getString(1)
                        val intent = c.getString(2)
                        val position = c.getLong(4)
                        val duration = c.getLong(5)
                        if (title == null || title.trim().isEmpty() || pkg == null || intent == null || intent.length > 8192 || hidden?.contains(pkg) == true || c.getInt(7) == 0 ||
                            !WatchNextModel.resumable(c.getInt(3), position, duration)
                        ) continue
                        val owner = try {
                            host.packageManager.getApplicationLabel(host.packageManager.getApplicationInfo(pkg, 0)).toString()
                        } catch (removed: PackageManager.NameNotFoundException) {
                            continue
                        }
                        entries.add(Item(title, pkg, owner, intent, position, duration, c.getString(8), c.getString(9)))
                    }
                }
            } catch (unavailable: RuntimeException) {
                error = "Your TV has not exposed shared Watch Next entries to this launcher."
            }
            val message = error
            handler.post {
                if (!running || request != generation) return@post
                val restore = hasFocus()
                cards.removeAllViews()
                for (item in entries) addCard(item)
                explanation.isFocusable = entries.isEmpty()
                if (restore) focusEntry()
                explanation.text = message ?: if (entries.isEmpty()) {
                    "No shared resume items yet. Apps must publish Watch Next entries; private histories cannot be read."
                } else {
                    "Shared by your TV apps · select a title to resume"
                }
            }
        }
    }

    fun focusEntry(): Boolean {
        if (cards.childCount > 0) {
            if (focusedEntry != null) {
                for (i in 0 until cards.childCount) {
                    if (focusedEntry == cards.getChildAt(i).tag) return cards.getChildAt(i).requestFocus()
                }
            }
            return cards.getChildAt(0).requestFocus()
        }
        if (allow.visibility == VISIBLE) return allow.requestFocus()
        return explanation.requestFocus()
    }

    private fun addCard(item: Item) {
        val card = LinearLayout(host).apply {
            orientation = VERTICAL
            setPadding(
                dp(if (compact) 10 else 16), dp(if (compact) 10 else 16),
                dp(if (compact) 10 else 16), dp(if (compact) 10 else 16)
            )
            isFocusable = true
            isClickable = true
            background = background(false)
            contentDescription = "Resume ${item.title} in ${item.owner}. ${host.getString(R.string.h_save_hold)}"
        }
        val poster = ImageView(host).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = background(false)
            clipToOutline = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        card.addView(poster, LayoutParams(-1, dp(if (compact) 68 else 106)))
        try {
            poster.setImageDrawable(host.packageManager.getApplicationIcon(item.pkg))
        } catch (ignored: PackageManager.NameNotFoundException) {
        }
        val request = generation
        artworkWorker?.execute {
            if (!running || request != generation) return@execute
            val bitmap = ResumeArtwork.load(host, item.thumbnail, item.poster)
            if (bitmap != null) {
                handler.post {
                    if (running && request == generation) poster.setImageBitmap(bitmap)
                }
            }
        }
        val title = label(item.title, if (compact) 14 else 19).apply {
            setPadding(0, dp(if (compact) 6 else 10), 0, 0)
            maxLines = 2
            minLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(title, LayoutParams(-1, -2))
        val owner = label(item.owner, if (compact) 11 else 13).apply {
            setPadding(0, dp(if (compact) 4 else 6), 0, dp(if (compact) 4 else 8))
        }
        card.addView(owner)
        if (item.duration > 0) {
            val progress = ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = WatchNextModel.progress(item.position, item.duration)
            }
            card.addView(progress, LayoutParams(-1, dp(6)))
        }
        card.tag = "${item.pkg}|${item.intent}"
        card.setOnFocusChangeListener { _, focused ->
            card.background = background(focused)
            if (focused) {
                focusedEntry = card.tag as String?
                focusTitle.accept(item.owner)
            }
        }
        card.setOnClickListener {
            try {
                val intent = Intent.parseUri(item.intent, Intent.URI_INTENT_SCHEME)
                intent.selector = null
                if (intent.component != null && item.pkg != intent.component?.packageName) intent.component = null
                intent.setPackage(item.pkg)
                intent.action = Intent.ACTION_VIEW
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                host.startActivity(intent)
            } catch (unavailable: Exception) {
                Toast.makeText(host, "This app cannot resume the item now", Toast.LENGTH_LONG).show()
            }
        }
        val position = LayoutParams(dp(220), -2).apply { setMargins(dp(6), 0, dp(6), 0) }
        cards.addView(card, position)
    }

    private fun background(focused: Boolean): GradientDrawable {
        if (compact) return GlassAppCard.surface(host, GlassSettings.themeAccent(host), focused, dp(16))
        val shape = GradientDrawable().apply {
            setColor(0xe00a2036.toInt())
            cornerRadius = dp(16).toFloat()
            setStroke(dp(if (focused) 3 else 1), if (focused) 0xff40d9ff.toInt() else 0xff28435e.toInt())
        }
        return shape
    }

    private class Item(
        val title: String,
        val pkg: String,
        val owner: String,
        val intent: String,
        val position: Long,
        val duration: Long,
        val poster: String?,
        val thumbnail: String?
    )
}
