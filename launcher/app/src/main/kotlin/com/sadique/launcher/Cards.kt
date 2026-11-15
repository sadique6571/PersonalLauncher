package com.sadique.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.Signature
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.os.Build
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.TreeMap
import java.util.function.Supplier
import org.xmlpull.v1.XmlPullParser

/**
 * Module cards (docs/specs/card.md): an installed app can declare a card for its tile, with its own
 * label, caption and glow colour. Nothing is shown until the TV owner says so on the prompt; the
 * decision is pinned to the app's signing certificate in the launcher's private storage.
 *
 * Discovery, parsing, the signer digest and the decision file live here, with no UI code, so the
 * part that reads untrusted input can be read on its own. Every module input is bounded and every
 * card is read inside its own try: a bad card is dropped with one log line, never a crash.
 *
 * No regular expressions and nothing in a static initialiser that can throw: the launcher's regex
 * engine (ICU) differs from the desktop JVM's, and a failure while loading this class would take
 * Home down. Every check is a plain character loop; see {@link CardFeature} for the guard around
 * every call into this class.
 */
internal class Cards private constructor() {

    enum class State {
        /** No record for this package: the prompt is due. */
        NEW,

        /** The record was made for a different signing key: it no longer applies and the prompt is due. */
        RESIGNED,

        /** The card's label changed since the owner decided: the decision was for another name, the prompt is due. */
        RELABELLED,
        SHOWN,
        HIDDEN
    }

    /** A valid card found on this TV, with the owner's decision for its current signer. */
    class Card(
        @JvmField val pkg: String,
        @JvmField val component: ComponentName?,
        @JvmField val label: String,
        /** The app's own label, the one Settings shows. */
        @JvmField val appLabel: String,
        /** Null when the card has none. */
        @JvmField val caption: String?,
        /** Opaque ARGB, or 0 when the card sets none. */
        @JvmField val accent: Int,
        /** SHA-256 of the signing certificate, 64 lower case hex characters. */
        @JvmField val digest: String,
        @JvmField val state: State
    ) {
        fun pending(): Boolean {
            return state == State.NEW || state == State.RESIGNED || state == State.RELABELLED
        }
    }

    /**
     * The owner's decision for one package, valid only while the package is signed with this key and
     * its card keeps the name (folded) it had when the owner decided.
     */
    class Record(
        @JvmField val pkg: String,
        @JvmField val digest: String,
        /** The card's label as {@link #fold} wrote it at decision time. */
        @JvmField val label: String,
        @JvmField val decision: String,
        @JvmField val firstSeen: Long,
        @JvmField val decidedAt: Long
    )

    /** The attributes of a `<card>` element after the limits were checked. */
    class Declared(
        @JvmField val spec: Int,
        @JvmField val label: String,
        @JvmField val caption: String?,
        @JvmField val accent: Int
    )

    /** A card rejected for a stated reason; the reason goes to logcat under {@link #TAG}. */
    class CardException(reason: String) : Exception(reason)

