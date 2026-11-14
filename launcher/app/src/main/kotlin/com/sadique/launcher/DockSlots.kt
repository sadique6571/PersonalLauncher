package com.sadique.launcher

import java.util.ArrayList

/**
 * The dock is the first row of the grid. dock_apps decides what is in it, or the saved order once the owner
 * changed the dock on the TV: a slot left free stays empty instead of taking the first grid app (#162) when
 * fill_dock is false; by default they fill.
 */
object DockSlots {
    /**
     * Empty slots to put after the [docked] dock apps found. None when the dock is full, when no
     * dock app is installed (the first row fills so Home still has a dock) or when nothing follows.
     */
    @JvmStatic
    fun empty(docked: Int, dockSize: Int, tiles: Int, fill: Boolean): Int {
        if (fill || docked <= 0 || docked >= dockSize || tiles <= docked) return 0
        return dockSize - docked
    }

    /**
     * The order a reorder session saves when fill_dock is false (#499). Reorder mode shows no empty slots, so a
     * grid app slides into a free dock slot without being put there. Only the keys in [dock] (in the dock
     * when the session started, or carried into it) that end inside the first [dockSize] places stay in the
     * dock: they come first, in their order, and every other key follows in its order. The dock size to save is
     * the number of keys in [dock] found there.
     */
    @JvmStatic
    fun dockFirst(keys: List<String>, dockSize: Int, dock: Set<String>): List<String> {
        val head = ArrayList<String>()
        val tail = ArrayList<String>()
        for (i in keys.indices) {
            val k = keys[i]
            if (i < dockSize && dock.contains(k)) head.add(k) else tail.add(k)
        }
        head.addAll(tail)
        return head
    }

    /** How many of the first [dockSize] keys are in [dock]: the dock size dockFirst puts first. */
    @JvmStatic
    fun dockCount(keys: List<String>, dockSize: Int, dock: Set<String>): Int {
        var n = 0
        for (i in 0 until Math.min(dockSize, keys.size)) {
            if (dock.contains(keys[i])) n++
        }
        return n
    }

    /** A position focus can land on: an empty slot sends it to the last dock app. */
    @JvmStatic
    fun focusable(position: Int, docked: Int, empty: Int): Int {
        return if (empty > 0 && position >= docked && position < docked + empty) docked - 1 else position
    }
}
