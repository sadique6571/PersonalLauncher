package com.sadique.launcher

/** Physical left/right boundaries for a grid whose first column follows its reading direction. */
object GridNavigation {
    @JvmStatic
    fun atHorizontalEdge(position: Int, count: Int, columns: Int, rtl: Boolean, left: Boolean): Boolean {
        val towardStart = left != rtl
        if (towardStart) return position % columns == 0
        return position % columns == columns - 1 || position == count - 1
    }
}
