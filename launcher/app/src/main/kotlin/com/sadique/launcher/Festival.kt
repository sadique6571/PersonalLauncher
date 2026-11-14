package com.sadique.launcher

import android.content.res.Resources
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * A festive skin set by a theme's config.json: [festival], [until], [greeting]
 * and [accent]. While it is active the pills get festive icons (a diya for the network, a bell
 * for Inputs, a marigold for Settings), the greeting is replaced and the focus glow takes the accent colour.
 * It switches itself off after [until], so nobody has to remember to undo it.
 */
class Festival private constructor(
    /** Lower-case festival name, or "" for none. */
    @JvmField val name: String,
    /** Last day the skin shows, inclusive; null for no end date. */
    @JvmField val until: LocalDate?,
    /** Text that replaces the time-of-day greeting; "" uses the festival's own default. */
    @JvmField val greeting: String,
    /** Focus glow colour as 0xAARRGGBB, or 0 for [DEFAULT_ACCENT]. */
    private val accent: Int
) {

    /** Whether the skin shows today. */
    fun active(today: LocalDate?): Boolean {
        return name.isNotEmpty() && (until == null || !today!!.isAfter(until))
    }

    /** The greeting to show while active, or "" to keep the normal time-of-day greeting. */
    fun greetingText(res: Resources?): String {
        if (greeting.isNotEmpty()) return greeting
        return when (name) {
            "dussehra" -> res!!.getString(R.string.festival_dussehra_greeting)
            "diwali" -> res!!.getString(R.string.festival_diwali_greeting)
            else -> ""
        }
    }

    fun accentColor(): Int {
        return if (accent != 0) accent else DEFAULT_ACCENT
    }

    companion object {
        @JvmField
        val NONE = Festival("", null, "", 0)

        /** Warm diya gold, the accent when a theme names a festival but no colour. */
        const val DEFAULT_ACCENT = 0xFFFFB23C.toInt()

        /**
         * Builds the skin from raw config values. Anything malformed turns the skin off rather than leaving a
         * festival on the screen forever: an unreadable end date or an empty name gives [NONE].
         */
        @JvmStatic
        fun parse(name: String?, until: String?, greeting: String?, accent: String?): Festival {
            val n = name?.trim { it <= ' ' }?.lowercase(Locale.ROOT) ?: ""
            if (n.isEmpty() || n.length > 40) return NONE
            var end: LocalDate? = null
            if (until != null && until.trim { it <= ' ' }.isNotEmpty()) {
                try {
                    end = LocalDate.parse(until.trim { it <= ' ' })
                } catch (e: DateTimeParseException) {
                    return NONE
                }
            }
            val g = if (greeting == null) "" else Cards.cleanLabel(greeting, "")
            return Festival(n, end, g, parseColor(accent))
        }

        /** `#RRGGBB` to an opaque colour, anything else to 0. */
        @JvmStatic
        fun parseColor(s: String?): Int {
            if (s == null) return 0
            val t = s.trim { it <= ' ' }
            if (!t.matches("#[0-9A-Fa-f]{6}".toRegex())) return 0
            return 0xFF000000.toInt() or t.substring(1).toInt(16)
        }
    }
}
