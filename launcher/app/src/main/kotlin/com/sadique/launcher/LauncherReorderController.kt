package com.sadique.launcher

import android.app.Activity
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.HashSet
import com.sadique.launcher.MainActivity.Companion.COLUMNS

internal class LauncherReorderController(
    private val host: Activity,
    private val currentGrid: () -> RecyclerView,
    private val currentAdapter: () -> AppAdapter,
    private val lastFocused: () -> Int,
    private val singleRow: () -> Boolean,
    private val rtlAppGrid: () -> Boolean,
    private val fillDockLeavesSlots: () -> Boolean,
    private val dockApps: () -> List<String>,
    private val setAccent: (Int) -> Unit,
    private val defaultAccent: () -> Int,
    private val setButtonActive: (Boolean) -> Unit,
    private val reloadTiles: () -> Unit,
    private val focusTile: (Int) -> Unit,
    private val openLibrary: () -> Unit,
) {
    /** Reorder mode is on (top-bar button). */
    var reordering = false
        private set
    /** Key of the tile being carried in reorder mode, or null. */
    private var movingKey: String? = null
    /** Tile order when reorder mode started, to save only when something moved. */
    private var orderAtStart: List<String>? = null
    /**
     * fill_dock false: tiles in the dock when reorder mode started, plus those carried or swapped into it since.
     * A grid app that only slid into a free slot, which reorder mode does not show, is not one of them.
     */
    private var dockInSession: MutableSet<String>? = null
    /** fill_dock false: how many dock slots held an app when reorder mode started. */
    private var dockedAtStart = 0
    /** Edit-dock mode (Settings menu): a reorder session that swaps one chosen app into one dock place. */
    private var dockEditing = false
    /** Dock place being changed in edit-dock mode, or -1 while the place is still being chosen. */
    private var dockEditSlot = -1
    /** Tile menu's "Add to dock": the app waiting for a dock place in edit-dock mode, or null. */
    private var dockEditAppKey: String? = null
    private val grid get() = currentGrid()
    private val adapter get() = currentAdapter()

    fun dispatch(event: KeyEvent, fallback: () -> Boolean): Boolean {
        if (!reordering) return fallback()
        if (dockEditing) return dispatchDockEdit(event, fallback)
        val down = event.action == KeyEvent.ACTION_DOWN
        val code = event.keyCode
        val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER
        val back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE
        if (movingKey != null) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (down) moveBy(code)
                    return true
                }
                else -> {
                    if (ok || back) {
                        if (!down) drop()
                        return true
                    }
                    return fallback()
                }
            }
        }
        if (back) {
            if (!down) endReorder()
            return true
        }
        val pos = focusedTilePosition()
        val focus = host.currentFocus
        if (ok && focus != null && focus.id == R.id.btnLibrary) {
            // The App library pill sits in the grid but is no tile: OK ends the mode (saving the order) and opens it.
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                endReorder()
                openLibrary()
            }
            return true
        }
        if (ok && grid.hasFocus()) {
            // RecyclerView can briefly report NO_POSITION during a move or rebind. Still consume
            // OK on the grid: falling through would let the focused card open its app in reorder mode.
            if (!down && pos >= 0) pickUp(pos)
            return true
        }
        return fallback()   // arrows navigate; OK on a top-bar pill works as usual
    }

    /** Adapter position of the focused tile, or -1 when focus is not on a tile. */
    fun focusedTilePosition(): Int {
        val f = host.currentFocus
        val parent = f?.parent
        if (f == null || f.id != R.id.card || parent !is View) return -1
        return grid.getChildAdapterPosition(parent)
    }

    /** The reorder button: turns the mode on (focus goes back to the last tile), or off again. */
    fun toggleReorder() {
        if (reordering) {
            endReorder()
            return
        }
        if (!beginReorder()) return
        focusTile(Math.max(0, Math.min(lastFocused(), adapter.movableCount() - 1)))
        Toast.makeText(host, R.string.reorder_hint, Toast.LENGTH_LONG).show()
    }

    fun startMoving(key: String) {
        if (!beginReorder()) return
        val position = adapter.positionOf(key)
        if (position < 0) return
        focusTile(position)
        pickUp(position)
        Toast.makeText(host, R.string.reorder_hint, Toast.LENGTH_LONG).show()
    }

    /** The start shared by reorder and edit-dock mode. False when there is nothing to move. */
    private fun beginReorder(): Boolean {
        if (adapter.movableCount() < 2) return false
        // Before the reload below: once it has dropped the empty slots, the dock row also shows grid apps.
        val dock = HashSet<String>()
        dockInSession = dock
        dockedAtStart = adapter.dockFilled()
        for (i in 0 until Math.min(dockedAtStart, adapter.movableCount())) adapter.keyAt(i)?.let { dock.add(it) }
        adapter.setReordering(true)
        if (adapter.hasEmptySlots()) reloadTiles()   // every position must hold a tile that can move
        reordering = true
        orderAtStart = adapter.movableKeys()
        setAccent(REORDER_ACCENT)
        setButtonActive(true)
        return true
    }

    /** Edit dock (Settings menu): focus goes to the first dock place, which OK then chooses. */
    fun startDockEdit() {
        if (reordering) endReorder()
        if (!beginReorder()) return
        dockEditing = true
        dockEditSlot = -1
        focusTile(0)
        Toast.makeText(host, R.string.dock_edit_hint, Toast.LENGTH_LONG).show()
    }

    /**
     * Keys in edit-dock mode. First OK on a dock tile chooses that place (the other dock tiles dim) and focus moves
     * to the grid; then OK on any tile swaps that app into the place, saves the order and ends the mode. Back goes
     * one step back: from choosing the app to choosing the place, and from there out of the mode.
     */
    private fun dispatchDockEdit(event: KeyEvent, fallback: () -> Boolean): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val code = event.keyCode
        val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER
        val back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE
        if (back) {
            if (!down) {
                if (dockEditAppKey != null) {
                    endReorder()   // "Add to dock" cancelled before a place was chosen
                } else if (dockEditSlot >= 0) {
                    val slot = dockEditSlot
                    dockEditSlot = -1
                    dimOthers()
                    focusTile(slot)
                } else {
                    endReorder()
                }
            }
            return true
        }
        val pos = focusedTilePosition()
        if (ok && pos >= 0) {
            if (!down) {
                val n = adapter.movableCount()
                if (dockEditAppKey != null) {
                    if (pos < Math.min(COLUMNS, n)) {
                        val from = adapter.positionOf(dockEditAppKey)
                        if (from >= 0) swapIntoDock(pos, from)
                        else endReorder()
                    }
                } else if (dockEditSlot < 0) {
                    if (pos < Math.min(COLUMNS, n)) {
                        dockEditSlot = pos
                        dimOthers()
                        focusTile(Math.min(COLUMNS, n - 1))
                        Toast.makeText(host, R.string.dock_edit_pick_app, Toast.LENGTH_LONG).show()
                    }
                } else if (pos < n) {
                    swapIntoDock(dockEditSlot, pos)
                }
            }
            return true   // never opens the app while editing
        }
        return fallback()   // arrows navigate as usual
    }

    /**
     * Edit-dock mode started from the tile menu. A dock app: its place is already chosen, so focus goes to the grid
     * for the app to put there. A grid app: it is already chosen, so focus goes to the dock for the place.
     */
    fun startDockEditFor(key: String, inDock: Boolean) {
        if (reordering) endReorder()
        if (!beginReorder()) return
        dockEditing = true
        val pos = adapter.positionOf(key)   // beginReorder can rebuild the tiles
        val n = adapter.movableCount()
        if (pos < 0) {
            endReorder()
        } else if (inDock) {
            dockEditSlot = pos
            dimOthers()
            focusTile(Math.min(COLUMNS, n - 1))
            Toast.makeText(host, R.string.dock_edit_pick_app, Toast.LENGTH_LONG).show()
        } else {
            dockEditAppKey = key
            focusTile(0)
            Toast.makeText(host, R.string.dock_edit_pick_place, Toast.LENGTH_LONG).show()
        }
    }

    /** Puts the app at pos into dock place slot, and the app that was there where the chosen one was; then saves. */
    private fun swapIntoDock(slot: Int, pos: Int) {
        val slotKey = adapter.keyAt(slot)
        if (pos != slot) {
            keepScroll {
                adapter.move(pos, slot)
                adapter.move(adapter.positionOf(slotKey), pos)
            }
            adapter.keyAt(slot)?.let { dockInSession!!.add(it) }
            if (pos >= COLUMNS) dockInSession!!.remove(slotKey)
        }
        endReorder()
        refocusAfterLayout(slot)
    }

    private fun pickUp(pos: Int) {
        if (pos >= adapter.movableCount()) return   // the "Default home" tile stays put
        movingKey = adapter.keyAt(pos)
        dimOthers()
    }

    /** Puts the carried tile down where it is; the mode stays on. */
    private fun drop() {
        movingKey = null
        dimOthers()
    }

    /** While a tile is carried, every other tile is dimmed so the carried one stands out; otherwise all are full. */
    private fun dimOthers() {
        for (i in 0 until grid.childCount) {
            val item = grid.getChildAt(i)
            item.alpha = tileAlpha(grid.getChildAdapterPosition(item))
        }
    }

    /** A tile's alpha: dim unless it is the carried one, or, in edit-dock mode, a dock tile other than the chosen place. */
    fun tileAlpha(pos: Int): Float {
        val key = movingKey
        if (key != null) {
            return if (pos != RecyclerView.NO_POSITION && key == adapter.keyAt(pos)) 1f else 0.45f
        }
        if (dockEditSlot >= 0 && pos >= 0 && pos < COLUMNS && pos != dockEditSlot) return 0.45f
        return 1f
    }

    /** Ends the mode and saves the order if it changed since the mode started. */
    fun endReorder() {
        if (!reordering) return
        dockEditing = false
        dockEditSlot = -1
        dockEditAppKey = null
        drop()
        reordering = false
        adapter.setReordering(false)
        setAccent(defaultAccent())
        setButtonActive(false)
        var keys = adapter.movableKeys()
        val before = orderAtStart
        orderAtStart = null
        // fill_dock false: the dock is what the owner put there, saved with its size, so the empty slots that
        // come back after the mode go after it. With fill_dock true every dock slot holds an app anyway.
        var docked = -1
        if (fillDockLeavesSlots()) {
            docked = DockSlots.dockCount(keys, COLUMNS, dockInSession!!)
            keys = DockSlots.dockFirst(keys, COLUMNS, dockInSession!!)
        }
        dockInSession = null
        if (keys != before || (fillDockLeavesSlots() && docked != dockedAtStart)) {
            val order = Order(Order.dockKey(dockApps()), keys, docked)
            if (Order.save(Order.file(host), order)) {
                adapter.setSavedOrder(Order.ranks(order, dockApps()), Order.docked(order, dockApps()))
            } else {
                Toast.makeText(host, R.string.reorder_not_saved, Toast.LENGTH_SHORT).show()
            }
        }
        if (fillDockLeavesSlots()) reloadTiles()   // the empty dock slots left out while reordering come back
    }

    /**
     * Moves the carried tile one step. Left and Right wrap across rows, like a list; Up and Down move a whole row,
     * and Down into a short last row lands on its last tile. Nothing moves past either end or up into the top bar.
     */
    private fun moveBy(keyCode: Int) {
        val key = movingKey ?: run { drop(); return }
        val from = adapter.positionOf(key)
        val n = adapter.movableCount()
        if (from < 0 || from >= n) {
            drop()
            return
        }
        // A single row has no rows to move between: Up and Down leave the carried tile where it is.
        if (singleRow() && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) return
        val to = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            // rtl_app_grid (right-to-left) mirrors the grid: position 0 is on the right, so Left moves towards the end.
            if (rtlAppGrid()) from + 1 else from - 1
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (rtlAppGrid()) from - 1 else from + 1
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            from - COLUMNS
        } else if (from + COLUMNS < n) {
            from + COLUMNS
        } else if (from / COLUMNS < (n - 1) / COLUMNS) {
            n - 1
        } else {
            from
        }
        if (to < 0 || to >= n || to == from) return
        keepScroll { adapter.move(from, to) }
        // The owner carries this tile: where it lands decides whether it is in the dock, not dock_apps.
        if (to < COLUMNS) dockInSession!!.add(key)
        else dockInSession!!.remove(key)
        refocusAfterLayout(to)
    }

    /**
     * Applies an adapter change without the grid jumping. After a move the layout manager anchors on the focused
     * tile, keeping it at its old place on screen and shifting every other row; pinning the first visible row at its
     * current offset as the next layout's anchor keeps the scroll where it was. onTileFocused still scrolls
     * afterwards if the moved tile itself ends up off screen.
     */
    private fun keepScroll(change: Runnable) {
        val lm = grid.layoutManager as GridLayoutManager
        val first = lm.findFirstVisibleItemPosition()
        val firstView = if (first != RecyclerView.NO_POSITION) lm.findViewByPosition(first) else null
        // single_row scrolls sideways, so its first visible tile is pinned by its left edge instead; mirrored by
        // rtl_app_grid, the row is laid out from the right, and the offset counts from the right edge.
        val offset = if (firstView == null) 0
                else if (singleRow() && rtlAppGrid()) grid.width - grid.paddingRight - lm.getDecoratedRight(firstView)
                else if (singleRow()) lm.getDecoratedLeft(firstView) - grid.paddingLeft
                else lm.getDecoratedTop(firstView) - grid.paddingTop
        change.run()
        if (firstView != null) lm.scrollToPositionWithOffset(first, offset)
    }

    /**
     * Once the grid has applied the change, put the glass (and focus, if the move dropped it) on the tile at pos.
     * The grid has a fixed size, so it applies adapter changes on the next animation frame rather than in a layout
     * pass; until then it cannot find the tile by position. Wait frame by frame (at most 10) for that. No focus
     * change fires for a moved tile, so focusTile's already-focused path calls onTileFocused itself.
     */
    private fun refocusAfterLayout(pos: Int) {
        grid.postOnAnimation(object : Runnable {
            var frames = 0

            override fun run() {
                if ((grid.hasPendingAdapterUpdates() || grid.isLayoutRequested) && ++frames < 10) {
                    grid.postOnAnimation(this)
                    return
                }
                focusTile(pos)
                dimOthers()   // views that came into view or were rebound during the move
            }
        })
    }

    private companion object {
        private val REORDER_ACCENT = 0xFF6EC1FF.toInt()
    }
}
