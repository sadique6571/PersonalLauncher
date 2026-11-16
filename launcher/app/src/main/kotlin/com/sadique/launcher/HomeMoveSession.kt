package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import java.util.Collections

internal class HomeMoveSession(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val currentGrid: () -> HomeAppGrid<PersonalApp>?,
    private val currentApps: () -> List<PersonalApp>?,
    private val orderedApps: (List<PersonalApp>) -> MutableList<PersonalApp>,
    private val clearCategory: () -> Unit,
    private val movingRendered: () -> Unit,
    private val showHeading: (String) -> Unit,
    private val gridWidth: () -> Int,
    private val submitHomeApps: (List<PersonalApp>, Int, Int) -> Unit,
    private val renderApps: (List<PersonalApp>) -> Unit,
    private val focusFirstApp: () -> Unit,
) {
    var carriedApp: PersonalApp? = null
        private set
    private var moveOrder: MutableList<String>? = null

    fun clear() {
        carriedApp = null
        moveOrder = null
    }


    /** Direct move mode: long-press > Move, then arrows carry the tile, OK drops it, Back cancels. */
    fun start(app: PersonalApp) {
        val apps = currentApps() ?: return
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
        movingRendered()
        val byPackage = LinkedHashMap<String, PersonalApp>()
        for (app in currentApps()!!) byPackage[app.info.activityInfo.packageName] = app
        val carried = carriedApp!!
        showHeading("Move " + carried.name)
        val avail = gridWidth()
        val g = currentGrid()!!
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
        for (app in orderedApps(currentApps()!!)) {
            val pkg = app.info.activityInfo.packageName
            result.add(if (visibleSet.contains(pkg)) moved.next() else pkg)
        }
        prefs.edit().putString("app_order", org.json.JSONArray(result).toString()).apply()
        clear()
        renderApps(currentApps()!!)
        focusFirstApp()
        Toast.makeText(host, "App order saved", Toast.LENGTH_SHORT).show()
    }

    private fun cancelMove() {
        clear()
        renderApps(currentApps()!!)
        focusFirstApp()
    }

    fun dispatchDown(event: KeyEvent): Boolean {
        val carried = carriedApp ?: return false
        val pkg = carried.info.activityInfo.packageName
        val order = moveOrder!!
        val from = order.indexOf(pkg)
        val g = currentGrid()!!
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
        else return false
        if (to >= 0) {
            Collections.swap(order, from, to)
            renderMoveGrid()
        }
        return true
    }
}
