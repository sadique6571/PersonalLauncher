package com.sadique.launcher

import android.view.KeyEvent
import java.util.ArrayList
import java.util.HashMap
import java.util.function.ToIntFunction
import java.util.regex.Pattern

internal object RemoteKeyMappings {
    private val NUMERIC_KEY = Pattern.compile("[0-9]{1,6}")
    private val KEYCODE_KEY = Pattern.compile("KEYCODE_[A-Z0-9_]+")

    /** keycode -> package, with key names resolved by Android. */
    fun parseAppKeys(entries: Array<String>): Map<Int, String> {
        return parseAppKeys(entries.asList(), KeyEvent::keyCodeFromString)
    }

    fun parseAppKeys(entries: Iterable<String>): Map<Int, String> {
        return parseAppKeys(entries, KeyEvent::keyCodeFromString)
    }

    /**
     * keycode -> package. The key is a number or a KEYCODE_ name (resolved by {@code names}; 0, KEYCODE_UNKNOWN,
     * means it isn't one). Entries without a colon, with an unknown key or with no package are skipped; for a
     * keycode listed twice the last entry wins.
     */
    fun parseAppKeys(entries: Iterable<String>, names: ToIntFunction<String>): Map<Int, String> {
        val keys = HashMap<Int, String>()
        for (entry in entries) {
            if (entry == null) continue
            val parts = entry.split(":", limit = 2)
            if (parts.size != 2 || parts[1].trim().isEmpty()) continue
            val key = parts[0].trim()
            val code: Int
            if (NUMERIC_KEY.matcher(key).matches()) {
                code = Integer.parseInt(key)
            } else if (KEYCODE_KEY.matcher(key).matches()) {
                code = names.applyAsInt(key) // keyCodeFromString takes digits only from API 29, names always
            } else {
                continue
            }
            if (code > 0) keys[code] = parts[1].trim()
        }
        return keys
    }

    /** keycode -> package from the "hold:" entries; the rest of each entry reads as it does in parseAppKeys. */
    fun parseHoldAppKeys(entries: Array<String>): Map<Int, String> {
        return parseHoldAppKeys(entries.asList(), KeyEvent::keyCodeFromString)
    }

    fun parseHoldAppKeys(entries: Iterable<String>): Map<Int, String> {
        return parseHoldAppKeys(entries, KeyEvent::keyCodeFromString)
    }

    fun parseHoldAppKeys(entries: Iterable<String>, names: ToIntFunction<String>): Map<Int, String> {
        return parseAppKeys(prefixed(entries, "hold:"), names)
    }

    /** keycode -> target from the "front:" entries, used while the button's own app is already in front. */
    fun parseFrontAppKeys(entries: Iterable<String>): Map<Int, String> {
        return parseFrontAppKeys(entries, KeyEvent::keyCodeFromString)
    }

    fun parseFrontAppKeys(entries: Iterable<String>, names: ToIntFunction<String>): Map<Int, String> {
        return parseAppKeys(prefixed(entries, "front:"), names)
    }

    fun parseFrontAppScancodes(entries: Iterable<String>): Map<Int, String> {
        return parseAppScancodes(prefixed(entries, "front:"))
    }

    /**
     * scancode -> package, from the "scan:490:com.wbd.hbomax" entries of the same list. For remotes that send
     * several app buttons as one keycode and tell them apart by scancode (Sony BRAVIA: YouTube, Prime Video and
     * Disney+ are all keycode 196, scancodes 485, 490 and 491). Find it with `getevent -lt` while pressing the
     * button: the EV_KEY code, in hex. Other entries are skipped; for a scancode listed twice the last entry wins.
     */
    fun parseAppScancodes(entries: Iterable<String>): Map<Int, String> {
        val keys = HashMap<Int, String>()
        for (entry in entries) {
            if (entry == null) continue
            val parts = entry.split(":", limit = 3)
            if (parts.size != 3 || parts[0].trim() != "scan" || parts[2].trim().isEmpty()) continue
            val code = parts[1].trim()
            if (!NUMERIC_KEY.matcher(code).matches()) continue
            val scan = Integer.parseInt(code)
            if (scan > 0) keys[scan] = parts[2].trim()
        }
        return keys
    }

    /** scancode -> package from the "hold:scan:..." entries; the rest reads as it does in parseAppScancodes. */
    fun parseHoldAppScancodes(entries: Iterable<String>): Map<Int, String> {
        return parseAppScancodes(prefixed(entries, "hold:"))
    }

    /**
     * Rewrites the two known-wrong scan:240 settings targets to the "home_settings" action, in place:
     * they opened a full-screen settings page (LauncherSettingsActivity) or the quick settings panel
     * (QuickSettingsActivity) instead of the Home settings popup. Only those exact targets are rewritten,
     * with or without a "hold:" prefix; every other entry — including a deliberate scan:240 mapping to
     * something else — is left untouched.
     */
    fun migrateScanSettingsKeys(entries: List<String>): List<String> {
        val out = ArrayList<String>(entries.size)
        for (entry in entries) out.add(migrateScanSettingsKey(entry) ?: entry)
        return out
    }

    private fun migrateScanSettingsKey(entry: String?): String? {
        if (entry == null) return null
        val text = entry.trim()
        var prefix = ""
        var rest = text
        if (rest.startsWith("hold:")) {
            prefix = "hold:"
            rest = rest.substring(5).trim()
        }
        if (rest == "scan:240:com.sadique.launcher/.LauncherSettingsActivity" ||
            rest == "scan:240:com.sadique.launcher/.QuickSettingsActivity"
        ) {
            return prefix + "scan:240:home_settings"
        }
        return entry
    }

    /** The entries with this prefix ("hold:", "front:"), the prefix removed, so the normal parsers can read them. */
    private fun prefixed(entries: Iterable<String>, prefix: String): Iterable<String> {
        val found = ArrayList<String>()
        for (entry in entries) {
            if (entry == null) continue
            val text = entry.trim()
            if (text.startsWith(prefix)) found.add(text.substring(prefix.length).trim())
        }
        return found
    }
}
