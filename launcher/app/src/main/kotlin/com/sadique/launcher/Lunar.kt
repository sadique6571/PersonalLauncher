package com.sadique.launcher

import android.content.res.Resources
import java.util.Calendar
import java.util.Locale

/**
 * Lunisolar (Vietnamese / Chinese) date, e.g. "2/9 âm lịch · Bính Ngọ" or "Lunar 2/9 · Year of the Horse", without ICU.
 */
object Lunar {

    /** Hours east of UTC used for the calendar: lunar_utc_offset, or 7 for Vietnamese, 9 for Korean and 8 otherwise. */
    @JvmStatic
    fun offsetFor(configured: Int, locale: Locale): Int {
        if (configured >= 0) return configured
        return when (locale.language) {
            "vi" -> 7
            "ko" -> 9
            else -> 8
        }
    }

    /** Today's lunar date, formatted with the locale's strings. */
    @JvmStatic
    fun today(res: Resources, utcOffset: Int): String {
        val c = Calendar.getInstance()
        val l = fromSolar(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR), utcOffset.toDouble())
        val month = if (l[3] == 1) res.getString(R.string.lunar_month_leap, l[1]) else l[1].toString()
        val stems = res.getStringArray(R.array.lunar_stems)
        val branches = res.getStringArray(R.array.lunar_branches)
        val year = res.getString(R.string.lunar_year, stems[Math.floorMod(l[2] + 6, 10)], branches[Math.floorMod(l[2] + 8, 12)])
        return res.getString(R.string.lunar_date_format, l[0], month, year)
    }

    /** Gregorian date to {day, month, year, leap (1 = leap month)} of the lunar calendar at UTC+tz. */
    @JvmStatic
    fun fromSolar(dd: Int, mm: Int, yy: Int, tz: Double): IntArray {
        val dayNumber = jdFromDate(dd, mm, yy)
        var k = Math.floor((dayNumber - 2415021.076998695) / 29.530588853).toInt() + 1
        var monthStart = newMoonDay(k, tz)
        while (monthStart > dayNumber) monthStart = newMoonDay(--k, tz)
        var a11 = lunarMonth11(yy, tz)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = lunarMonth11(yy - 1, tz)
        } else {
            lunarYear = yy + 1
            b11 = lunarMonth11(yy + 1, tz)
        }
        val lunarDay = (dayNumber - monthStart + 1).toInt()
        val diff = Math.floor((monthStart - a11) / 29.0).toInt()
        var leap = 0
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapDiff = leapMonthOffset(a11, tz)
            if (diff >= leapDiff) {
                lunarMonth = diff + 10
                if (diff == leapDiff) leap = 1
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1
        return intArrayOf(lunarDay, lunarMonth, lunarYear, leap)
    }

    @JvmStatic
    fun jdFromDate(dd: Int, mm: Int, yy: Int): Long {
        val a = (14 - mm) / 12
        val y = yy + 4800L - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        return jd
    }

    @JvmStatic
    fun newMoonDay(k: Int, tz: Double): Long {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = Math.PI / 180
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * Math.sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * Math.sin(m * dr) + 0.0021 * Math.sin(2 * dr * m)
        c1 -= 0.4068 * Math.sin(mpr * dr) - 0.0161 * Math.sin(dr * 2 * mpr)
        c1 -= 0.0004 * Math.sin(dr * 3 * mpr)
        c1 += 0.0104 * Math.sin(dr * 2 * f) - 0.0051 * Math.sin(dr * (m + mpr))
        c1 -= 0.0074 * Math.sin(dr * (m - mpr)) - 0.0004 * Math.sin(dr * (2 * f + m))
        c1 -= 0.0004 * Math.sin(dr * (2 * f - m)) - 0.0006 * Math.sin(dr * (2 * f + mpr))
        c1 += 0.0010 * Math.sin(dr * (2 * f - mpr)) + 0.0005 * Math.sin(dr * (2 * mpr + m))
        val deltaT = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        return Math.floor(jd1 + c1 - deltaT + 0.5 + tz / 24).toLong()
    }

    @JvmStatic
    fun sunLongitude(jdn: Long, tz: Double): Int {
        val t = (jdn - 2451545.5 - tz / 24) / 36525
        val t2 = t * t
        val dr = Math.PI / 180
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * Math.sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * Math.sin(dr * 2 * m) + 0.000290 * Math.sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= Math.PI * 2 * Math.floor(l / (Math.PI * 2))
        return Math.floor(l / Math.PI * 6).toInt()
    }

    @JvmStatic
    fun lunarMonth11(yy: Int, tz: Double): Long {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = Math.floor(off / 29.530588853).toInt()
        var nm = newMoonDay(k, tz)
        if (sunLongitude(nm, tz) >= 9) nm = newMoonDay(k - 1, tz)
        return nm
    }

    @JvmStatic
    fun leapMonthOffset(a11: Long, tz: Double): Int {
        val k = Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var i = 1
        var arc = sunLongitude(newMoonDay(k + i, tz), tz)
        var last: Int
        do {
            last = arc
            i++
            arc = sunLongitude(newMoonDay(k + i, tz), tz)
        } while (arc != last && i < 14)
        return i - 1
    }
}
