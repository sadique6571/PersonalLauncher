package com.sadique.launcher

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

internal class LauncherGridLayoutManager(
    context: Context,
    private val columns: Int,
    private val singleRow: Boolean,
    private val rtlAppGrid: Boolean,
    private val dockRow: Boolean,
    private val animations: Boolean,
    private val reducedMotion: Boolean,
) : GridLayoutManager(
    context, if (singleRow) 1 else columns,
    if (singleRow) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL, false,
) {
    override fun isLayoutRTL(): Boolean {
        return rtlAppGrid
    }

    /**
     * Every "bring this tile on screen" scroll lands here, both RecyclerView's own on focus and
     * onTileFocused's. The stock version treats the grid's top padding as the visible top, but that
     * padding only pushes the dock down the screen: grid rows scroll up into it. With the stock rule,
     * Up into a row above the padding dragged that row down to the padding line, cut it off at the
     * bottom of the screen and could scroll the focused tile out entirely, dropping focus into the
     * top bar. Grid tiles now scroll just enough to show the rect between the grid's top edge and its
     * bottom; dock tiles are left to onTileFocused, which parks the dock row on the padding line.
     */
    override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View,
                                               rect: Rect, immediate: Boolean,
                                               focusedChildVisible: Boolean): Boolean {
        if (singleRow) {
            // Sideways: scroll just enough to bring the tile's cell inside the padding. Cells are a
            // column wide, so the row moves a whole tile at a time and the tiles stay on the columns.
            // The whole cell, not the card rect passed in: the card is narrower and centred in it.
            val left = child.left
            val right = child.right
            val dx: Int
            if (left < paddingLeft) dx = left - paddingLeft
            else if (right > width - paddingRight) dx = right - (width - paddingRight)
            else dx = 0
            if (dx == 0) return false
            if (immediate || reducedMotion || !animations) parent.scrollBy(dx, 0)
            else parent.smoothScrollBy(dx, 0)
            return true
        }
        if (dockRow && getPosition(child) < columns) return false
        val top = child.top + rect.top
        val bottom = top + rect.height()
        // A row already fully on screen stays put, even inside the bottom padding (the last row
        // rows_below_dock leaves visible ends there; nudging it up was the bug). A row that has to scroll
        // in still stops at the padding line, clear of the screen edge, as before.
        val limitBottom = height - paddingBottom
        val dy: Int
        if (top < 0) dy = top
        else if (bottom <= height) dy = 0
        else dy = Math.min(top, bottom - limitBottom)
        if (dy == 0) return false
        if (immediate || reducedMotion || !animations) parent.scrollBy(0, dy)
        else parent.smoothScrollBy(0, dy)
        return true
    }
}
