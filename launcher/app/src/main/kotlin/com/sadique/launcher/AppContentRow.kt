package com.sadique.launcher

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer

/** A horizontal banner row for one app (Netflix, YouTube), fed by the TV's Watch Next. */
class AppContentRow(
    private val host: Activity,
    title: String,
    private val packages: Array<String>,
    private val focusTitle: Consumer<String>
) : LinearLayout(host) {
    private val prefs: SharedPreferences = ThemePreferences.get(host)
    private val cards: LinearLayout
    private val heading: TextView
    /** Current label color, following the wallpaper brightness (cards sit on the wallpaper). */
    private var labelColor = 0xfff0f7ff.toInt()
    private val scroller: HorizontalScrollView
    private val handler = Handler(Looper.getMainLooper())
    private var worker: ExecutorService? = null
    private var artworkWorker: ExecutorService? = null
    private var query: CancellationSignal? = null
    private var running = false
    private var generation = 0

    init {
        orientation = VERTICAL
        val rowGap = intArrayOf(8, 18, 28)[Math.max(0, Math.min(2, prefs.getInt("row_spacing", 1)))]
        setPadding(0, dp(rowGap), 0, dp(8))
        // Title row with app logo.
        val titleRow = LinearLayout(host).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = ImageView(host)
        try {
            val iconPkg = packages[0]
            logo.setImageDrawable(host.packageManager.getApplicationIcon(iconPkg))
        } catch (ignored: Exception) {
        }
        titleRow.addView(logo, LayoutParams(dp(28), dp(28)))
        heading = TextView(host).apply {
            text = title
            textSize = 20f
            setTextColor(0xfff0f7ff.toInt())
            setPadding(dp(10), 0, 0, dp(10))
        }
        titleRow.addView(heading, LayoutParams(-2, -2))
        addView(titleRow)
        scroller = HorizontalScrollView(host).apply {
            setHorizontalScrollBarEnabled(false)
            clipChildren = false
            clipToPadding = false
        }
        cards = LinearLayout(host).apply {
            orientation = HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        scroller.addView(cards, LayoutParams(-2, -2))
        addView(scroller, LayoutParams(-1, -2))
        visibility = GONE
    }

    /** Adapts the heading and card labels to wallpaper brightness. */
    fun setHeadingColor(color: Int) {
        labelColor = color
        heading.setTextColor(color)
        for (i in 0 until cards.childCount) {
            val card = cards.getChildAt(i)
            if (card is LinearLayout) {
                val box = card
                for (j in 0 until box.childCount) {
                    val v = box.getChildAt(j)
                    if (v is TextView && "banner_label" == v.tag) {
                        v.setTextColor(color)
                    }
                }
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        worker = Executors.newSingleThreadExecutor()
        artworkWorker = Executors.newFixedThreadPool(2)
        reload()
    }

    fun stop() {
        running = false
        generation++
        handler.removeCallbacks(autoScroll)
        query?.cancel()
        worker?.shutdownNow()
        artworkWorker?.shutdownNow()
    }

    /** Gentle auto-scroll for banner rows (toggle in settings). */
    private val autoScroll: Runnable = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            if (!running || !prefs.getBoolean("banner_autoscroll", false)) return
            val max = cards.width - scroller.width
            if (max > 0) {
                var next = scroller.scrollX + dp(160)
                if (next >= max) next = 0
                scroller.smoothScrollTo(next, 0)
            }
            handler.postDelayed(this, 4000)
        }
    }

    fun reload() {
        if (!running) return
        query?.cancel()
        query = CancellationSignal()
        val cancellation = query
        val request = ++generation
        cards.removeAllViews()
        worker?.execute {
            val entries = ArrayList<Item>()
            val columns = arrayOf(
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.WatchNextPrograms.COLUMN_BROWSABLE
            )
            try {
                host.contentResolver.query(
                    TvContract.WatchNextPrograms.CONTENT_URI, columns,
                    null, null, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC", cancellation
                )?.use { cursor ->
                    var scanned = 0
                    while (cursor.moveToNext() && scanned++ < 200 && entries.size < 12) {
                        val title = cursor.getString(0)
                        val pkg = cursor.getString(1)
                        val intent = cursor.getString(2)
                        if (title.isNullOrBlank() || pkg == null || intent == null || cursor.getInt(5) == 0) continue
                        var match = false
                        for (p in packages) {
                            if (p == pkg) {
                                match = true
                                break
                            }
                        }
                        if (!match) continue
                        entries.add(Item(title, pkg, intent, cursor.getString(3), cursor.getString(4)))
                    }
                }
            } catch (ignored: Exception) {
            }
            handler.post {
                if (!running || request != generation) return@post
                for (item in entries) addCard(item)
                visibility = if (entries.isEmpty()) GONE else VISIBLE
                handler.removeCallbacks(autoScroll)
                if (prefs.getBoolean("banner_autoscroll", false) && entries.isNotEmpty()) {
                    handler.postDelayed(autoScroll, 4000)
                }
            }
        }
    }

    private fun addCard(item: Item) {
        val card = LinearLayout(host).apply {
            orientation = VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            isFocusable = true
            isClickable = true
        }
        val poster = ImageView(host).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.banner_poster_bg)
            clipToOutline = true
        }
        val sizePref = Math.max(0, Math.min(2, prefs.getInt("banner_poster_size", 1)))
        val w = dp(intArrayOf(120, 160, 200)[sizePref])
        val h = dp(intArrayOf(68, 90, 112)[sizePref])
        card.addView(poster, LayoutParams(w, h))
        val label = TextView(host).apply {
            text = item.title
            textSize = 13f
            setTextColor(labelColor)
            tag = "banner_label"
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(6), 0, 0)
        }
        card.addView(label, LayoutParams(w, -2))
        card.setOnClickListener {
            try {
                host.startActivity(Intent.parseUri(item.intent, 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ignored: Exception) {
            }
        }
        card.setOnFocusChangeListener { v, focus ->
            if (focus) focusTitle.accept(item.title)
            v.background = if (focus) focusBorder() else null
        }
        cards.addView(card)
        val request = generation
        artworkWorker?.execute {
            if (!running || request != generation) return@execute
            val bitmap = ResumeArtwork.load(host, item.thumbnail, item.poster)
            if (bitmap != null) handler.post {
                if (!running || request != generation) return@post
                poster.setImageBitmap(bitmap)
            }
        }
    }

    private fun focusBorder(): Drawable {
        val border = GradientDrawable()
        border.setColor(0x00000000)
        border.setCornerRadius(dp(16).toFloat())
        border.setStroke(dp(2), GlassSettings.themeAccent(host))
        return border
    }

    private fun dp(value: Int): Int {
        return Math.round(value * host.resources.displayMetrics.density)
    }

    private class Item(
        val title: String,
        val pkg: String,
        val intent: String,
        val poster: String?,
        val thumbnail: String?
    )
}
