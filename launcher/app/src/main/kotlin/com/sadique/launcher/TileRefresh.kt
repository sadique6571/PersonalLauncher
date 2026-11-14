package com.sadique.launcher

/** Tracks whether returning Home needs a package scan or only a focus restore. */
class TileRefresh {
    enum class Action { NOTHING, REFOCUS, REBUILD }

    private var started = false
    private var stale = true
    private var refocus = false

    fun onStart() {
        started = true
    }

    fun onStop() {
        started = false
        refocus = true
    }

    fun onHomeAgain() {
        refocus = true
    }

    fun invalidate() {
        stale = true
    }

    fun onPackagesChanged(): Boolean {
        if (started) return true
        stale = true
        return false
    }

    fun onResume(changedMeanwhile: Boolean): Action {
        val action = when {
            stale || changedMeanwhile -> Action.REBUILD
            refocus -> Action.REFOCUS
            else -> Action.NOTHING
        }
        stale = false
        refocus = false
        return action
    }
}
