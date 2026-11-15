package com.sadique.launcher

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Collections
import java.util.TreeMap
import com.sadique.launcher.Cards.Card
import com.sadique.launcher.Cards.Record
import com.sadique.launcher.Cards.Companion.STORE_FILE
import com.sadique.launcher.Cards.Companion.TAG
import com.sadique.launcher.Cards.Companion.MAX_RECORDS
import com.sadique.launcher.Cards.Companion.DECISION_SHOW
import com.sadique.launcher.Cards.Companion.DECISION_HIDE
import com.sadique.launcher.Cards.Companion.fold
import com.sadique.launcher.Cards.Companion.isPackageName
import com.sadique.launcher.Cards.Companion.isDigest
import com.sadique.launcher.Cards.Companion.isDigits
import com.sadique.launcher.Cards.Companion.isFolded

internal object CardDecisionStore {
    private const val HEADER = "{\"version\":2,\"records\":["
    private const val FOOTER = "]}"
    private const val F_PACKAGE = "{\"package\":\""
    private const val F_DIGEST = "\",\"signer_sha256\":\""
    private const val F_LABEL = "\",\"label\":\""
    private const val F_DECISION = "\",\"decision\":\""
    private const val F_FIRST_SEEN = "\",\"first_seen\":"
    private const val F_DECIDED_AT = ",\"decided_at\":"
    // ---- decision file (cards.json) --------------------------------------------------------------

    fun storeFile(ctx: Context): File {
        return File(ctx.filesDir, STORE_FILE)
    }

    /**
     * The records in cards.json, or an empty map. A file that does not decode is moved aside to
     * cards.json.bad (kept, not deleted) and the launcher continues as if no decision had been made.
     */
    fun load(file: File): Map<String, Record> {
        if (!file.exists()) return TreeMap()
        val text: String
        try {
            text = String(AtomicFile(file).readFully(), StandardCharsets.UTF_8)
        } catch (e: IOException) {
            Log.w(TAG, "cannot read " + file.name + ": " + e)
            return TreeMap()
        }
        val records = decode(text)
        if (records == null) {
            Log.w(TAG, file.name + " is not valid; moved to " + STORE_FILE + ".bad")
            quarantine(file)
            return TreeMap()
        }
        return records
    }

    /** Writes the records atomically. False (and a log line) when the write failed. */
    fun save(file: File, records: Map<String, Record>): Boolean {
        val af = AtomicFile(file)
        var out: FileOutputStream? = null
        try {
            val stream = af.startWrite()
            out = stream
            stream.write(encode(records).toByteArray(StandardCharsets.UTF_8))
            af.finishWrite(stream)
            return true
        } catch (e: IOException) {
            Log.w(TAG, "cannot write " + file.name + ": " + e)
            if (out != null) af.failWrite(out)
            return false
        }
    }

    /** Records the owner's decision for the card's package under its current signer and saves. */
    fun decide(file: File, card: Card, show: Boolean): Boolean {
        val records = load(file).toMutableMap()
        val old = records[card.pkg]
        val now = System.currentTimeMillis()
        val firstSeen = if (old != null && old.digest == card.digest) old.firstSeen else now
        records[card.pkg] = Record(card.pkg, card.digest, fold(card.label), if (show) DECISION_SHOW else DECISION_HIDE, firstSeen, now)
        return save(file, records)
    }

    /** Moves a bad decision file to `<name>.bad`, replacing an older .bad. Returns true when the file is out of the way. */
    fun quarantine(file: File): Boolean {
        val bad = File(file.parentFile, file.name + ".bad")
        if (bad.exists() && !bad.delete()) return false
        return file.renameTo(bad) || file.delete()
    }

