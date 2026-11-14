package com.sadique.launcher

import java.time.LocalDate
import java.time.ZoneId

/**
 * Sunrise and sunset for a place, computed offline with NOAA's general solar position equations.
 */
object Sun {

    @JvmStatic
    fun riseAndSet(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): IntArray? {
        val g = 2 * Math.PI / 365 * (date.dayOfYear - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g))
        val decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) -
                0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g) -
                0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g)
        val lat = Math.toRadians(latitude)
        val cosHa = Math.cos(Math.toRadians(90.833)) / (Math.cos(lat) * Math.cos(decl)) - Math.tan(lat) * Math.tan(decl)
        if (cosHa > 1 || cosHa < -1) return null
        val ha = Math.toDegrees(Math.acos(cosHa))
        val offset = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 60
        return intArrayOf(
            Math.round(720 - 4 * (longitude + ha) - eqTime + offset).toInt(),
            Math.round(720 - 4 * (longitude - ha) - eqTime + offset).toInt()
        )
    }
}
