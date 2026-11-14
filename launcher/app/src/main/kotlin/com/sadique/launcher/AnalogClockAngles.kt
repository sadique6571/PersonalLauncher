package com.sadique.launcher

/** Fractional seconds drive every hand; the renderer never advances by one-second steps. */
object AnalogClockAngles {
    @JvmStatic
    fun seconds(second: Int, millis: Int): Float {
        return 6f * (second + millis / 1000f)
    }

    @JvmStatic
    fun minutes(minute: Int, second: Int, millis: Int): Float {
        return 6f * (minute + (second + millis / 1000f) / 60f)
    }

    @JvmStatic
    fun hours(hour: Int, minute: Int, second: Int, millis: Int): Float {
        return 30f * ((hour % 12) + (minute + (second + millis / 1000f) / 60f) / 60f)
    }
}
