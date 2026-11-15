package com.sadique.launcher

import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.TreeMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

/** Plain JVM tests for the module card validators, the parser limits and the decision file format. */
class CardsTest {

    companion object {
        private const val DIGEST_A = "7f3a91c0b2e4000000000000000000000000000000000000000000000000abcd"
        private const val DIGEST_B = "0202281bb726dbc12b82622dee50a278656128a82204db3cf055f8f5c006e405"

        private fun rejects(events: List<Event>, reasonPart: String) {
            try {
                Cards.parse(xml(events))
                fail("expected rejection containing '" + reasonPart + "'")
            } catch (e: Cards.CardException) {
                assertTrue(e.message, e.message!!.contains(reasonPart))
            } catch (e: Exception) {
                fail("expected CardException, got " + e)
            }
        }

        private fun card(pkg: String, label: String, state: Cards.State): Cards.Card {
            return Cards.Card(pkg, null, label, label, null, 0, DIGEST_A, state)
        }

        private fun recordsFor(vararg pkgAndFirstSeen: Any): Map<String, Cards.Record> {
            val m = TreeMap<String, Cards.Record>()
            for (i in pkgAndFirstSeen.indices step 2) {
                val pkg = pkgAndFirstSeen[i] as String
                val seen = (pkgAndFirstSeen[i + 1] as Number).toLong()
                m[pkg] = Cards.Record(pkg, DIGEST_A, "games", "show", seen, seen)
            }
            return m
        }

        private fun one(pkg: String, digest: String, decision: String): MutableMap<String, Cards.Record> {
            val m = TreeMap<String, Cards.Record>()
            m[pkg] = Cards.Record(pkg, digest, "a", decision, 1700000000000L, 1700000000000L)
            return m
        }

        private fun doc(): Doc {
            val d = Doc()
            d.events.add(Event(XmlPullParser.START_DOCUMENT, 0, null, null))
            return d
        }

        private fun attrs(vararg kv: String): Map<String, String> {
            val m = LinkedHashMap<String, String>()
            for (i in kv.indices step 2) m[kv[i]] = kv[i + 1]
            return m
        }

        /** Replays the events through the XmlPullParser interface, the way the compiled resource parser would. */
        private fun xml(events: List<Event>): XmlPullParser {
            val at = intArrayOf(0)
            val h = InvocationHandler { _, method, args ->
                val e = events[at[0]]
                val keys: List<String> = e.attrs?.keys?.toList() ?: emptyList()
                when (method.name) {
                    "getEventType" -> e.type
                    "next" -> {
                        if (e.type == XmlPullParser.END_DOCUMENT) XmlPullParser.END_DOCUMENT
                        else {
                            at[0]++
                            events[at[0]].type
                        }
                    }
                    "getDepth" -> e.depth
                    "getName" -> e.name
                    "getAttributeCount" -> keys.size
                    "getAttributeNamespace" -> ""
                    "getAttributeName" -> keys[args[0] as Int]
                    "getAttributeValue" -> if (args.size == 1) e.attrs!![keys[args[0] as Int]] else e.attrs!![args[1] as String]
                    else -> throw UnsupportedOperationException(method.name)
                }
            }
            return Proxy.newProxyInstance(XmlPullParser::class.java.classLoader,
                arrayOf<Class<*>>(XmlPullParser::class.java), h) as XmlPullParser
        }
    }

    // ---- labels ----------------------------------------------------------------------------------

    @Test
    fun labelAcceptsTheSpecAlphabet() {
        assertNull(Cards.checkLabel("Retro"))
        assertNull(Cards.checkLabel("NES & SNES games"))
        assertNull(Cards.checkLabel("Kid's TV+ 2.0-beta"))
        assertNull(Cards.checkLabel("123456789012345678901234"))   // 24 characters
    }

