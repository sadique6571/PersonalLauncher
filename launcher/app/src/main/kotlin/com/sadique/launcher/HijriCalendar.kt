package com.sadique.launcher

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** Calculated Umm al-Qura date with a user-adjustable offset for local moon sighting. */
object HijriCalendar {
    private val months = arrayOf(
        "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Awwal", "Jumada al-Thani",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    )

    @JvmStatic
    fun format(day: LocalDate, adjustment: Int): String {
        val date = HijrahDate.from(day.plusDays(adjustment.coerceIn(-2, 2).toLong()))
        val month = months[date.get(ChronoField.MONTH_OF_YEAR) - 1]
        return "${date.get(ChronoField.DAY_OF_MONTH)} $month ${date.get(ChronoField.YEAR_OF_ERA)} AH"
    }
}
