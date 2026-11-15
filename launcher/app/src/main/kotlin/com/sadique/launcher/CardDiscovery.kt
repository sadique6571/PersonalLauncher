package com.sadique.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.util.Log
import com.sadique.launcher.Cards.Card
import com.sadique.launcher.Cards.CardException
import com.sadique.launcher.Cards.Companion.MAX_DECLARED
import com.sadique.launcher.Cards.Companion.META_DATA
import com.sadique.launcher.Cards.Declared
import com.sadique.launcher.Cards.Record
import com.sadique.launcher.Cards.State
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.function.Supplier

internal object CardDiscovery {
    /**
     * Every valid card installed on this TV, with its state under the given records. One cheap
     * PackageManager query when no app declares a card; the app list is only walked when one does.
     * hidden is the launcher's hidden_apps as in force (config.xml or a theme overlay): no card for those.
     * The decision records are only read when some app declares a card, and leanback is the launcher's
     * own LEANBACK_LAUNCHER query, so the app list isn't queried twice.
     */
    fun discover(
        ctx: Context, hidden: Set<String>, recordSource: Supplier<Map<String, Record>>,
        leanback: List<ResolveInfo>
    ): List<Card> {
        if ((ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) Cards.selfCheck()
        val out = ArrayList<Card>()
        val pm = ctx.packageManager
        val declared: List<ResolveInfo>
        try {
            declared = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Cards.CATEGORY),
                PackageManager.GET_META_DATA
            )
        } catch (e: Exception) {
            Log.w(Cards.TAG, "card query failed: $e")
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
                Log.w(Cards.TAG, "$pkg: label unreadable, $e") // an app with broken resources; not a card problem
            }
        }
        val uiNames = uiNames(ctx.resources)
        val seen = HashSet<String>()

        var n = 0
        for (ri in declared) {
            if (++n > MAX_DECLARED) {
                Log.w(Cards.TAG, "more than $MAX_DECLARED cards declared; the rest are ignored")
                break
            }
            val pkg = ri.activityInfo?.packageName ?: "?"
            try {
                val card = read(ctx, pm, ri, tiles, names, uiNames, hidden, seen, records)
                seen.add(card.pkg)
                out.add(card)
            } catch (e: CardException) {
                Log.w(Cards.TAG, "$pkg: ${e.message}")
            } catch (e: Exception) {
                // Package gone between the query and the read, a resource that is not XML, and anything else.
                Log.w(Cards.TAG, "$pkg: $e")
            }
        }

        val kept = Cards.resolveClashes(out, records)
        for (c in out) {
            if (!kept.contains(c)) Log.w(Cards.TAG, "${c.pkg}: another card is also named '${c.label}'")
        }
        return kept
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
            d = Cards.parse(xml)
        } finally {
            xml.close()
        }
        if (d.spec != Cards.SPEC_SUPPORTED) throw CardException("spec ${d.spec} is not supported (this launcher reads spec ${Cards.SPEC_SUPPORTED})")

        val sigDigest = Cards.digest(pm, pkg)
        val folded = Cards.fold(d.label)
        val r = records[pkg]
        val state: State
        if (r == null) state = State.NEW
        else if (r.digest != sigDigest) state = State.RESIGNED
        else if (r.label != folded) state = State.RELABELLED // the owner said yes to another name
        else state = if (Cards.DECISION_SHOW == r.decision) State.SHOWN else State.HIDDEN
        val pending = state == State.NEW || state == State.RESIGNED || state == State.RELABELLED

        // Reserved words are policy and always apply. Name collisions only matter while the prompt is
        // due: they stop a newcomer from borrowing a name, and never undo a decision the owner made.
        // A decided card that changes its label becomes pending again (RELABELLED), so it cannot take
        // another app's name on an update either.
        if (Cards.isReserved(folded, uiNames)) throw CardException("label '${d.label}' is reserved")
        if (pending && Cards.isTaken(folded, pkg, names)) throw CardException("label '${d.label}' is the name of another installed app")
        val appLabel = Cards.cleanLabel(ai.applicationInfo.loadLabel(pm).toString(), pkg)
        if (pending && Cards.isTaken(Cards.fold(appLabel), pkg, names)) throw CardException("app label '$appLabel' is the name of another installed app")

        return Card(pkg, component, d.label, appLabel, d.caption, d.accent, sigDigest, state)
    }

    private fun addName(names: MutableMap<String, MutableSet<String>>, label: String, pkg: String) {
        val key = Cards.fold(label)
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
        for (id in ids) out.add(Cards.fold(res.getString(id)))
        return out
    }
}
