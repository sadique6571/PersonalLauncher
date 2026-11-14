package com.sadique.launcher

import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Selects wallpaper groups from sunrise, noon and sunset. */
object WallpaperMood {
    const val MORNING = "morning"
    const val EVENING = "evening"
    const val NIGHT = "night"

    @JvmField
    val ALL = arrayOf(MORNING, EVENING, NIGHT)
    const val DEFAULT_SUNRISE = 7 * 60
    const val DEFAULT_SUNSET = 18 * 60 + 30
    private const val NOON = 12 * 60
    private const val DUSK_MINUTES = 30

    @JvmStatic
    fun timeOfDay(minute: Int, sunrise: Int, sunset: Int): String = when {
        minute >= sunrise && minute < NOON -> MORNING
        minute >= NOON && minute < sunset + DUSK_MINUTES -> EVENING
        else -> NIGHT
    }

    @JvmStatic
    fun sun(day: LocalDate, latitude: String?, longitude: String?, zone: ZoneId): IntArray {
        val lat = latitude?.trim { it <= ' ' }?.toDoubleOrNull()
        val lon = longitude?.trim { it <= ' ' }?.toDoubleOrNull()
        val times = if (lat != null && lon != null) Sun.riseAndSet(day, lat, lon, zone) else null
        return times ?: intArrayOf(DEFAULT_SUNRISE, DEFAULT_SUNSET)
    }

    @JvmStatic
    fun select(all: List<File>, groups: Map<String, Set<String>>, mood: String?): List<File> {
        val names = groups[mood] ?: return all
        val group = all.filter { it.name in names }
        return group.ifEmpty { all }
    }
}
