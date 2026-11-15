package com.sadique.launcher

import android.view.KeyEvent
import android.view.SoundEffectConstants
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * D-pad navigation across the app grid: the row ends that swallow a key, the jump to the App library
 * footer, and the way back up from it. The Activity keeps the TileListener overrides and delegates here.
 */
internal class MainTileNavigation(
    private val currentAdapter: () -> AppAdapter,
    private val grid: RecyclerView,
    private val gridFocus: LauncherGridFocus,
    private val singleRow: Boolean,
    private val rtlAppGrid: Boolean,
    private val lastFocused: () -> Int,
    private val firstTopButton: () -> View,
) {
    fun onTileVerticalKey(position: Int, keyCode: Int): Boolean {
        val adapter = currentAdapter()
        val count = adapter.appCount()
        if (singleRow) {
            // One row: Up leaves for the top bar, Down has nowhere to go, and Left/Right walk the whole row,
            // stopping at its two ends (the default focus search scrolls the row to the next tile).
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return false
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true
            val forward = if (rtlAppGrid) keyCode == KeyEvent.KEYCODE_DPAD_LEFT else keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            val last = adapter.focusablePosition(count - 1)
            if (forward && position >= last) {
                if (adapter.isLibraryAt(count)) gridFocus.focusLibrary()
                return true
            }
            return !forward && position == 0
        }
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            val forward = if (rtlAppGrid) keyCode == KeyEvent.KEYCODE_DPAD_LEFT else keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            if (forward && position == adapter.focusablePosition(count - 1) && adapter.isLibraryAt(count)) {
                gridFocus.focusLibrary()
                return true
            }
            // In the dock, the last app is the row's end: the empty slots after it take no focus.
            val rowCount = if (position < MainActivity.COLUMNS) Math.min(count, adapter.dockFilled()) else count
            return GridNavigation.atHorizontalEdge(position, rowCount, MainActivity.COLUMNS,
                    rtlAppGrid, keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
        }
        val target: Int
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < MainActivity.COLUMNS) return false          // dock row: let focus move to the top bar
            target = position - MainActivity.COLUMNS
        } else {
            val lastRowStart = ((count - 1) / MainActivity.COLUMNS) * MainActivity.COLUMNS
            if (position >= lastRowStart) {
                if (adapter.isLibraryAt(count)) gridFocus.focusLibrary()
                return true
            }
            target = Math.min(position + MainActivity.COLUMNS, count - 1)
        }
        gridFocus.focusTile(target)
        // The default focus search clicks for Left/Right; Up/Down are handled here, so click for them too.
        grid.playSoundEffect(if (keyCode == KeyEvent.KEYCODE_DPAD_UP)
                SoundEffectConstants.NAVIGATION_UP else SoundEffectConstants.NAVIGATION_DOWN)
        return true
    }

    fun onLibraryUp() {
        val adapter = currentAdapter()
        if (adapter.appCount() == 0) { firstTopButton().requestFocus(); return }
        val lastRow = ((adapter.appCount() - 1) / MainActivity.COLUMNS) * MainActivity.COLUMNS
        gridFocus.focusTile(Math.min(lastRow + lastFocused() % MainActivity.COLUMNS, adapter.appCount() - 1))
    }
}