    companion object {
        /** One place for the names a module declares; see docs/specs/card.md. */
        const val CATEGORY = "com.sadique.launcher.category.DOCK_CARD"
        const val META_DATA = "com.sadique.launcher.card"
        const val SPEC_SUPPORTED = 1

        /** logcat tag for every rejection: `logcat -s TvHomeCards`. */
        const val TAG = "TvHomeCards"

        // Limits. Declared: cards read per resume. Shown: cards rendered as tiles. Records: decisions remembered.
        const val MAX_DECLARED = 32
        const val MAX_SHOWN = 8
        const val MAX_RECORDS = 128

        // XML: events walked and nesting depth before the card is rejected; attributes read on the root.
        const val MAX_EVENTS = 64
        const val MAX_DEPTH = 2
        const val MAX_ATTRIBUTES = 16
        const val LABEL_MAX = 24
        const val CAPTION_MAX = 32

        const val STORE_FILE = "cards.json"
        const val DECISION_SHOW = "show"
        const val DECISION_HIDE = "hide"

        /** Label and caption punctuation; letters, digits and the space are the rest of the alphabet (ASCII only). */
        private const val TEXT_PUNCTUATION = ".&+'-"

        /** Words that imply endorsement: rejected anywhere in a label (folded form). */
        private val RESERVED_ANYWHERE = arrayOf("verified", "official")

        /** Names the launcher's own UI renders (pills, tiles, the Inputs menu): rejected as a whole label. */
        private val RESERVED_EXACT = setOf(
            "personallauncher", "settings", "inputs", "livetv", "home", "defaulthome", "wifi", "av"
        )

        /** "HDMI", "HDMI 1", "HDMI2"... */
        private const val RESERVED_PREFIX = "hdmi"


        private var selfChecked = false

        // ---- discovery -------------------------------------------------------------------------------

        /**
         * Every valid card installed on this TV, with its state under the given records. One cheap
         * PackageManager query when no app declares a card; the app list is only walked when one does.
         * hidden is the launcher's hidden_apps as in force (config.xml or a theme overlay): no card for those.
         * The decision records are only read when some app declares a card, and leanback is the launcher's
         * own LEANBACK_LAUNCHER query, so the app list isn't queried twice.
         */
        @JvmStatic
        fun discover(
            ctx: Context, hidden: Set<String>, recordSource: Supplier<Map<String, Record>>,
            leanback: List<ResolveInfo>
        ): List<Card> {
            if ((ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) selfCheck()
            val out = ArrayList<Card>()
            val pm = ctx.packageManager
            val declared: List<ResolveInfo>
            try {
                declared = pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(CATEGORY),
                    PackageManager.GET_META_DATA
                )
            } catch (e: Exception) {
                Log.w(TAG, "card query failed: $e")
                return out
            }
            if (declared.isEmpty()) return out
            val records = recordSource.get()

            // What the card must agree with: the activities shown as tiles, and the names already on screen.
            val tiles = HashSet<String>()
            val names = HashMap<String, MutableSet<String>>()
            for (ri in leanback) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName
                tiles.add(ComponentName(pkg, ai.name).flattenToString())
                try {
                    addName(names, ri.loadLabel(pm).toString(), pkg)
                    addName(names, ai.applicationInfo.loadLabel(pm).toString(), pkg)
                } catch (e: Exception) {
                    Log.w(TAG, "$pkg: label unreadable, $e") // an app with broken resources; not a card problem
                }
            }
            val uiNames = uiNames(ctx.resources)
            val seen = HashSet<String>()

            var n = 0
            for (ri in declared) {
                if (++n > MAX_DECLARED) {
                    Log.w(TAG, "more than $MAX_DECLARED cards declared; the rest are ignored")
                    break
                }
                val pkg = ri.activityInfo?.packageName ?: "?"
                try {
                    val card = read(ctx, pm, ri, tiles, names, uiNames, hidden, seen, records)
                    seen.add(card.pkg)
                    out.add(card)
                } catch (e: CardException) {
                    Log.w(TAG, "$pkg: ${e.message}")
                } catch (e: Exception) {
                    // Package gone between the query and the read, a resource that is not XML, and anything else.
                    Log.w(TAG, "$pkg: $e")
                }
            }

            val kept = resolveClashes(out, records)
            for (c in out) {
                if (!kept.contains(c)) Log.w(TAG, "${c.pkg}: another card is also named '${c.label}'")
            }
            return kept
        }

        /**
         * Cards that share a folded label: the one the owner already decided on keeps its name (the
         * oldest decision wins, then the smaller package name), every other card in the group is
         * dropped. A group without a decided card is dropped entirely, so nobody is asked about a
         * contested name. A decision the owner made is never undone by a newer install.
         */
        @JvmStatic
        fun resolveClashes(cards: List<Card>, records: Map<String, Record>): List<Card> {
            val groups = HashMap<String, MutableList<Card>>()
            for (c in cards) {
                groups.getOrPut(fold(c.label)) { ArrayList() }.add(c)
            }
            val kept = ArrayList<Card>()
            for (c in cards) {
                val group = groups.getValue(fold(c.label))
                if (group.size == 1) {
                    kept.add(c)
                    continue
                }
                var winner: Card? = null
                for (g in group) {
                    if (g.pending()) continue
                    if (winner == null || olderDecision(g, winner, records)) winner = g
                }
                if (winner == c) kept.add(c)
            }
            return kept
        }

