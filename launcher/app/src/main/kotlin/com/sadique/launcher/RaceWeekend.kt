package com.sadique.launcher

import android.content.res.Resources
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.Locale

/** Computes the current race weekend and session countdown from the theme calendar. */
object RaceWeekend {
    @JvmField val LEAD: Duration = Duration.ofHours(12)
    @JvmField val TAIL: Duration = Duration.ofHours(4)

    enum class Status { DONE, LIVE, NEXT, LATER }

    class Session(name: String, @JvmField val start: Instant) {
        @JvmField val name = name.trim { it <= ' ' }.uppercase(Locale.ROOT)

        fun isRace(): Boolean = name == "RACE"
        fun length(): Duration = when (name) {
            "RACE" -> Duration.ofHours(2)
            "SPRINT QUALI" -> Duration.ofMinutes(45)
            else -> Duration.ofHours(1)
        }

        fun title(res: Resources?): String = when (name) {
            "FP1" -> res!!.getString(R.string.race_practice_1)
            "FP2" -> res!!.getString(R.string.race_practice_2)
            "FP3" -> res!!.getString(R.string.race_practice_3)
            "SPRINT QUALI" -> res!!.getString(R.string.race_sprint_qualifying)
            "SPRINT" -> res!!.getString(R.string.race_sprint)
            "QUALIFYING" -> res!!.getString(R.string.race_qualifying)
            "RACE" -> res!!.getString(R.string.race_race)
            else -> name[0] + name.substring(1).lowercase(Locale.ROOT)
        }

        fun shortName(res: Resources?): String = when (name) {
            "FP1" -> res!!.getString(R.string.race_practice_1_short)
            "FP2" -> res!!.getString(R.string.race_practice_2_short)
            "FP3" -> res!!.getString(R.string.race_practice_3_short)
            "SPRINT QUALI" -> res!!.getString(R.string.race_sprint_qualifying_short)
            "SPRINT" -> res!!.getString(R.string.race_sprint_short)
            "QUALIFYING" -> res!!.getString(R.string.race_qualifying_short)
            "RACE" -> res!!.getString(R.string.race_race_short)
            else -> name
        }
    }

    class Race(@JvmField val round: Int, @JvmField val name: String, @JvmField val place: String,
        sessions: List<Session>) {
        @JvmField val sessions: List<Session> = Collections.unmodifiableList(sessions.sortedBy { it.start })

        fun shortName(): String = name.replace("Grand Prix", "GP").trim { it <= ' ' }
    }

    class State(@JvmField val race: Race, statuses: List<Status>, @JvmField val live: Session?,
        @JvmField val next: Session?, @JvmField val untilNext: Duration?) {
        @JvmField val statuses: List<Status> = Collections.unmodifiableList(statuses)

        fun finished(): Boolean = live == null && next == null
    }

    @JvmStatic
    fun at(calendar: List<Race>, now: Instant): State? {
        for (race in calendar) {
            if (race.sessions.isEmpty()) continue
            val opens = race.sessions.first().start.minus(LEAD)
            val closes = race.sessions.last().start.plus(TAIL)
            if (now.isBefore(opens) || !now.isBefore(closes)) continue

            val statuses = ArrayList<Status>(race.sessions.size)
            var live: Session? = null
            var next: Session? = null
            for (session in race.sessions) {
                val status = when {
                    now.isBefore(session.start) && next == null -> {
                        next = session
                        Status.NEXT
                    }
                    now.isBefore(session.start) -> Status.LATER
                    now.isBefore(session.start.plus(session.length())) -> {
                        live = session
                        Status.LIVE
                    }
                    else -> Status.DONE
                }
                statuses.add(status)
            }
            return State(race, statuses, live, next, next?.let { Duration.between(now, it.start) })
        }
        return null
    }

    @JvmStatic
    fun countdown(res: Resources, duration: Duration): String {
        val minutes = maxOf(1, (duration.seconds + 59) / 60)
        val hours = minutes / 60
        return when {
            minutes < 60 -> res.getString(R.string.race_countdown_minutes, minutes)
            hours < 24 -> res.getString(R.string.race_countdown_hours, hours, minutes % 60)
            else -> res.getString(R.string.race_countdown_days, hours / 24, hours % 24)
        }
    }
}