    /**
     * cards.json: one record per line, in the exact layout {@link #encode} writes. Anything else,
     * including more than {@link #MAX_RECORDS} records or a package listed twice, is invalid (null).
     */
    fun decode(text: String?): Map<String, Record>? {
        if (text == null || text.length > 64 * 1024) return null
        val lines = ArrayList<String>()
        var start = 0
        for (i in 0..text.length) {
            if (i == text.length || text[i] == '\n') {
                lines.add(text.substring(start, i))
                start = i + 1
            }
        }
        if (lines.size < 2 || lines[0] != HEADER) return null
        var last = lines.size - 1
        if (lines[last].isEmpty()) last -= 1 // a trailing newline is fine
        if (last < 1 || lines[last] != FOOTER) return null
        val count = last - 1
        if (count > MAX_RECORDS) return null
        val out = TreeMap<String, Record>()
        for (i in 1 until last) {
            var line = lines[i]
            val lastRecord = i == last - 1
            if (!lastRecord) {
                if (!line.endsWith(",")) return null
                line = line.substring(0, line.length - 1)
            }
            val r = decodeRecord(line)
            if (r == null || out.containsKey(r.pkg)) return null
            out[r.pkg] = r
        }
        return out
    }

    /** One record line, or null when it is not exactly what {@link #encode} writes. */
    fun decodeRecord(line: String): Record? {
        val at = intArrayOf(0)
        if (!take(line, at, F_PACKAGE)) return null
        val pkg = until(line, at, '"')
        if (pkg == null || !isPackageName(pkg) || !take(line, at, F_DIGEST)) return null
        val digest = until(line, at, '"')
        if (digest == null || !isDigest(digest) || !take(line, at, F_LABEL)) return null
        val label = until(line, at, '"')
        if (label == null || !isFolded(label) || !take(line, at, F_DECISION)) return null
        val decision = until(line, at, '"')
        if (decision == null || !(DECISION_SHOW == decision || DECISION_HIDE == decision)) return null
        if (!take(line, at, F_FIRST_SEEN)) return null
        val firstSeen = until(line, at, ',')
        if (firstSeen == null || firstSeen.length > 18 || !isDigits(firstSeen)) return null
        if (!take(line, at, F_DECIDED_AT)) return null
        val decidedAt = until(line, at, '}')
        if (decidedAt == null || decidedAt.length > 18 || !isDigits(decidedAt)) return null
        if (!take(line, at, "}") || at[0] != line.length) return null
        return Record(pkg, digest, label, decision, firstSeen.toLong(), decidedAt.toLong())
    }

    /** Consumes the literal at the cursor, or returns false. */
    private fun take(s: String, at: IntArray, literal: String): Boolean {
        if (!s.startsWith(literal, at[0])) return false
        at[0] += literal.length
        return true
    }

    /** The text from the cursor up to the next stop character, which stays for the next literal; null when there is none. */
    private fun until(s: String, at: IntArray, stop: Char): String? {
        val end = s.indexOf(stop, at[0])
        if (end < 0) return null
        val out = s.substring(at[0], end)
        at[0] = end
        return out
    }

    /** The decision file text for these records, by package; the oldest decisions are dropped beyond {@link #MAX_RECORDS}. */
    fun encode(records: Map<String, Record>): String {
        var list = ArrayList(records.values)
        if (list.size > MAX_RECORDS) {
            Collections.sort(list, Comparator { a, b -> java.lang.Long.compare(b.decidedAt, a.decidedAt) })
            list = ArrayList(list.subList(0, MAX_RECORDS))
        }
        Collections.sort(list, Comparator { a, b -> a.pkg.compareTo(b.pkg) })
        val b = StringBuilder(HEADER).append('\n')
        for (i in list.indices) {
            val r = list[i]
            // Every field is validated before it gets here, so no JSON escaping is ever needed.
            if (!isPackageName(r.pkg) || !isDigest(r.digest) || !isFolded(r.label) ||
                !(DECISION_SHOW == r.decision || DECISION_HIDE == r.decision) ||
                r.firstSeen < 0 || r.decidedAt < 0
            ) {
                throw IllegalArgumentException("record for ${r.pkg} is not valid")
            }
            b.append(F_PACKAGE).append(r.pkg).append(F_DIGEST).append(r.digest).append(F_LABEL).append(r.label).append(F_DECISION).append(r.decision)
                .append(F_FIRST_SEEN).append(r.firstSeen).append(F_DECIDED_AT).append(r.decidedAt).append('}')
            if (i < list.size - 1) b.append(',')
            b.append('\n')
        }
        return b.append(FOOTER).append('\n').toString()
    }
}
