package com.sadique.launcher

import android.content.res.Resources
import java.util.Calendar

/** Solar Hijri (Shamsi) date with localized month names, without ICU: the arithmetic is small and stable. */
object Shamsi {

    private val MONTHS = intArrayOf(
        R.string.shamsi_month_farvardin, R.string.shamsi_month_ordibehesht, R.string.shamsi_month_khordad,
        R.string.shamsi_month_tir, R.string.shamsi_month_mordad, R.string.shamsi_month_shahrivar,
        R.string.shamsi_month_mehr, R.string.shamsi_month_aban, R.string.shamsi_month_azar,
        R.string.shamsi_month_dey, R.string.shamsi_month_bahman, R.string.shamsi_month_esfand
    )

    /** e.g. "27 Mehr 1405" */
    @JvmStatic
    fun today(res: Resources): String {
        val c = Calendar.getInstance()
        val j = toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        return "${j[2]} ${res.getString(MONTHS[j[1] - 1])} ${j[0]}"
    }

    /** Gregorian to Jalali, the 33-year-cycle algorithm used by jalaali-js. Returns {year, month, day}. */
    @JvmStatic
    fun toJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val monthStart = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + monthStart[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return intArrayOf(jy, jm, jd)
    }
}
