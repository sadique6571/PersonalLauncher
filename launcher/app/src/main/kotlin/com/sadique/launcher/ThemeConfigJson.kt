package com.sadique.launcher

import android.graphics.Color
import java.util.Collections
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Decodes the supported values in a theme's JSON overlay. */
internal object ThemeConfigJson {
    /**
     * {@code "race_calendar": [{"round": 18, "name": "United States Grand Prix", "place": "Austin",
     * "sessions": [{"name": "FP1", "start": "2026-10-23T17:30:00Z"}, ...]}, ...]}. A race or session that does
     * not parse is left out on its own; the rest of the calendar still counts.
     */
    fun raceCalendar(json: JSONObject): List<RaceWeekend.Race> {
        val races = ArrayList<RaceWeekend.Race>()
        val list = json.optJSONArray("race_calendar") ?: return races
        for (i in 0 until list.length()) {
            val r = list.optJSONObject(i) ?: continue
            val sessions = r.optJSONArray("sessions") ?: continue
            val parsed = ArrayList<RaceWeekend.Session>()
            for (j in 0 until sessions.length()) {
                val s = sessions.optJSONObject(j) ?: continue
                val name = s.optString("name", "").trim()
                try {
                    if (name.isNotEmpty()) parsed.add(RaceWeekend.Session(name, java.time.Instant.parse(s.optString("start", ""))))
                } catch (ignored: java.time.format.DateTimeParseException) {
                    // a session without a readable start time cannot be counted down
                }
            }
            if (parsed.isNotEmpty()) races.add(RaceWeekend.Race(r.optInt("round", 0),
                r.optString("name", "Grand Prix").trim(), r.optString("place", "").trim(), parsed))
        }
        return races
    }

    /** wallpaper_moods as JSON text (config.xml); empty or unreadable is no groups. */
    fun moods(text: String): Map<String, Set<String>> {
        if (text.trim().isEmpty()) return Collections.emptyMap()
        try {
            return moods(JSONObject(text))
        } catch (e: JSONException) {
            return Collections.emptyMap()
        }
    }

    /** {"night": ["03-moon-lake.jpg", ...], ...}: each mood's picture names; other keys and names are left out. */
    fun moods(o: JSONObject): Map<String, Set<String>> {
        val groups = HashMap<String, Set<String>>()
        for (mood in WallpaperMood.ALL) {
            val names = o.optJSONArray(mood) ?: continue
            val group = HashSet<String>()
            for (i in 0 until names.length()) {
                val name = names.optString(i, "").trim()
                if (ThemeFiles.collectionImage(name)) group.add(name)
            }
            if (group.isNotEmpty()) groups[mood] = Collections.unmodifiableSet(group)
        }
        return Collections.unmodifiableMap(groups)
    }

    fun string(o: JSONObject, key: String, fallback: String): String {
        val v = o.opt(key)
        return if (v is String) v.trim() else fallback
    }

    fun bool(o: JSONObject, key: String, fallback: Boolean): Boolean {
        val v = o.opt(key)
        return if (v is Boolean) v else fallback
    }

    fun integer(o: JSONObject, key: String, fallback: Int, min: Int, max: Int): Int {
        val v = o.opt(key)
        if (v !is Number) return fallback
        val i = v.toInt()
        return if (i < min || i > max) fallback else i
    }

    fun color(o: JSONObject, key: String, fallback: Int): Int {
        val v = o.opt(key)
        if (v !is String) return fallback
        try {
            val c = Color.parseColor(v.trim())
            return c or 0xFF000000.toInt()   // always opaque: the window behind is black anyway
        } catch (e: IllegalArgumentException) {
            return fallback
        }
    }

    /** Positive keycodes only (vendor remotes use codes in the thousands); a list with anything else in it is ignored as a whole. */
    fun intArray(o: JSONObject, key: String, fallback: IntArray): IntArray {
        val v = o.opt(key)
        if (v !is JSONArray) return fallback
        val out = IntArray(v.length())
        for (i in 0 until v.length()) {
            val item = v.opt(i)
            if (item !is Number) return fallback
            out[i] = item.toInt()
            if (out[i] < 1) return fallback
        }
        return out
    }

    fun stringList(o: JSONObject, key: String, fallback: MutableList<String>): MutableList<String> {
        val v = o.opt(key)
        if (v !is JSONArray) return fallback
        val out = ArrayList<String>()
        for (i in 0 until v.length()) {
            val item = v.opt(i)
            if (item is String && item.trim().isNotEmpty()) out.add(item.trim())
        }
        return out
    }
}
