package com.sadique.launcher

import java.util.Locale

/** Device-independent rules for the personal home. Times follow the device clock, not location. */
object PersonalHomeModel {

    @JvmStatic
    fun time(hour: Int, minute: Int): String {
        val h = Math.floorMod(hour, 24)
        return String.format(
            Locale.ENGLISH, "%02d:%02d %s",
            if (h % 12 == 0) 12 else h % 12, Math.floorMod(minute, 60), if (h < 12) "AM" else "PM"
        )
    }

    @JvmStatic
    fun daylight(minute: Int): Float {
        val m = Math.floorMod(minute, 1440)
        if (m < 330 || m >= 1170) return 0f
        if (m < 450) return (m - 330) / 120f
        return if (m > 1050) (1170 - m) / 120f else 1f
    }

    @JvmStatic
    @JvmOverloads
    fun columns(widthDp: Int, preferred: Int, large: Boolean = false): Int {
        val fit = Math.max(2, Math.min(6, widthDp / if (large) 160 else 112))
        return if (preferred >= 2) Math.min(fit, preferred) else fit
    }

    @JvmStatic
    fun name(raw: String?): String {
        val name = raw?.trim() ?: ""
        val count = name.codePointCount(0, name.length)
        return if (count > 32) name.substring(0, name.offsetByCodePoints(0, 32)) else name
    }
}
