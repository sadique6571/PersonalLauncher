package com.sadique.launcher

/**
 * Which catalogue load of the app library fills it, and when the popup appears.
 */
class LibraryLoads {
    private var latest = 0
    private var revealPending = false

    fun start(reveal: Boolean): Int {
        if (reveal) revealPending = true
        return ++latest
    }

    fun isLatest(load: Int): Boolean = load == latest

    fun takeReveal(): Boolean {
        val reveal = revealPending
        revealPending = false
        return reveal
    }
}
