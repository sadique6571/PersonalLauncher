package com.sadique.launcher

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.SystemClock
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal class LauncherSportsPanel(
    private val host: Activity,
    private val handler: Handler,
    cfg: Config,
    private val density: Float,
    private val stopped: () -> Boolean,
) {
    private lateinit var raceCalendar: List<RaceWeekend.Race>
    private lateinit var racePanel: View
    private lateinit var raceKicker: TextView
    private lateinit var raceHeadline: TextView
    private lateinit var raceSessions: TextView
    /** game_day: each team's next game from an add-on, rotating in the race panel; null when the key is off. */
    private var gameDay: GameDay? = null
    private lateinit var raceBar: View
    private lateinit var racePager: LinearLayout
    private var rotationStart = 0L
    private var lastEndedRead = 0L
    private val turnTick = Runnable { update() }

    init {
        raceCalendar = cfg.raceCalendar
        racePanel = host.findViewById(R.id.racePanel)
        raceKicker = host.findViewById(R.id.raceKicker)
        raceHeadline = host.findViewById(R.id.raceHeadline)
        raceSessions = host.findViewById(R.id.raceSessions)
        raceBar = host.findViewById(R.id.raceBar)
        racePager = host.findViewById(R.id.racePager)
        gameDay = GameDay.create(host, cfg.gameDay, handler, Runnable { update() })
        // The rotation runs on the clock that never jumps: setting the TV's time must not stall or stretch a turn.
        rotationStart = SystemClock.elapsedRealtime()
    }

    fun attachToGrid(grid: RecyclerView, clockBlock: View) {
        // game_day: the panel above the dock rides up with the cards instead of being covered by them, and fades
        // out by the time it reaches the clock. The grid's own offset is read, so a jump (Home, a restored focus)
        // lands right too.
        if (gameDay != null) {
            grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    val shift = if (rv.canScrollVertically(-1)) rv.computeVerticalScrollOffset() else 0
                    racePanel.translationY = -shift.toFloat()
                    val room = Math.max(1, racePanel.top - clockBlock.bottom)
                    racePanel.alpha = Math.max(0f, 1f - shift / room.toFloat())
                }
            })
        }
    }

    fun read() { gameDay?.read() }
    fun start() { gameDay?.start() }
    fun stop() {
        gameDay?.stop()
        handler.removeCallbacks(turnTick)
    }
    fun shutdown() { gameDay?.shutdown() }

    // ---- race-weekend panel (a theme's race_calendar) -----------------------------------------------

    /**
     * Shows or hides the race-weekend panel for this minute. Text only, set once a minute with the clock:
     * nothing animates, so the panel costs the GPU nothing between ticks.
     */
    /**
     * The race panel's place under the clock. With game_day off it is exactly the F1 panel (updateRace). With it on,
     * each team's next game takes a turn there, and the theme's F1 weekend is one more turn drawn as today.
     */
    fun update() {
        val day = gameDay
        if (day == null) {
            updateRace()
            return
        }
        handler.removeCallbacks(turnTick)
        val now = System.currentTimeMillis()
        val games = day.turns()
        for (g in games) {
            if (g.endMs <= now && now - lastEndedRead > MINUTE_MS) {
                // A game is over: the add-on already has that team's next one.
                lastEndedRead = now
                day.read()
                break
            }
        }
        val f1 = if (raceCalendar.isEmpty()) null else RaceWeekend.at(raceCalendar, Instant.ofEpochMilli(now))
        val turns = GameDay.merge(games, f1, now)
        if (turns.isEmpty()) {
            racePanel.visibility = View.GONE
            return
        }
        val seconds = day.turnSeconds()
        val since = SystemClock.elapsedRealtime() - rotationStart
        val on = GameDay.current(turns.size, since, seconds)
        val t = turns[on]
        if (t.f1) {
            raceBar.setBackgroundResource(R.drawable.race_bar)
            raceSessions.visibility = View.VISIBLE
            updateRace()
        } else {
            val bar = GradientDrawable()
            bar.cornerRadius = 1.5f * density
            bar.setColor(t.colour)
            raceBar.background = bar
            raceKicker.text = t.topLine
            if (GameDay.live(t, now)) {
                val live = SpannableString("\u25CF  " + host.getString(R.string.game_live))
                live.setSpan(ForegroundColorSpan(RACE_RED), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                raceHeadline.text = live
            } else {
                val countdown = RaceWeekend.countdown(host.resources, GameDay.until(t, now))
                raceHeadline.text = if (t.word.isEmpty()) countdown else host.getString(R.string.race_next, t.word, countdown)
            }
            // No session times for a game: the line takes no room, so the dots sit under the countdown.
            raceSessions.visibility = View.GONE
            racePanel.visibility = View.VISIBLE
        }
        showPager(turns.size, on)
        if (turns.size > 1 && !stopped()) handler.postDelayed(turnTick, GameDay.delayToNextTurn(since, seconds))
    }

    /** One dot per turn, the one on screen bright; none for a single turn. */
    private fun showPager(count: Int, on: Int) {
        if (count < 2) {
            racePager.visibility = View.GONE
            return
        }
        if (racePager.childCount != count) {
            racePager.removeAllViews()
            val size = Math.round(5 * density)
            for (k in 0 until count) {
                val dot = View(host)
                val d = GradientDrawable()
                d.shape = GradientDrawable.OVAL
                d.setColor(0xFFFFFFFF.toInt())
                dot.background = d
                val lp = LinearLayout.LayoutParams(size, size)
                lp.marginEnd = size
                racePager.addView(dot, lp)
            }
        }
        for (k in 0 until count) racePager.getChildAt(k).alpha = if (k == on) 0.9f else 0.3f
        racePager.visibility = View.VISIBLE
    }

    private fun updateRace() {        // an empty calendar (a theme without race_calendar, or a reset) hides a panel the last theme showed
        val s = if (raceCalendar.isEmpty()) null else RaceWeekend.at(raceCalendar, Instant.now())
        if (s == null) {
            racePanel.visibility = View.GONE
            return
        }
        val race = s.race
        raceKicker.text = if (race.place.isEmpty())
                host.getString(R.string.race_kicker_no_place, race.round, race.shortName())
                else host.getString(R.string.race_kicker, race.round, race.shortName(), race.place)

        if (s.live != null) {
            val live = SpannableString("\u25CF  " + host.getString(R.string.race_live, s.live!!.title(host.resources)))
            live.setSpan(ForegroundColorSpan(RACE_RED), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            raceHeadline.text = live
        } else if (s.next != null) {
            // untilNext is set whenever next is (RaceWeekend.at builds it from next).
            val countdown = RaceWeekend.countdown(host.resources, s.untilNext!!)
            raceHeadline.text = if (s.next!!.isRace()) host.getString(R.string.race_lights_out, countdown)
                    else host.getString(R.string.race_next, s.next!!.title(host.resources), countdown)
        } else {
            raceHeadline.setText(R.string.race_finished)
        }

        // FP1 FRI 11:00 PM   FP2 SAT 2:30 AM ...: done sessions fade, the live one is red, the next one bright.
        val formatter = DateTimeFormatter.ofPattern(
                if (android.text.format.DateFormat.is24HourFormat(host)) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault())
                .withZone(ZoneId.systemDefault())
        val row = SpannableStringBuilder()
        for (i in race.sessions.indices) {
            val session = race.sessions[i]
            if (i > 0) row.append("     ")
            val start = row.length
            row.append(session.shortName(host.resources)).append("  ").append(formatter.format(session.start).uppercase(Locale.getDefault()))
            val color = when (s.statuses[i]) {
                RaceWeekend.Status.DONE -> 0x47FFFFFF.toInt()
                RaceWeekend.Status.LIVE -> RACE_RED
                RaceWeekend.Status.NEXT -> 0xFFFFFFFF.toInt()
                else -> 0x99FFFFFF.toInt()
            }
            row.setSpan(ForegroundColorSpan(color), start, row.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        raceSessions.text = row
        racePanel.visibility = View.VISIBLE
    }

    private companion object {
        private const val MINUTE_MS = 60_000L
        private val RACE_RED = 0xFFFF3B2F.toInt()
    }
}
