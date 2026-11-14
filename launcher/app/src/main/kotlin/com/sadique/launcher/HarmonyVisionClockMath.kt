package com.sadique.launcher

/** Angular continuity for the three Harmony watch faces. */
object HarmonyVisionClockMath {
    @JvmStatic
    fun clampFace(face: Int): Int = face.coerceIn(0, 2)

    @JvmStatic
    fun secondAngle(second: Int, millis: Int, smooth: Boolean): Double {
        return ((second + if (smooth) fractionalSecond(millis) else 0.0) % 60) * 6
    }

    @JvmStatic
    fun minuteAngle(minute: Int, second: Int, millis: Int): Double {
        return (minute + (second + fractionalSecond(millis)) / 60) * 6
    }

    @JvmStatic
    fun hourAngle(hour: Int, minute: Int, second: Int, millis: Int): Double {
        return (hour % 12 + (minute + (second + fractionalSecond(millis)) / 60) / 60) * 30
    }

    private fun fractionalSecond(millis: Int): Double = millis.coerceIn(0, 999) / 1000.0
}