    @Test
    fun labelRejectsLengthAndCharacters() {
        assertNotNull(Cards.checkLabel(null))
        assertNotNull(Cards.checkLabel(""))
        assertNotNull(Cards.checkLabel("1234567890123456789012345"))   // 25
        assertNotNull(Cards.checkLabel("Netflіx"))                // Cyrillic i look-alike
        assertNotNull(Cards.checkLabel("Retro\n"))
        assertNotNull(Cards.checkLabel("Retro\u0000"))
        assertNotNull(Cards.checkLabel("Retro\u202E"))                  // bidi override
        assertNotNull(Cards.checkLabel("Retro\u200D"))                  // zero width joiner
        assertNotNull(Cards.checkLabel("Retro!"))
        assertNotNull(Cards.checkLabel(" Retro"))
        assertNotNull(Cards.checkLabel("Retro "))
        assertNotNull(Cards.checkLabel("+.&"))                         // no letter or digit
        assertNotNull(Cards.checkLabel("@2131820545"))                 // a compiled @string reference
        assertNotNull(Cards.checkLabel("?2130968576"))                 // a compiled ?attr reference
    }

    @Test
    fun captionIsOptionalAndBounded() {
        assertNull(Cards.checkCaption(null))
        assertNull(Cards.checkCaption(""))
        assertNull(Cards.checkCaption("NES games"))
        assertNull(Cards.checkCaption("12345678901234567890123456789012"))      // 32
        assertNotNull(Cards.checkCaption("123456789012345678901234567890123"))  // 33
        assertNotNull(Cards.checkCaption("gamesé"))
        assertNotNull(Cards.checkCaption("NES\tgames"))
        assertNotNull(Cards.checkCaption("NES\u202Egames"))
        assertNotNull(Cards.checkCaption("@2131820545"))
    }

    @Test
    fun foldDropsCaseSpacesAndPunctuation() {
        assertEquals("retro", Cards.fold("Retro"))
        assertEquals("livetv", Cards.fold("Live TV"))
        assertEquals("personallauncher", Cards.fold("Personal Launcher"))
        assertEquals("kidstv20", Cards.fold("Kid's TV+ 2.0"))
        assertEquals("", Cards.fold(null))
        assertEquals("netflx", Cards.fold("Netflіx"))   // non ASCII letters never match anything
    }

    @Test
    fun reservedWordsAreSubstringsOrWholeNames() {
        val ui = Cards.englishUiNames()
        assertTrue(Cards.isReserved(Cards.fold("Verified Retro"), ui))
        assertTrue(Cards.isReserved(Cards.fold("Retro (Official)"), ui))
        assertTrue(Cards.isReserved(Cards.fold("official"), ui))
        assertTrue(Cards.isReserved(Cards.fold("Settings"), ui))
        assertTrue(Cards.isReserved(Cards.fold("S E T T I N G S"), ui))
        assertTrue(Cards.isReserved(Cards.fold("Inputs"), ui))
        assertTrue(Cards.isReserved(Cards.fold("Live TV"), ui))
        assertTrue(Cards.isReserved(Cards.fold("Home"), ui))
        assertTrue(Cards.isReserved(Cards.fold("HDMI"), ui))
        assertTrue(Cards.isReserved(Cards.fold("HDMI 2"), ui))
        assertTrue(Cards.isReserved(Cards.fold("AV"), ui))
        assertTrue(Cards.isReserved(Cards.fold("Personal Launcher"), ui))
        assertFalse(Cards.isReserved(Cards.fold("Xbox"), ui))
        assertTrue(Cards.isReserved("", ui))
        assertFalse(Cards.isReserved(Cards.fold("Retro"), ui))
        assertFalse(Cards.isReserved(Cards.fold("Retro Settings Panel"), ui))   // equality, not substring
    }

    @Test
    fun translatedUiNamesCountToo() {
        val ui = HashSet(Arrays.asList(Cards.fold("Einstellungen"), Cards.fold("Eingänge")))
        assertTrue(Cards.isReserved(Cards.fold("Einstellungen"), ui))
        assertFalse(Cards.isReserved(Cards.fold("Settings panel"), ui))
    }

