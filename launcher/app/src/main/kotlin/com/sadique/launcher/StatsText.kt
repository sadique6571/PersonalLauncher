package com.sadique.launcher

import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

/** Isolate complete measurements, not individual digits, in the Hebrew stats paragraph. */
object StatsText {
    private val MEASUREMENT = Pattern.compile("[0-9]+(?:[.,][0-9]+)?(?:\\s*/\\s*[0-9]+(?:[.,][0-9]+)?)?(?:\\s+(?:MB|GB)|%)")

    @JvmStatic
    fun forLocale(text: String, locale: Locale): String {
        val language = locale.language
        if (language != "he" && language != "iw") return text
        val matcher = MEASUREMENT.matcher(text)
        val isolated = StringBuffer()
        while (matcher.find()) {
            matcher.appendReplacement(isolated, Matcher.quoteReplacement("\u200E\u202A" + matcher.group() + "\u202C\u200E"))
        }
        matcher.appendTail(isolated)
        return "\u200F" + isolated.toString().replace("  ·  ", "\u200F  ·  \u200F")
    }
}
