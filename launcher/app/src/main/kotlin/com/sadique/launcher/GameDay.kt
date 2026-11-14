package com.sadique.launcher

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import java.time.Duration
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * game_day (config.xml or a theme): each team's next game in the race panel's place, from an add-on such as
 * Game Day, rotating with the theme's F1 weekend. The launcher only reads what the add-on publishes and stays
 * offline. Contract: docs/modules.md.
 */
internal class GameDay private constructor(
    private val ctx: Context,
    private val uri: Uri,
    private val main: Handler,
    private val onChange: Runnable
) {
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val observer: ContentObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            read()
        }
    }
    private var watching = false
    private var turnsList: List<Turn> = Collections.emptyList()
    private var turnSecondsVal = TURN_DEFAULT

    /** Home is on screen: read now and watch for changes. */
    fun start() {
        if (!watching) {
            try {
                ctx.contentResolver.registerContentObserver(uri, false, observer)
                watching = true
            } catch (e: SecurityException) {
                // The add-on's provider went away; the panel keeps what it had.
            }
        }
        read()
    }

    /** Home is off screen: nothing runs until it is back. */
    fun stop() {
        if (watching) ctx.contentResolver.unregisterContentObserver(observer)
        watching = false
    }

    fun shutdown() {
        stop()
        io.shutdownNow()
    }

    /** One query off the main thread (the provider lives in the add-on's process), then onChange on the main thread. */
    fun read() {
        try {
            io.execute {
                val rows = ArrayList<Array<Any?>>()
                try {
                    ctx.contentResolver.query(uri, COLUMNS, null, null, null)?.use { c ->
                        while (c.moveToNext()) {
                            rows.add(
                                arrayOf(
                                    c.getString(0),
                                    c.getString(1),
                                    c.getLong(2),
                                    c.getLong(3),
                                    c.getInt(4),
                                    c.getString(5),
                                    c.getInt(6)
                                )
                            )
                        }
                    }
                } catch (gone: RuntimeException) {
                    // Uninstalled or crashed mid-query: keep what is on screen.
                    return@execute
                }
                val parsed = fromRows(rows)
                val seconds = turnSeconds(rows)
                main.post {
                    turnsList = parsed
                    turnSecondsVal = seconds
                    onChange.run()
                }
            }
        } catch (shutDown: RejectedExecutionException) {
            // Home is being destroyed.
        }
    }

    fun turns(): List<Turn> = turnsList

    fun turnSeconds(): Int = turnSecondsVal

    /** One turn in the rotation: a game from the add-on, or the theme's F1 weekend ([f1]). */
    class Turn(
        @JvmField val topLine: String,
        /** "Puck drop": the countdown reads "Puck drop in 2h 18m". */
        @JvmField val word: String,
        /** For the F1 turn: when its next session starts, or now while one is live (the sort key). */
        @JvmField val startMs: Long,
        @JvmField val endMs: Long,
        /** ARGB, for the short bar. */
        @JvmField val colour: Int,
        @JvmField val f1: Boolean
    ) {
        constructor(topLine: String, word: String, startMs: Long, endMs: Long, colour: Int) :
                this(topLine, word, startMs, endMs, colour, false)
    }

    companion object {
        const val META = "com.sadique.launcher.games"
        const val TURN_DEFAULT = 30
        private val COLUMNS = arrayOf("top_line", "word", "start_ms", "end_ms", "colour", "calendar", "turn_seconds")

        /**
         * Null when the key is empty, the package is not installed, or it names no provider of its own in a
         * [META] meta-data. Naming the package is the owner's opt-in.
         */
        @JvmStatic
        fun create(ctx: Context, pkg: String?, main: Handler, onChange: Runnable): GameDay? {
            if (pkg.isNullOrBlank()) return null
            return try {
                val pm = ctx.packageManager
                val info = pm.getApplicationInfo(pkg.trim(), PackageManager.GET_META_DATA)
                val authority = info.metaData?.getString(META)
                if (authority.isNullOrBlank()) return null
                val provider = pm.resolveContentProvider(authority.trim(), 0)
                if (provider == null || pkg.trim() != provider.packageName) return null
                val uri = Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(authority.trim())
                    .appendPath("turns")
                    .build()
                GameDay(ctx, uri, main, onChange)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

        /** The add-on's rows as turns; a row without a top line or with impossible times is skipped. */
        @JvmStatic
        fun fromRows(rows: List<Array<Any?>>): List<Turn> {
            val out = ArrayList<Turn>()
            for (r in rows) {
                if (r[0] !is String || (r[0] as String).isEmpty()) continue
                val start = if (r[2] is Number) (r[2] as Number).toLong() else -1L
                val end = if (r[3] is Number) (r[3] as Number).toLong() else -1L
                if (start < 0 || end <= start) continue
                val word = if (r[1] is String) r[1] as String else ""
                val colour = if (r[4] is Number) (r[4] as Number).toInt() else 0xFFFFFFFF.toInt()
                out.add(Turn(r[0] as String, word, start, end, colour))
            }
            return out
        }

        /** 10 to 120 seconds per turn, as the add-on's settings allow; anything else is the default. */
        @JvmStatic
        fun turnSeconds(rows: List<Array<Any?>>): Int {
            if (rows.isEmpty() || rows[0][6] !is Number) return TURN_DEFAULT
            val s = (rows[0][6] as Number).toInt()
            return if (s in 10..120) s else TURN_DEFAULT
        }

        /**
         * The turns to rotate through, soonest first: the games that have not ended, and the F1 weekend as one turn
         * while the race panel would show it (sorted by its next session, or now while a session is live).
         */
        @JvmStatic
        fun merge(games: List<Turn>, f1: RaceWeekend.State?, nowMs: Long): List<Turn> {
            val out = ArrayList<Turn>()
            for (t in games) {
                if (t.endMs > nowMs) out.add(t)
            }
            if (f1 != null) {
                val key = if (f1.live == null && f1.next != null) f1.next.start.toEpochMilli() else nowMs
                out.add(Turn("", "", key, Long.MAX_VALUE, 0, true))
            }
            out.sortWith(Comparator.comparingLong { t -> t.startMs })
            return out
        }

        /** Which turn is on screen, [sinceMs] after the rotation started. */
        @JvmStatic
        fun current(count: Int, sinceMs: Long, turnSeconds: Int): Int {
            if (count <= 0) return 0
            return ((Math.max(0L, sinceMs) / (turnSeconds * 1000L)) % count).toInt()
        }

        /** Milliseconds until the next turn, [sinceMs] after the rotation started. */
        @JvmStatic
        fun delayToNextTurn(sinceMs: Long, turnSeconds: Int): Long {
            val turnMs = turnSeconds * 1000L
            return turnMs - Math.floorMod(sinceMs, turnMs)
        }

        @JvmStatic
        fun live(t: Turn, nowMs: Long): Boolean {
            return nowMs >= t.startMs && nowMs < t.endMs
        }

        @JvmStatic
        fun until(t: Turn, nowMs: Long): Duration {
            return Duration.ofMillis(t.startMs - nowMs)
        }
    }
}