    @Test
    fun labelTakenByAnotherPackageOnly() {
        val names = HashMap<String, Set<String>>()
        names["retro"] = HashSet(Arrays.asList("io.github.udhaykumarbala.retro"))
        names["netflix"] = HashSet(Arrays.asList("com.netflix.ninja"))
        assertFalse(Cards.isTaken("retro", "io.github.udhaykumarbala.retro", names))   // its own app label
        assertTrue(Cards.isTaken("retro", "io.example.spoof", names))
        assertTrue(Cards.isTaken("netflix", "io.example.spoof", names))
        assertFalse(Cards.isTaken("retrogames", "io.example.spoof", names))
    }

    // ---- accent and digests ----------------------------------------------------------------------

    @Test
    fun accentAcceptsWrittenAndCompiledForms() {
        assertEquals(0xFFE8A33D.toInt(), Cards.parseAccent("#E8A33D"))
        assertEquals(0xFFE8A33D.toInt(), Cards.parseAccent("#e8a33d"))
        assertEquals(0xFFE8A33D.toInt(), Cards.parseAccent("#ffe8a33d"))   // what the resource compiler hands back
        assertEquals(0xFFE8A33D.toInt(), Cards.parseAccent("#80E8A33D"))   // alpha is dropped
        assertEquals(0, Cards.parseAccent("E8A33D"))
        assertEquals(0, Cards.parseAccent("#E8A"))
        assertEquals(0, Cards.parseAccent("#GGGGGG"))
        assertEquals(0, Cards.parseAccent("#E8A33D "))
        assertEquals(0, Cards.parseAccent("@2131099648"))
        assertEquals(0, Cards.parseAccent(null))
        assertEquals(0, Cards.parseAccent("#ffffffffffffffffffffff"))
    }

    @Test
    fun digestFormatting() {
        assertEquals("00ff10", Cards.hex(byteArrayOf(0, 0xff.toByte(), 0x10)))
        assertEquals("7f3a 91c0 b2e4", Cards.shortDigest(DIGEST_A))
        assertEquals("7f3a 91c0 b2e4 0000 0000 0000 0000 0000\n0000 0000 0000 0000 0000 0000 0000 abcd",
            Cards.longDigest(DIGEST_A))
        assertEquals("0202 281b b726", Cards.shortDigest(DIGEST_B))
        assertEquals("abc", Cards.shortDigest("abc"))
    }

    // ---- parser ----------------------------------------------------------------------------------

    @Test
    fun parsesTheReferenceCard() {
        val d = Cards.parse(xml(doc().tag("card", attrs("spec", "1", "label", "Retro",
            "caption", "NES games", "accent", "#E8A33D")).end()))
        assertEquals(1, d.spec)
        assertEquals("Retro", d.label)
        assertEquals("NES games", d.caption)
        assertEquals(0xFFE8A33D.toInt(), d.accent)
    }

    @Test
    fun parsesTheCompiledForms() {
        // aapt2 stores spec as an integer and accent as a colour; the parser hands back "1" and "#ffe8a33d".
        val d = Cards.parse(xml(doc().tag("card", attrs("spec", "1", "label", "Retro",
            "accent", "#ffe8a33d", "colour", "blue")).end()))
        assertEquals(1, d.spec)
        assertNull(d.caption)
        assertEquals(0xFFE8A33D.toInt(), d.accent)
    }

    @Test
    fun parserIgnoresChildrenAndUnknownAttributes() {
        val d = Cards.parse(xml(doc().tag("card", attrs("spec", "1", "label", "Retro", "note", "x"))
            .tag("extra", attrs("value", "x")).close().text("hello").end()))
        assertEquals("Retro", d.label)
        assertEquals(0, d.accent)
    }

