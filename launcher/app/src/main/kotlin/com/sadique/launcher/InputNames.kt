package com.sadique.launcher

import java.util.Locale

/**
 * Philips (TP Vision) saves an input's name from its Sources menu as "Name&TYPE".
 */
object InputNames {

    @JvmStatic
    fun tidy(raw: CharSequence?): String? {
        if (raw == null) return null
        val s = raw.toString().trim()
        if (s.isEmpty() || s == "null") return null
        val amp = s.lastIndexOf('&')
        if (amp < 0 || !s.substring(amp + 1).matches("[A-Z][A-Z0-9_]{2,}".toRegex())) return s
        val name = s.substring(0, amp).trim()
        val emoji = emoji(s.substring(amp + 1))
        if (name.isEmpty()) return emoji
        return if (emoji == null) name else "$emoji $name"
    }

    private fun emoji(type: String): String? {
        val t = type.uppercase(Locale.ROOT)
        if (t.contains("GAME")) return "🎮"
        if (t.contains("COMPUTER") || t == "PC") return "💻"
        if (t.contains("BLU") || t.contains("DVD") || t.contains("DISC")) return "📀"
        if (t.contains("BOX") || t.contains("STB") || t.contains("SAT") || t.contains("CABLE")) return "📡"
        if (t.contains("AUDIO") || t.contains("SOUND") || t.contains("AMP")) return "🔊"
        if (t.contains("CAM")) return "📷"
        return null
    }
}
