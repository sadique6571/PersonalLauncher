package com.sadique.launcher

/** Time-of-day sky palette for the sunset backdrop. */
object Sky {
    private class Stop(
        val minute: Int, val top: Int, val mid: Int, val bottom: Int, val sun: Int, val sunX: Float
    )

    class Palette(
        @JvmField val top: Int, @JvmField val mid: Int, @JvmField val bottom: Int,
        @JvmField val sun: Int, @JvmField val sunX: Float
    )

    private const val NIGHT_TOP = 0xFF04060E.toInt()
    private const val NIGHT_MID = 0xFF070C1A.toInt()
    private const val NIGHT_BOTTOM = 0xFF0B1326.toInt()
    const val REF_SUNRISE = 450
    const val REF_SUNSET = 1140
    private val stops = arrayOf(
        Stop(0, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
        Stop(320, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
        Stop(390, 0xFF160F2E.toInt(), 0xFF3B2352.toInt(), 0xFF8A4A6B.toInt(), 100, 0.24f),
        Stop(450, 0xFF12203F.toInt(), 0xFF5C3A63.toInt(), 0xFFD3674F.toInt(), 160, 0.30f),
        Stop(570, 0xFF0E1A33.toInt(), 0xFF1F3557.toInt(), 0xFF43648B.toInt(), 90, 0.38f),
        Stop(780, 0xFF0B1524.toInt(), 0xFF17304F.toInt(), 0xFF31587F.toInt(), 60, 0.50f),
        Stop(1020, 0xFF0D1526.toInt(), 0xFF20395C.toInt(), 0xFF4C5470.toInt(), 90, 0.62f),
        Stop(1140, 0xFF1B1030.toInt(), 0xFF6C2E52.toInt(), 0xFFE2704A.toInt(), 190, 0.76f),
        Stop(1185, 0xFF140C27.toInt(), 0xFF4A2049.toInt(), 0xFFA54A5C.toInt(), 130, 0.82f),
        Stop(1260, 0xFF0A0A1C.toInt(), 0xFF191230.toInt(), 0xFF3A1F49.toInt(), 60, 0.86f),
        Stop(1350, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
        Stop(1440, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
    )

    /** Stretches the reference day so the glows land at the local sunrise and sunset. */
    @JvmStatic
    fun at(minuteOfDay: Int, sunrise: Int, sunset: Int): Palette {
        if (sunrise <= 0 || sunset >= 1440 || sunrise >= sunset) return at(minuteOfDay)
        val minute = minuteOfDay.coerceIn(0, 1440)
        val reference = when {
            minute < sunrise -> minute * REF_SUNRISE / sunrise.toFloat()
            minute < sunset -> REF_SUNRISE +
                (minute - sunrise) * (REF_SUNSET - REF_SUNRISE) / (sunset - sunrise).toFloat()
            else -> REF_SUNSET + (minute - sunset) * (1440 - REF_SUNSET) / (1440 - sunset).toFloat()
        }
        return at(Math.round(reference))
    }

    @JvmStatic
    fun at(minuteOfDay: Int): Palette {
        val minute = minuteOfDay.coerceIn(0, 1440)
        val next = (1 until stops.size).first { minute <= stops[it].minute }
        val a = stops[next - 1]
        val b = stops[next]
        val t = (minute - a.minute) / (b.minute - a.minute).toFloat()
        return Palette(lerpColor(a.top, b.top, t), lerpColor(a.mid, b.mid, t),
            lerpColor(a.bottom, b.bottom, t), Math.round(a.sun + (b.sun - a.sun) * t),
            a.sunX + (b.sunX - a.sunX) * t)
    }

    @JvmStatic
    fun minuteOfDay(hour: Int, minute: Int): Int = hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)

    @JvmStatic
    fun lerpColor(from: Int, to: Int, t: Float): Int {
        val fraction = t.coerceIn(0f, 1f)
        return (channel(from, to, 24, fraction) shl 24) or (channel(from, to, 16, fraction) shl 16) or
            (channel(from, to, 8, fraction) shl 8) or channel(from, to, 0, fraction)
    }

    private fun channel(from: Int, to: Int, shift: Int, fraction: Float): Int {
        val start = (from ushr shift) and 255
        val end = (to ushr shift) and 255
        return Math.round(start + (end - start) * fraction)
    }
}