    @Test
    fun parserRejectsWrongRootMissingSpecAndReferences() {
        rejects(doc().tag("module", attrs("spec", "1", "label", "Retro")).end(), "root element")
        rejects(doc().tag("card", attrs("label", "Retro")).end(), "spec")
        rejects(doc().tag("card", attrs("spec", "one", "label", "Retro")).end(), "spec")
        rejects(doc().tag("card", attrs("spec", "1")).end(), "label is missing")
        rejects(doc().tag("card", attrs("spec", "1", "label", "@2131820545")).end(), "reference")
        rejects(doc().tag("card", attrs("spec", "1", "label", "Retro", "accent", "@2131099648")).end(), "reference")
        rejects(doc().tag("card", attrs("spec", "1", "label", "Retro", "accent", "orange")).end(), "accent")
        rejects(doc().end(), "no <card>")
    }

    @Test
    fun parserRejectsTwoRootElements() {
        rejects(doc().tag("card", attrs("spec", "1", "label", "Retro")).close()
            .tag("card", attrs("spec", "1", "label", "Other")).end(), "more than one root")
    }

    @Test
    fun unsupportedSpecIsParsedThenRejectedByTheCaller() {
        val d = Cards.parse(xml(doc().tag("card", attrs("spec", "2", "label", "Retro")).end()))
        assertEquals(2, d.spec)
    }

    @Test
    fun parserRejectsDeepNesting() {
        val d = doc().tag("card", attrs("spec", "1", "label", "Retro"))
        for (i in 0 until 4) d.tag("a", attrs())
        rejects(d.end(), "deeper")
    }

    @Test
    fun parserRejectsTooManyEvents() {
        val d = doc().tag("card", attrs("spec", "1", "label", "Retro"))
        for (i in 0 until 40) d.tag("a", attrs()).close()
        rejects(d.end(), "events")
    }

    @Test
    fun parserRejectsLongValuesBeforeLookingAtThem() {
        val big = CharArray(40 * 1024)
        Arrays.fill(big, 'A')
        rejects(doc().tag("card", attrs("spec", "1", "label", String(big))).end(), "longer than")
        rejects(doc().tag("card", attrs("spec", "1", "label", "Retro", "caption", String(big))).end(), "longer than")
        rejects(doc().tag("card", attrs("spec", "1234", "label", "Retro")).end(), "spec")
    }

    @Test
    fun parserRejectsTooManyAttributes() {
        val kv = ArrayList(Arrays.asList("spec", "1", "label", "Retro"))
        for (i in 0 until 20) kv.addAll(Arrays.asList("x" + i, "y"))
        rejects(doc().tag("card", attrs(*kv.toTypedArray())).end(), "attributes")
    }

    // ---- app label, clashes, ordering ------------------------------------------------------------

    @Test
    fun cleanLabelStripsFormattingAndBounds() {
        assertEquals("Netflix", Cards.cleanLabel("Netflix\u202E", "io.example"))
        assertEquals("a b", Cards.cleanLabel("a\n\nb", "io.example"))
        assertEquals("a b c", Cards.cleanLabel("  a \t b\u2028c  ", "io.example"))
        assertEquals("Zero width", Cards.cleanLabel("Zero\u200D width\u200B", "io.example"))
        assertEquals("Retro", Cards.cleanLabel("Retro", "io.example"))
        assertEquals("io.example", Cards.cleanLabel("", "io.example"))
        assertEquals("io.example", Cards.cleanLabel("\u202E\n\u0000", "io.example"))
        assertEquals("io.example", Cards.cleanLabel(null, "io.example"))
        val big = CharArray(100)
        Arrays.fill(big, 'x')
        assertEquals(40, Cards.cleanLabel(String(big), "io.example").length)
        // 39 letters then an emoji (a surrogate pair): the pair does not fit in 40 and is left out whole.
        val emoji = String(Character.toChars(0x1F3AE))
        assertEquals(39, Cards.cleanLabel(String(big, 0, 39) + emoji + "y", "io.example").length)
        assertEquals(String(big, 0, 38) + emoji, Cards.cleanLabel(String(big, 0, 38) + emoji + "y", "io.example"))
    }

