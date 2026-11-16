package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import java.util.Collections
import java.util.function.IntConsumer

/** Owns the visible app order and the tile carried by the directional keys. */
internal class HomeAppReorder(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val cachedApps: () -> List<PersonalApp>?,
    private val grid: () -> HomeAppGrid<PersonalApp>?,
    private val gridWidth: () -> Int,
    private val clearCategory: () -> Unit,
    private val setHeading: (String) -> Unit,
    private val submitHomeApps: (List<PersonalApp>, Int, Int) -> Unit,
    private val renderApps: (List<PersonalApp>) -> Unit,
    private val focusFirstApp: () -> Unit,
    private val chooseSetting: (String, Array<String>, IntConsumer) -> Unit,
    private val refreshApps: () -> Unit,
) {
    var carriedApp: PersonalApp? = null
        private set
    private var moveOrder: MutableList<String>? = null
    private var movingRendered = false

    fun clear() {
        carriedApp = null
        moveOrder = null
    }

    fun consumeRebind(): Boolean {
        val needed = movingRendered
        movingRendered = false
        return needed
    }

    /** Null lets Home continue normal navigation when it is not carrying a tile. */
    fun handleKey(event: KeyEvent, fallback: () -> Boolean): Boolean? {
        val carried = carriedApp
        if (carried != null && event.action == KeyEvent.ACTION_DOWN) {
            val pkg = carried.info.activityInfo.packageName
            val order = moveOrder!!
            val from = order.indexOf(pkg)
            val g = grid()!!
            val cols = Math.max(1, g.columnCount)
            val code = event.keyCode
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                commitMove()
                return true
            }
            if (code == KeyEvent.KEYCODE_BACK) { cancelMove(); return true }
            var to = -1
            if (g.isHorizontalDock() && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                val forward = (code == KeyEvent.KEYCODE_DPAD_RIGHT) == (g.layoutDirection != View.LAYOUT_DIRECTION_RTL)
                to = AppGridNavigation.horizontal(from, order.size, forward)
                if (to == from) to = -1
            } else if (g.isHorizontalDock() && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
                return true
            } else if (code == KeyEvent.KEYCODE_DPAD_LEFT) { if (from % cols != 0) to = from - 1 }
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { if (from % cols != cols - 1 && from + 1 < order.size) to = from + 1 }
            else if (code == KeyEvent.KEYCODE_DPAD_UP) { if (from - cols >= 0) to = from - cols }
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) { if (from + cols < order.size) to = from + cols }
            else return fallback()
            if (to >= 0) {
                Collections.swap(order, from, to)
                renderMoveGrid()
            }
            return true
        }
        return null
    }

    /** Direct move mode: long-press > Move, then arrows carry the tile, OK drops it, Back cancels. */
    fun startMoveMode(app: PersonalApp) {
        val apps = cachedApps() ?: return
        val visible = orderedApps(apps)
        val hidden = prefs.getStringSet("hidden_apps", emptySet())
        visible.removeIf { a -> hidden?.contains(a.info.activityInfo.packageName) == true }
        val order = ArrayList<String>()
        moveOrder = order
        for (a in visible) order.add(a.info.activityInfo.packageName)
        clearCategory()
        carriedApp = app
        renderMoveGrid()
        Toast.makeText(host, "Arrows move · OK places · Back cancels", Toast.LENGTH_LONG).show()
    }

    private fun renderMoveGrid() {
        movingRendered = true
        val byPackage = LinkedHashMap<String, PersonalApp>()
        for (app in cachedApps()!!) byPackage[app.info.activityInfo.packageName] = app
        val carried = carriedApp!!
        setHeading("Move " + carried.name)
        val avail = gridWidth()
        val g = grid()!!
        val columns = g.columnCount
        val movingPkg = carried.info.activityInfo.packageName
        val moved = ArrayList<PersonalApp>()
        for (pkg in moveOrder!!) {
            val app = byPackage[pkg]
            if (app != null) moved.add(app)
        }
        submitHomeApps(moved, columns, avail)
        g.rebind()
        g.focusKey(movingPkg)
    }

    private fun commitMove() {
        // Hidden apps keep their slots; visible ones take the new order.
        val order = moveOrder!!
        val visibleSet = HashSet(order)
        val moved = order.iterator()
        val result = ArrayList<String>()
        for (app in orderedApps(cachedApps()!!)) {
            val pkg = app.info.activityInfo.packageName
            result.add(if (visibleSet.contains(pkg)) moved.next() else pkg)
        }
        prefs.edit().putString("app_order", org.json.JSONArray(result).toString()).apply()
        clear()
        renderApps(cachedApps()!!)
        focusFirstApp()
        Toast.makeText(host, "App order saved", Toast.LENGTH_SHORT).show()
    }

    private fun cancelMove() {
        clear()
        renderApps(cachedApps()!!)
        focusFirstApp()
    }

    fun orderedApps(apps: List<PersonalApp>): MutableList<PersonalApp> {
        val byPackage = LinkedHashMap<String, PersonalApp>()
        for (app in apps) byPackage[app.info.activityInfo.packageName] = app
        val saved = ArrayList<String>()
        try {
            val array = org.json.JSONArray(prefs.getString("app_order", "[]"))
            for (i in 0 until array.length()) saved.add(array.getString(i))
        } catch (malformed: org.json.JSONException) { /* Fall back to alphabetical order. */ }
        val result = ArrayList<PersonalApp>()
        for (pkg in AppOrder.merge(ArrayList(byPackage.keys), saved)) result.add(byPackage[pkg]!!)
        return result
    }

    fun rearrangeApps() {
        val apps = cachedApps() ?: return
        val visible = orderedApps(apps)
        val hidden = prefs.getStringSet("hidden_apps", emptySet())
        visible.removeIf { app -> hidden?.contains(app.info.activityInfo.packageName) == true }
        if (visible.isEmpty()) { Toast.makeText(host, "Unhide an app first", Toast.LENGTH_SHORT).show(); return }
        val names = Array(visible.size) { "" }
        val hints = Array(visible.size) { "" }
        for (i in visible.indices) { names[i] = visible[i].name; hints[i] = "Select to move this app" }
        GlassSettings.options(host, "Rearrange apps", names, hints) { which -> moveApp(visible[which]) }
    }

    private fun moveApp(moving: PersonalApp) {
        val apps = cachedApps() ?: return
        val ordered = orderedApps(apps)
        val visible = ArrayList(ordered)
        val hidden = prefs.getStringSet("hidden_apps", emptySet())
        visible.removeIf { app -> hidden?.contains(app.info.activityInfo.packageName) == true }
        val positions = Array(visible.size) { "" }
        for (i in visible.indices) positions[i] = "${i + 1} · ${visible[i].name}"
        chooseSetting("Move " + moving.name + " to position", positions, IntConsumer { which ->
            val packages = ArrayList<String>()
            for (app in orderedApps(cachedApps()!!)) packages.add(app.info.activityInfo.packageName)
            val destination = packages.indexOf(visible[which].info.activityInfo.packageName)
            val moved = AppOrder.move(packages, moving.info.activityInfo.packageName, destination)
            prefs.edit().putString("app_order", org.json.JSONArray(moved).toString()).apply()
            refreshApps()
            Toast.makeText(host, "App order saved", Toast.LENGTH_SHORT).show()
        })
    }
}