        /** True when a's record was made before b's (tie: smaller package name). */
        private fun olderDecision(a: Card, b: Card, records: Map<String, Record>): Boolean {
            val ra = records[a.pkg]
            val rb = records[b.pkg]
            val fa = ra?.firstSeen ?: Long.MAX_VALUE
            val fb = rb?.firstSeen ?: Long.MAX_VALUE
            if (fa != fb) return fa < fb
            return a.pkg.compareTo(b.pkg) < 0
        }

        private fun read(
            ctx: Context, pm: PackageManager, ri: ResolveInfo, tiles: Set<String>,
            names: Map<String, Set<String>>, uiNames: Set<String>, hidden: Set<String>,
            seen: Set<String>, records: Map<String, Record>
        ): Card {
            val ai: ActivityInfo = ri.activityInfo ?: throw CardException("not an activity")
            val pkg = ai.packageName
            if (pkg == ctx.packageName) throw CardException("the launcher itself")
            if (hidden.contains(pkg)) throw CardException("listed in hidden_apps")
            if (seen.contains(pkg)) throw CardException("declares a second card; one per package is read")
            if (!ai.exported || !ai.enabled || !ai.applicationInfo.enabled) {
                throw CardException("card activity is not exported and enabled")
            }
            val component = ComponentName(pkg, ai.name)
            if (!tiles.contains(component.flattenToString())) {
                throw CardException("card activity has no LEANBACK_LAUNCHER intent filter")
            }

            val xml: XmlResourceParser = ai.loadXmlMetaData(pm, META_DATA)
                ?: throw CardException("no <meta-data android:name=\"$META_DATA\" android:resource=...>")
            val d: Declared
            try {
                d = parse(xml)
            } finally {
                xml.close()
            }
            if (d.spec != SPEC_SUPPORTED) throw CardException("spec ${d.spec} is not supported (this launcher reads spec $SPEC_SUPPORTED)")

            val sigDigest = digest(pm, pkg)
            val folded = fold(d.label)
            val r = records[pkg]
            val state: State
            if (r == null) state = State.NEW
            else if (r.digest != sigDigest) state = State.RESIGNED
            else if (r.label != folded) state = State.RELABELLED // the owner said yes to another name
            else state = if (DECISION_SHOW == r.decision) State.SHOWN else State.HIDDEN
            val pending = state == State.NEW || state == State.RESIGNED || state == State.RELABELLED

            // Reserved words are policy and always apply. Name collisions only matter while the prompt is
            // due: they stop a newcomer from borrowing a name, and never undo a decision the owner made.
            // A decided card that changes its label becomes pending again (RELABELLED), so it cannot take
            // another app's name on an update either.
            if (isReserved(folded, uiNames)) throw CardException("label '${d.label}' is reserved")
            if (pending && isTaken(folded, pkg, names)) throw CardException("label '${d.label}' is the name of another installed app")
            val appLabel = cleanLabel(ai.applicationInfo.loadLabel(pm).toString(), pkg)
            if (pending && isTaken(fold(appLabel), pkg, names)) throw CardException("app label '$appLabel' is the name of another installed app")

            return Card(pkg, component, d.label, appLabel, d.caption, d.accent, sigDigest, state)
        }

        private fun addName(names: MutableMap<String, MutableSet<String>>, label: String, pkg: String) {
            val key = fold(label)
            if (key.isEmpty()) return
            names.getOrPut(key) { HashSet() }.add(pkg)
        }

        /** The launcher's own UI names in the TV's language, folded. */
        private fun uiNames(res: Resources): Set<String> {
            val ids = intArrayOf(
                R.string.app_name, R.string.settings, R.string.inputs, R.string.choose_input, R.string.wifi,
                R.string.live_tv, R.string.home_settings, R.string.app_library, R.string.dream_name
            )
            val out = HashSet<String>()
            for (id in ids) out.add(fold(res.getString(id)))
            return out
        }