    @Test
    fun resolveClashesKeepsTheOwnersDecision() {
        val decided = card("io.example.retro", "Games", Cards.State.SHOWN)
        val newcomer = card("io.example.late", "games", Cards.State.NEW)
        val other = card("io.example.other", "Other", Cards.State.NEW)
        val kept = Cards.resolveClashes(Arrays.asList(newcomer, decided, other), recordsFor("io.example.retro", 100))
        assertEquals(Arrays.asList(decided, other), kept)
        // A hidden decision is still a decision.
        val hidden = card("io.example.retro", "Games", Cards.State.HIDDEN)
        assertEquals(Arrays.asList(hidden), Cards.resolveClashes(Arrays.asList(hidden, newcomer), recordsFor("io.example.retro", 100)))
    }

    @Test
    fun resolveClashesOldestDecisionWins() {
        val older = card("io.example.b", "Games", Cards.State.SHOWN)
        val newer = card("io.example.a", "Games", Cards.State.SHOWN)
        var kept = Cards.resolveClashes(Arrays.asList(newer, older), recordsFor("io.example.a", 200, "io.example.b", 100))
        assertEquals(Arrays.asList(older), kept)
        // Same first seen: the smaller package name wins.
        kept = Cards.resolveClashes(Arrays.asList(older, newer), recordsFor("io.example.a", 100, "io.example.b", 100))
        assertEquals(Arrays.asList(newer), kept)
    }

    @Test
    fun resolveClashesDropsTwoPendingAndLeavesTheRest() {
        val p1 = card("io.example.a", "Games", Cards.State.NEW)
        val p2 = card("io.example.b", "GAMES", Cards.State.RESIGNED)
        val alone = card("io.example.c", "Alone", Cards.State.NEW)
        assertEquals(Arrays.asList(alone), Cards.resolveClashes(Arrays.asList(p1, alone, p2), TreeMap()))
        assertEquals(Arrays.asList(p1, alone), Cards.resolveClashes(Arrays.asList(p1, alone), TreeMap()))
        assertTrue(Cards.resolveClashes(ArrayList(), TreeMap()).isEmpty())
    }

    @Test
    fun shownFiltersOrdersAndCaps() {
        val all = ArrayList<Cards.Card>()
        all.add(card("io.example.hidden", "Aardvark", Cards.State.HIDDEN))
        all.add(card("io.example.new", "Aardvark", Cards.State.NEW))
        all.add(card("io.example.z", "beta", Cards.State.SHOWN))
        all.add(card("io.example.y", "Beta", Cards.State.SHOWN))
        for (i in 0 until 9) all.add(card("io.example.p" + i, "Card " + ('a' + i), Cards.State.SHOWN))
        val shown = Cards.shown(all)
        assertEquals(Cards.MAX_SHOWN, shown.size)
        assertEquals("io.example.y", shown[0].pkg)     // "Beta" and "beta" tie on label, package decides
        assertEquals("io.example.z", shown[1].pkg)
        assertEquals("Card a", shown[2].label)
        assertEquals("Card f", shown[7].label)
        for (c in shown) assertEquals(Cards.State.SHOWN, c.state)
    }

    @Test
    fun firstPendingIsByPackage() {
        assertNull(Cards.firstPending(ArrayList<Cards.Card>()))
        val all = Arrays.asList(
            card("io.example.c", "C", Cards.State.NEW),
            card("io.example.a", "A", Cards.State.SHOWN),
            card("io.example.b", "B", Cards.State.RESIGNED),
            card("io.example.d", "D", Cards.State.HIDDEN))
        assertEquals("io.example.b", Cards.firstPending(all)!!.pkg)
        assertNull(Cards.firstPending(Arrays.asList(all[1], all[3])))
    }

    // ---- character checks ------------------------------------------------------------------------

