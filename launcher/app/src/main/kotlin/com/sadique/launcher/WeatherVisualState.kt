package com.sadique.launcher

/** Groups documented weather codes and leaves unrecognized codes at UNKNOWN. */
object WeatherVisualState {
    const val UNKNOWN = 0
    const val CLEAR = 1
    const val PARTLY = 2
    const val CLOUD = 3
    const val FOG = 4
    const val RAIN = 5
    const val SNOW = 6
    const val STORM = 7

    @JvmStatic
    fun kind(code: Int): Int = when (code) {
        0, 1 -> CLEAR
        2 -> PARTLY
        3 -> CLOUD
        45, 48 -> FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
        71, 73, 75, 77, 85, 86 -> SNOW
        95, 96, 99 -> STORM
        else -> UNKNOWN
    }

    @JvmStatic
    fun drops(code: Int): Int = when (code) {
        65, 67, 82 -> 42
        51, 53, 56 -> 12
        else -> when (kind(code)) {
            STORM -> 42
            RAIN -> 26
            else -> 0
        }
    }
}
