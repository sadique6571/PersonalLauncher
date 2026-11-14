package com.sadique.launcher

/** Horizontal keys follow the visible row-major app order, stopping at the list boundaries. */
object AppGridNavigation {
    @JvmStatic
    fun vertical(position: Int, count: Int, columns: Int, down: Boolean): Int {
        if (count <= 0 || columns <= 0 || position < 0 || position >= count) return -1
        if (!down) return if (position < columns) -1 else position - columns
        if (position / columns == (count - 1) / columns) return position
        return Math.min(count - 1, position + columns)
    }

    @JvmStatic
    fun verticalWithFooter(position: Int, count: Int, columns: Int, down: Boolean, footerEnabled: Boolean): Int {
        val next = vertical(position, count, columns, down)
        return if (down && footerEnabled && next == position && position in 0 until count) count else next
    }

    /** Stable identity wins over list order; a removed app falls back to its nearest old position. */
    @JvmStatic
    fun footerReturn(keys: List<String>, enteredFrom: String?, oldPosition: Int): Int {
        if (keys.isEmpty()) return -1
        val found = if (enteredFrom == null) -1 else keys.indexOf(enteredFrom)
        return if (found >= 0) found else Math.max(0, Math.min(keys.size - 1, oldPosition))
    }

    @JvmStatic
    fun horizontal(position: Int, count: Int, right: Boolean): Int {
        if (count <= 0 || position < 0 || position >= count) return -1
        return Math.max(0, Math.min(count - 1, position + if (right) 1 else -1))
    }
}