    @Test
    fun packageNames() {
        assertTrue(Cards.isPackageName("io.example.a"))
        assertTrue(Cards.isPackageName("com.netflix.ninja"))
        assertTrue(Cards.isPackageName("a_b.c9"))
        assertFalse(Cards.isPackageName("example"))
        assertFalse(Cards.isPackageName("io."))
        assertFalse(Cards.isPackageName(".io"))
        assertFalse(Cards.isPackageName("io..a"))
        assertFalse(Cards.isPackageName("io.1a"))
        assertFalse(Cards.isPackageName("1io.a"))
        assertFalse(Cards.isPackageName("io.a\""))
        assertFalse(Cards.isPackageName("io.a b"))
        assertFalse(Cards.isPackageName(""))
        assertFalse(Cards.isPackageName(null))
        val big = CharArray(260)
        Arrays.fill(big, 'a')
        assertFalse(Cards.isPackageName("io." + String(big)))
    }

    @Test
    fun characterClasses() {
        assertTrue(Cards.isText(""))
        assertTrue(Cards.isText("Kid's TV+ 2.0-beta & more"))
        assertFalse(Cards.isText("a\tb"))
        assertFalse(Cards.isText("caf\u00e9"))
        assertTrue(Cards.isDigits("007"))
        assertFalse(Cards.isDigits(""))
        assertFalse(Cards.isDigits("1a"))
        assertTrue(Cards.isHex("0aF", false))
        assertFalse(Cards.isHex("0aF", true))
        assertFalse(Cards.isHex("0g", false))
        assertTrue(Cards.isDigest(DIGEST_A))
        assertFalse(Cards.isDigest(DIGEST_A.uppercase()))
        assertFalse(Cards.isDigest(DIGEST_A + "0"))
    }

    @Test
    fun decodeRecordIsExact() {
        val line = "{\"package\":\"io.example.a\",\"signer_sha256\":\"" + DIGEST_A +
            "\",\"label\":\"retro\",\"decision\":\"show\",\"first_seen\":1,\"decided_at\":2}"
        val r = Cards.decodeRecord(line)
        assertNotNull(r)
        assertEquals("io.example.a", r!!.pkg)
        assertEquals("retro", r!!.label)
        assertEquals(1L, r!!.firstSeen)
        assertEquals(2L, r!!.decidedAt)
        assertNull(Cards.decodeRecord(line + " "))
        assertNull(Cards.decodeRecord(line.substring(0, line.length - 1)))
        assertNull(Cards.decodeRecord(line.replace("\"first_seen\":1", "\"first_seen\":1 ")))
        assertNull(Cards.decodeRecord(line.replace("\"decided_at\":2", "\"decided_at\":")))
        assertNull(Cards.decodeRecord(line.replace("\"decided_at\":2", "\"decided_at\":1234567890123456789")))
        assertNull(Cards.decodeRecord(line.replace("signer_sha256", "signer")))
        assertNull(Cards.decodeRecord(line.replace("\"label\":\"retro\"", "\"label\":\"Retro\"")))   // not folded
        assertNull(Cards.decodeRecord(line.replace("\"label\":\"retro\"", "\"label\":\"\"")))
        assertNull(Cards.decodeRecord(line.replace("\"label\":\"retro\",", "")))
        assertNull(Cards.decodeRecord(""))
        assertNull(Cards.decodeRecord("{"))
    }

    // ---- decision file (cards.json) --------------------------------------------------------------