        /** Shown cards in the order they take on screen: by label, at most {@link #MAX_SHOWN}. */
        @JvmStatic
        fun shown(cards: List<Card>): List<Card> {
            val out = ArrayList<Card>()
            for (c in cards) if (c.state == State.SHOWN) out.add(c)
            Collections.sort(out, Comparator { a, b -> byLabel(a, b) })
            return if (out.size > MAX_SHOWN) ArrayList(out.subList(0, MAX_SHOWN)) else out
        }

        /** The card whose prompt is due next, or null. Package order, so repeated prompts come in one order. */
        @JvmStatic
        fun firstPending(cards: List<Card>): Card? {
            var best: Card? = null
            for (c in cards) {
                if (c.pending() && (best == null || c.pkg.compareTo(best.pkg) < 0)) best = c
            }
            return best
        }

        @JvmStatic
        fun byLabel(a: Card, b: Card): Int {
            val c = a.label.compareTo(b.label, ignoreCase = true)
            return if (c != 0) c else a.pkg.compareTo(b.pkg)
        }

        // ---- parser ----------------------------------------------------------------------------------

        /**
         * Reads one `<card spec="1" label=".." caption=".." accent="#RRGGBB"/>`. The document may hold at
         * most {@link #MAX_EVENTS} events and nest at most {@link #MAX_DEPTH} deep; child elements and
         * unknown attributes are ignored. Rejects, never guesses.
         */
        @JvmStatic
        fun parse(p: XmlPullParser): Declared {
            var events = 0
            var found: Declared? = null
            var type = p.eventType
            while (type != XmlPullParser.END_DOCUMENT) {
                if (++events > MAX_EVENTS) throw CardException("more than $MAX_EVENTS XML events")
                if (type == XmlPullParser.START_TAG) {
                    if (p.depth > MAX_DEPTH) throw CardException("XML nested deeper than $MAX_DEPTH")
                    if (p.depth == 1) {
                        if (found != null) throw CardException("more than one root element")
                        val name = p.name
                        if ("card" != name) throw CardException("root element is <$name>, expected <card>")
                        found = readCard(p)
                    }
                }
                type = p.next()
            }
            if (found == null) throw CardException("no <card> element")
            return found
        }

        private fun readCard(p: XmlPullParser): Declared {
            val count = p.attributeCount
            if (count > MAX_ATTRIBUTES) throw CardException("more than $MAX_ATTRIBUTES attributes on <card>")
            var spec: String? = null
            var label: String? = null
            var caption: String? = null
            var accent: String? = null
            for (i in 0 until count) {
                val ns = p.getAttributeNamespace(i)
                if (ns != null && ns.isNotEmpty()) continue
                val name = p.getAttributeName(i)
                if (name == null || name.length > 8) continue
                when (name) {
                    "spec" -> spec = p.getAttributeValue(i)
                    "label" -> label = p.getAttributeValue(i)
                    "caption" -> caption = p.getAttributeValue(i)
                    "accent" -> accent = p.getAttributeValue(i)
                    else -> {}
                }
            }
            // Lengths first, before any pattern or comparison touches the value.
            // The compiler stores spec="1" as an integer; the parser hands it back as the decimal text "1".
            if (spec == null || spec.isEmpty() || spec.length > 3 || !isDigits(spec)) throw CardException("spec is missing or not a small integer")
            var reason = checkLabel(label)
            if (reason != null) throw CardException(reason)
            reason = checkCaption(caption)
            if (reason != null) throw CardException(reason)
            var colour = 0
            if (accent != null && accent.isNotEmpty()) {
                if (isReference(accent)) throw CardException("accent is a resource reference; write #RRGGBB itself")
                colour = parseAccent(accent)
                if (colour == 0) throw CardException("accent is not #RRGGBB")
            }
            // checkLabel returns a reason for a null label, so reaching here means label is non-null.
            return Declared(Integer.parseInt(spec), label!!, if (caption == null || caption.isEmpty()) null else caption, colour)
        }

        // ---- validators (pure Java, unit tested) -----------------------------------------------------

