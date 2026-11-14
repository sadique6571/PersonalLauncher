package com.sadique.launcher

/** UI-thread ownership of artwork requests across app scans and theme changes. */
class ArtworkRequestState {
    private var generation = 0
    private var loading = false

    fun pending(generation: Int): Boolean = loading && this.generation == generation

    fun begin(generation: Int) {
        this.generation = generation
        loading = true
    }

    fun finish(generation: Int): Boolean {
        if (!pending(generation)) return false
        loading = false
        return true
    }
}