    @Test
    fun storeRoundTrip() {
        val `in` = TreeMap<String, Cards.Record>()
        `in`["io.github.udhaykumarbala.retro"] = Cards.Record("io.github.udhaykumarbala.retro", DIGEST_B, "retro", "show", 1700000000000L, 1700000001000L)
        `in`["io.example.spoof"] = Cards.Record("io.example.spoof", DIGEST_A, "netflix", "hide", 1700000002000L, 1700000003000L)
        val text = Cards.encode(`in`)
        assertEquals("{\"version\":2,\"records\":[\n" +
            "{\"package\":\"io.example.spoof\",\"signer_sha256\":\"" + DIGEST_A + "\",\"label\":\"netflix\",\"decision\":\"hide\",\"first_seen\":1700000002000,\"decided_at\":1700000003000},\n" +
            "{\"package\":\"io.github.udhaykumarbala.retro\",\"signer_sha256\":\"" + DIGEST_B + "\",\"label\":\"retro\",\"decision\":\"show\",\"first_seen\":1700000000000,\"decided_at\":1700000001000}\n" +
            "]}\n", text)
        val out = Cards.decode(text)
        assertNotNull(out)
        assertEquals(2, out!!.size)
        assertEquals("show", out!!["io.github.udhaykumarbala.retro"]!!.decision)
        assertEquals("retro", out!!["io.github.udhaykumarbala.retro"]!!.label)
        assertEquals(DIGEST_B, out!!["io.github.udhaykumarbala.retro"]!!.digest)
        assertEquals(1700000000000L, out!!["io.github.udhaykumarbala.retro"]!!.firstSeen)
        assertEquals(1700000003000L, out!!["io.example.spoof"]!!.decidedAt)
    }

    @Test
    fun emptyStoreRoundTrip() {
        val text = Cards.encode(TreeMap<String, Cards.Record>())
        assertEquals("{\"version\":2,\"records\":[\n]}\n", text)
        val out = Cards.decode(text)
        assertNotNull(out)
        assertTrue(out!!.isEmpty())
    }

    @Test
    fun decodeRejectsAnythingElse() {
        val good = Cards.encode(one("io.example.a", DIGEST_A, "show"))
        assertNull(Cards.decode(null))
        assertNull(Cards.decode(""))
        assertNull(Cards.decode("{}"))
        assertNull(Cards.decode("[]"))
        assertNull(Cards.decode(good.replace("show", "yes")))
        assertNull(Cards.decode(good.replace(DIGEST_A, DIGEST_A.substring(1))))
        assertNull(Cards.decode(good.replace(DIGEST_A, DIGEST_A.uppercase())))
        assertNull(Cards.decode(good.replace("io.example.a", "io.example.a\",\"x\":\"")))
        assertNull(Cards.decode(good.replace("io.example.a", "example")))          // no dot
        assertNull(Cards.decode(good.replace("io.example.a", "io.1example.a")))   // bad segment
        assertNull(Cards.decode(good.replace("\"version\":2", "\"version\":1")))   // the old format, without labels
        assertNull(Cards.decode(good.replace("]}", "")))
        assertNull(Cards.decode(good.replace("1700", "-170")))
        assertNull(Cards.decode(good + "garbage"))
        // Missing comma between records, and the same package twice.
        val two = one("io.example.a", DIGEST_A, "show")
        two["io.example.b"] = Cards.Record("io.example.b", DIGEST_B, "b", "hide", 1, 2)
        val pair = Cards.encode(two)
        assertNotNull(Cards.decode(pair))
        assertNull(Cards.decode(pair.replace("},\n", "}\n")))
        assertNull(Cards.decode(pair.replace("io.example.b", "io.example.a")))
    }

    @Test
    fun decodeCapsSizeAndCount() {
        val many = TreeMap<String, Cards.Record>()
        for (i in 0 until Cards.MAX_RECORDS + 1) {
            val pkg = "io.example.p" + i
            many[pkg] = Cards.Record(pkg, DIGEST_A, "card" + i, "hide", i.toLong(), i.toLong())
        }
        val text = Cards.encode(many)                 // encode drops the oldest beyond the cap
        val out = Cards.decode(text)
        assertNotNull(out)
        assertEquals(Cards.MAX_RECORDS, out!!.size)
        assertFalse(out!!.containsKey("io.example.p0"))    // decidedAt 0 was the oldest
        // A file with one record too many cannot come from encode; it is invalid.
        val lines = text.split("\n").dropLastWhile { it.isEmpty() }
        val forged = StringBuilder(lines[0]).append('\n')
        for (i in 1 until lines.size - 1) forged.append(lines[i]).append(if (i == lines.size - 2) ",\n" else "\n")
        forged.append("{\"package\":\"io.example.extra\",\"signer_sha256\":\"").append(DIGEST_A)
            .append("\",\"decision\":\"hide\",\"first_seen\":1,\"decided_at\":1}\n]}\n")
        assertNull(Cards.decode(forged.toString()))
        val huge = CharArray(70 * 1024)
        Arrays.fill(huge, ' ')
        assertNull(Cards.decode(String(huge)))
    }

