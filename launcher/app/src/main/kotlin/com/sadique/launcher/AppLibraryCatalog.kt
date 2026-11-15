package com.sadique.launcher

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap

internal object AppLibraryCatalog {
    /** Labels kept between openings (LOADER thread only), until the app is updated. */
    private val KNOWN: MutableMap<String, Known> = HashMap()

    fun loadLabel(pm: PackageManager, app: ResolveInfo): String {
        val pkg = app.activityInfo.packageName
        try {
            return Cards.cleanLabel(app.loadLabel(pm).toString(), pkg)
        } catch (e: RuntimeException) {
            return pkg
        }
    }

    private class Known(val updated: Long, val label: String)


    data class Listing(
        val apps: MutableList<ResolveInfo>,
        val tvPackages: Set<String>,
        val labels: Map<String, String>,
        val installTimes: Map<String, Long>,
    )

    fun load(pm: PackageManager, self: String, excludedApps: Set<String>): Listing {
        val unique = LinkedHashMap<String, ResolveInfo>()
        val tv = HashSet<String>()
        // Prefer the TV entry when an app declares both categories.
        for (category in arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (info in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val ai = info.activityInfo
                if (ai == null || !ai.exported) continue
                val pkg = ai.packageName
                if (Intent.CATEGORY_LEANBACK_LAUNCHER == category) tv.add(pkg)
                if (pkg != self && !LibraryApps.excludedFromCatalog(excludedApps, pkg) && !unique.containsKey(pkg)) unique[pkg] = info
            }
        }
        val names = HashMap<String, String>()
        val installed = HashMap<String, Long>()
        for ((pkg, value) in unique) {
            var updated = 0L
            try {
                val info: PackageInfo = pm.getPackageInfo(pkg, 0)
                installed[pkg] = info.firstInstallTime
                updated = info.lastUpdateTime
            } catch (e: PackageManager.NameNotFoundException) {
                installed[pkg] = 0L
            } catch (e: RuntimeException) {
                installed[pkg] = 0L
            }
            var known = KNOWN[pkg]
            if (known == null || known.updated != updated) {
                known = Known(updated, loadLabel(pm, value))
                KNOWN[pkg] = known
            }
            names[pkg] = known.label
        }
        KNOWN.keys.retainAll(unique.keys)
        return Listing(ArrayList(unique.values), tv, names, installed)
    }
}
