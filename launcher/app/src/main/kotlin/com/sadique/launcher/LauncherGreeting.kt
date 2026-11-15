package com.sadique.launcher

import android.app.Activity
import android.widget.TextView
import java.util.Calendar

internal object LauncherGreeting {
    fun update(host: Activity, greeting: TextView, greetingName: String, greetingColor: Int, festive: Boolean, festival: Festival) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        var res: Int
        // Locale-specific day parts (app language on Android 13+).
        // Indonesian: Pagi 00-10, Siang 10-15, Sore 15-18, Malam 18-24.
        // Spain (es-ES) and Catalan: "Buenas tardes" into the evening; "Buenas noches" from 21.
        // Latin American Spanish (es-MX, es-AR, …) keeps the default hours: evening strings are also
        // «Buenas noches», and there «Buenas noches» from ~17 matches local use better than Spain's 21.
        val locale = host.resources.configuration.locales[0]
        val lang = locale.language
        val country = locale.country
        if ("in" == lang || "id" == lang) {
            if (hour < 10) res = R.string.good_morning
            else if (hour < 15) res = R.string.good_afternoon
            else if (hour < 18) res = R.string.good_evening
            else res = R.string.good_night
        } else if (("es" == lang && "ES" == country) || "ca" == lang) {
            if (hour < 6) res = R.string.good_night
            else if (hour < 12) res = R.string.good_morning
            else if (hour < 21) res = R.string.good_afternoon
            else res = R.string.good_night
        } else if ("da" == lang) {
            // Danish day parts: "God morgen" until 11, then "God formiddag" to noon.
            if (hour < 5) res = R.string.good_night
            else if (hour < 11) res = R.string.good_morning
            else if (hour < 12) res = R.string.good_forenoon
            else if (hour < 18) res = R.string.good_afternoon
            else if (hour < 22) res = R.string.good_evening
            else res = R.string.good_night
        } else {
            if (hour < 5) res = R.string.good_night
            else if (hour < 12) res = R.string.good_morning
            else if (hour < 17) res = R.string.good_afternoon
            else if (hour < 22) res = R.string.good_evening
            else res = R.string.good_night
        }
        val festiveGreeting = if (festive) festival.greetingText(host.resources) else ""
        if (festiveGreeting.isNotEmpty()) {
            greeting.text = festiveGreeting
            greeting.setTextColor(liftTowardsWhite(festival.accentColor(), 0.25f))
        } else {
            greeting.text = if (greetingName.isEmpty()) host.getString(res)
                    else host.getString(R.string.greeting_with_name, host.getString(res), greetingName)
            greeting.setTextColor(greetingColor)
        }
    }

    private fun liftTowardsWhite(argb: Int, amount: Float): Int {
        var r = (argb shr 16) and 0xFF
        var g = (argb shr 8) and 0xFF
        var b = argb and 0xFF
        r += ((255 - r) * amount).toInt()
        g += ((255 - g) * amount).toInt()
        b += ((255 - b) * amount).toInt()
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }
}