    @Test
    fun encodeRefusesAnInvalidRecord() {
        try {
            Cards.encode(one("io.example.a", "nothex", "show"))
            fail()
        } catch (expected: IllegalArgumentException) {
        }
        try {
            Cards.encode(one("io.example.a\"", DIGEST_A, "show"))
            fail()
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun quarantineMovesTheBadFileAside() {
        val dir = File.createTempFile("cards", "dir")
        assertTrue(dir.delete() && dir.mkdir())
        val store = File(dir, Cards.STORE_FILE)
        FileOutputStream(store).use { out ->
            out.write("not json".toByteArray(StandardCharsets.UTF_8))
        }
        assertTrue(Cards.quarantine(store))
        val bad = File(dir, Cards.STORE_FILE + ".bad")
        assertFalse(store.exists())
        assertTrue(bad.exists())
        // A second bad file replaces the first; the evidence is the latest one.
        FileOutputStream(store).use { out ->
            out.write("still not json".toByteArray(StandardCharsets.UTF_8))
        }
        assertTrue(Cards.quarantine(store))
        assertEquals(14, bad.length())
        assertTrue(bad.delete() && dir.delete())
    }

    @Test
    fun captionCannotClaimEndorsement() {
        assertNull(Cards.checkCaption("NES games"))
        assertNotNull(Cards.checkCaption("Official Netflix app"))
        assertNotNull(Cards.checkCaption("VERIFIED by Google"))
        assertNotNull(Cards.checkCaption("ver-ified"))   // folding removes the hyphen
    }

    @Test
    fun relabelledCardIsPendingAgain() {
        // A card the owner decided on, whose label then changed, is treated like a new card: it does
        // not win a name clash, and the clash rule drops both undecided cards.
        val renamed = card("io.example.retro", "Netflix", Cards.State.RELABELLED)
        val other = card("io.example.other", "netflix", Cards.State.NEW)
        assertTrue(renamed.pending())
        assertTrue(Cards.resolveClashes(Arrays.asList(renamed, other), recordsFor("io.example.retro", 100)).isEmpty())
        assertEquals(renamed, Cards.firstPending(Arrays.asList(card("io.example.a", "A", Cards.State.SHOWN), renamed)))
    }

    @Test
    fun isFoldedIsStrict() {
        assertTrue(Cards.isFolded("retro"))
        assertTrue(Cards.isFolded("nes2"))
        assertFalse(Cards.isFolded(""))
        assertFalse(Cards.isFolded("Retro"))
        assertFalse(Cards.isFolded("re tro"))
        assertFalse(Cards.isFolded("abcdefghijklmnopqrstuvwxy"))   // 25
    }

    // ---- a scripted XmlPullParser ----------------------------------------------------------------

    /** One parser event: type, depth, name and attributes (START_TAG only). */
    private class Event(
        val type: Int,
        val depth: Int,
        val name: String?,
        val attrs: Map<String, String>?
    )

    private class Doc {
        val events = ArrayList<Event>()
        var depth = 0

        fun tag(name: String, attrs: Map<String, String>): Doc {
            events.add(Event(XmlPullParser.START_TAG, ++depth, name, attrs))
            return this
        }

        fun close(): Doc {
            events.add(Event(XmlPullParser.END_TAG, depth, null, null))
            depth -= 1
            return this
        }

        fun text(s: String): Doc {
            events.add(Event(XmlPullParser.TEXT, depth, s, null))
            return this
        }

        fun end(): List<Event> {
            while (depth > 0) close()
            events.add(Event(XmlPullParser.END_DOCUMENT, 0, null, null))
            return events
        }
    }
}