        /** Null when the label is acceptable, else the reason. */
        @JvmStatic
        fun checkLabel(label: String?): String? {
            if (label == null || label.isEmpty()) return "label is missing"
            if (label.length > LABEL_MAX) return "label is longer than $LABEL_MAX characters"
            if (isReference(label)) return "label is a resource reference; write the text itself"
            if (!isText(label)) return "label has characters outside A-Z a-z 0-9 space . & + ' -"
            if (label[0] == ' ' || label[label.length - 1] == ' ') return "label starts or ends with a space"
            if (fold(label).isEmpty()) return "label has no letter or digit"
            return null
        }

        /** Null when the caption is acceptable (absent counts as acceptable), else the reason. */
        @JvmStatic
        fun checkCaption(caption: String?): String? {
            if (caption == null || caption.isEmpty()) return null
            if (caption.length > CAPTION_MAX) return "caption is longer than $CAPTION_MAX characters"
            if (isReference(caption)) return "caption is a resource reference; write the text itself"
            if (!isText(caption)) return "caption has characters outside A-Z a-z 0-9 space . & + ' -"
            if (caption[0] == ' ' || caption[caption.length - 1] == ' ') return "caption starts or ends with a space"
            val folded = fold(caption)
            for (word in RESERVED_ANYWHERE) if (folded.contains(word)) return "caption claims endorsement ('$word')"
            return null
        }

        /**
         * An app's own label as the prompt shows it: control, format (bidi overrides, zero width joiners),
         * line and paragraph separator characters become spaces, runs of spaces collapse, at most 40
         * characters (never cutting a surrogate pair), the fallback (package name) when nothing is left.
         */
        @JvmStatic
        fun cleanLabel(s: String?, fallback: String): String {
            if (s == null) return fallback
            val b = StringBuilder(minOf(s.length, 48))
            var space = true // leading spaces are dropped too
            var i = 0
            while (i < s.length) {
                val c = s[i]
                val type = Character.getType(c)
                val blank = c == ' ' || Character.isISOControl(c) || Character.isWhitespace(c) || type == Character.FORMAT.toInt() ||
                    type == Character.LINE_SEPARATOR.toInt() || type == Character.PARAGRAPH_SEPARATOR.toInt() ||
                    type == Character.SPACE_SEPARATOR.toInt()
                if (blank) {
                    if (!space) b.append(' ')
                    space = true
                    i++
                    continue
                }
                val pair = Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1])
                if (b.length + (if (pair) 2 else 1) > 40) break
                b.append(c)
                if (pair) b.append(s[++i])
                space = false
                i++
            }
            val out = b.toString().trim()
            return if (out.isEmpty()) fallback else out
        }

        /**
         * A compiled `@string/x` or `?attr/x` reference: the resource compiler stores it as an id and the
         * parser hands back "@2131..." or "?2130...". Spec 1 takes literal text only.
         */
        @JvmStatic
        fun isReference(value: String): Boolean {
            return value.startsWith("@") || value.startsWith("?")
        }

        /** Lower case with everything but ASCII letters and digits removed: the form every name comparison uses. */
        @JvmStatic
        fun fold(s: String?): String {
            if (s == null) return ""
            val b = StringBuilder(s.length)
            for (i in s.indices) {
                val c = Character.toLowerCase(s[i])
                if ((c in 'a'..'z') || (c in '0'..'9')) b.append(c)
            }
            return b.toString()
        }

        /** True when a folded label claims endorsement or a name the launcher's UI uses (uiNames are folded too). */
        @JvmStatic
        fun isReserved(folded: String, uiNames: Set<String>): Boolean {
            if (folded.isEmpty()) return true
            for (word in RESERVED_ANYWHERE) if (folded.contains(word)) return true
            if (folded.startsWith(RESERVED_PREFIX)) return true
            return RESERVED_EXACT.contains(folded) || uiNames.contains(folded)
        }

        /** True when another package than pkg already uses this folded name. */
        @JvmStatic
        fun isTaken(folded: String, pkg: String, names: Map<String, Set<String>>): Boolean {
            val pkgs = names[folded] ?: return false
            for (p in pkgs) if (p != pkg) return true
            return false
        }

        /** Opaque ARGB for `#RRGGBB` (or the compiled `#AARRGGBB` form, alpha dropped), 0 when it is anything else. */
        @JvmStatic
        fun parseAccent(s: String?): Int {
            if (s == null || (s.length != 7 && s.length != 9) || s[0] != '#') return 0
            val hex = s.substring(1)
            if (!isHex(hex, false)) return 0
            // `#RRGGBB` as written, or `#AARRGGBB` as the resource compiler stores a colour attribute (it
            // compiles `#E8A33D` to the colour value and the parser hands back `#ffe8a33d`). The alpha is dropped.
            return 0xFF000000.toInt() or Integer.parseInt(hex.substring(hex.length - 6), 16)
        }

        // ---- character checks (no regular expressions, see the class comment) -----------------------

        /** Every character is an ASCII letter, digit, space or one of {@link #TEXT_PUNCTUATION}. */
        @JvmStatic
        fun isText(s: String): Boolean {
            for (i in s.indices) {
                val c = s[i]
                val ok = (c in 'A'..'Z') || (c in 'a'..'z') || (c in '0'..'9') || c == ' ' ||
                    TEXT_PUNCTUATION.indexOf(c) >= 0
                if (!ok) return false
            }
            return true
        }

        @JvmStatic
        fun isDigits(s: String): Boolean {
            if (s.isEmpty()) return false
            for (i in s.indices) {
                val c = s[i]
                if (c < '0' || c > '9') return false
            }
            return true
        }

        /** Hex digits; lower case only when lowerOnly (the decision file keeps digests lower case). */
        @JvmStatic
        fun isHex(s: String, lowerOnly: Boolean): Boolean {
            if (s.isEmpty()) return false
            for (i in s.indices) {
                val c = s[i]
                val ok = (c in '0'..'9') || (c in 'a'..'f') || (!lowerOnly && c in 'A'..'F')
                if (!ok) return false
            }
            return true
        }

        /** A Java style package name: two or more dot separated segments, each a letter then letters, digits or _. */
        @JvmStatic
        fun isPackageName(s: String?): Boolean {
            if (s == null || s.isEmpty() || s.length > 255) return false
            var segments = 0
            var i = 0
            while (i < s.length) {
                var c = s[i]
                if (!((c in 'A'..'Z') || (c in 'a'..'z'))) return false
                i++
                while (i < s.length && s[i] != '.') {
                    c = s[i]
                    if (!((c in 'A'..'Z') || (c in 'a'..'z') || (c in '0'..'9') || c == '_')) return false
                    i++
                }
                segments++
                if (i < s.length) i++ // skip the dot; a trailing dot leaves an empty segment below
                else break
                if (i == s.length) return false
            }
            return segments >= 2
        }

        @JvmStatic
        fun isDigest(s: String?): Boolean {
            return s != null && s.length == 64 && isHex(s, true)
        }

        /** A label exactly as {@link #fold} writes it: 1 to LABEL_MAX characters from a-z 0-9. */
        @JvmStatic
        fun isFolded(s: String?): Boolean {
            if (s == null || s.isEmpty() || s.length > LABEL_MAX) return false
            for (i in s.indices) {
                val c = s[i]
                if (!((c in 'a'..'z') || (c in '0'..'9'))) return false
            }
            return true
        }

        @JvmStatic
        fun hex(bytes: ByteArray): String {
            val digits = "0123456789abcdef".toCharArray()
            val out = CharArray(bytes.size * 2)
            for (i in bytes.indices) {
                out[i * 2] = digits[(bytes[i].toInt() shr 4) and 0xF]
                out[i * 2 + 1] = digits[bytes[i].toInt() and 0xF]
            }
            return String(out)
        }

        /** The first 12 hex characters in groups of four, as shown on the prompt: "7f3a 91c0 b2e4". */
        @JvmStatic
        fun shortDigest(digest: String): String {
            return grouped(if (digest.length >= 12) digest.substring(0, 12) else digest, 3)
        }

        /** The whole digest in groups of four, eight groups per line, as shown in the Module cards list. */
        @JvmStatic
        fun longDigest(digest: String): String {
            return grouped(digest, 8)
        }

        private fun grouped(hex: String, groupsPerLine: Int): String {
            val b = StringBuilder(hex.length + hex.length / 4 + 2)
            var i = 0
            while (i < hex.length) {
                if (i > 0) b.append(if (i / 4 % groupsPerLine == 0) '\n' else ' ')
                b.append(hex, i, minOf(hex.length, i + 4))
                i += 4
            }
            return b.toString()
        }

        // ---- signer ----------------------------------------------------------------------------------

        /**
         * SHA-256 of the DER certificate of the package's one signer: the value apksigner verify (with
         * print-certs) prints as the certificate SHA-256 digest. A package with more than one signer
         * is rejected, because the order of its certificates is not defined.
         */
        @JvmStatic
        @Suppress("DEPRECATION")
        fun digest(pm: PackageManager, pkg: String): String {
            val sigs: Array<Signature>?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val info = pi.signingInfo ?: throw CardException("package has no signing certificate")
                if (info.hasMultipleSigners()) throw CardException("package has more than one signer")
                sigs = info.apkContentsSigners
            } else {
                val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                sigs = pi.signatures
            }
            if (sigs == null || sigs.size != 1) {
                throw CardException("package has " + (sigs?.size ?: 0) + " signing certificates, expected one")
            }
            return hex(MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray()))
        }

        @JvmStatic
        fun storeFile(ctx: Context): File = CardDecisionStore.storeFile(ctx)

        @JvmStatic
        fun load(file: File): Map<String, Record> = CardDecisionStore.load(file)

        @JvmStatic
        fun save(file: File, records: Map<String, Record>): Boolean = CardDecisionStore.save(file, records)

        @JvmStatic
        fun decide(file: File, card: Card, show: Boolean): Boolean = CardDecisionStore.decide(file, card, show)

        @JvmStatic
        fun quarantine(file: File): Boolean = CardDecisionStore.quarantine(file)

        @JvmStatic
        fun decode(text: String?): Map<String, Record>? = CardDecisionStore.decode(text)

        @JvmStatic
        fun decodeRecord(line: String): Record? = CardDecisionStore.decodeRecord(line)

        @JvmStatic
        fun encode(records: Map<String, Record>): String = CardDecisionStore.encode(records)

        @JvmStatic
        fun englishUiNames(): Set<String> {
            return hashSetOf(
                fold("Home"), fold("Settings"), fold("Inputs"), fold("Wi-Fi"),
                fold("Live TV"), fold("Default home"), fold("Clock")
            )
        }

        // ---- self-check ------------------------------------------------------------------------------

        /**
         * Debuggable builds only, once per process: runs the validators and the decision file codec on fixed
         * input and logs "self-check ok" or what differs, so a device run shows at once whether this
         * class behaves on the TV's runtime the way the JVM tests say it does.
         */
        @JvmStatic
        fun selfCheck() {
            if (selfChecked) return
            selfChecked = true
            val bad = ArrayList<String>()
            if (checkLabel("Retro") != null) bad.add("label Retro rejected")
            if (checkLabel("Verified\u202E") == null) bad.add("control character accepted")
            if (checkCaption("NES games") != null) bad.add("caption rejected")
            if (parseAccent("#E8A33D") != 0xFFE8A33D.toInt() || parseAccent("#ffe8a33d") != 0xFFE8A33D.toInt()) bad.add("accent")
            if ("livetv" != fold("Live TV") || !isReserved("hdmi2", englishUiNames())) bad.add("fold or reserved")
            if ("Netflix" != cleanLabel("Netflix\u202E\n", "x")) bad.add("cleanLabel")
            if (!isPackageName("io.example.a") || isPackageName("example")) bad.add("package name")
            val one = TreeMap<String, Record>()
            val digest = hex(ByteArray(32))
            one["io.example.a"] = Record("io.example.a", digest, "a", DECISION_SHOW, 1, 2)
            val back = decode(encode(one))
            if (back == null || back.size != 1 || back["io.example.a"]!!.decidedAt != 2L) bad.add("store round trip")
            if (decode("{}") != null) bad.add("bad store accepted")
            Log.w(TAG, if (bad.isEmpty()) "self-check ok" else "self-check FAILED: $bad")
        }
    }
}
