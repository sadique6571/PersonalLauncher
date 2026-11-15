package com.sadique.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList
import java.util.function.Function

/** One bounded viewport for app tiles and the full-width content shelf. Never nested in a ScrollView. */
internal class HomeAppGrid<T>(
    context: Context,
    private val key: Function<T, String>,
    private val factory: Factory<T>,
    private val horizontalDock: Boolean
) : RecyclerView(context) {
    fun interface Factory<T> {
        fun create(item: T, width: Int): View
    }

    private val manager: GridLayoutManager
    private val tiles: Tiles
    private var items: List<T> = emptyList()
    private var footer: View? = null
    private var pendingFooter = false
    private var pendingFocus: String? = null
    private var lastAppKey: String? = null
    private var footerReturnKey: String? = null
    private var returnVisibleKey: String? = null
    private var lastAppPosition = 0
    private var footerReturnPosition = 0
    private var returnVisibleOffset = 0
    private var cellWidth = 0
    private var columns = 4
    private var referenceExpansion = false
    private var expandedKey: String? = null
    private var pendingExpansion: String? = null
    private var priorExpanded = -1
    private var nextExpanded = -1
    private var expansion = 1f
    private var expansionAnimator: ValueAnimator? = null
    private val applyExpansionTask: Runnable = Runnable { applyExpansion() }
    private var upFromApps: Runnable? = null

    init {
        manager = object : GridLayoutManager(context, if (horizontalDock) 1 else 4, if (horizontalDock) HORIZONTAL else VERTICAL, false) {
            override fun onLayoutCompleted(state: RecyclerView.State) {
                super.onLayoutCompleted(state)
                if (pendingFocus != null) this@HomeAppGrid.post { restorePendingFocus() }
            }

            override fun getColumnCountForAccessibility(recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
                return if (horizontalDock) Math.max(1, items.size) else columns
            }
        }
        manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                if (position >= items.size) return manager.spanCount
                return if (referenceExpansion) GlassDockLayout.span(position, columns, priorExpanded, nextExpanded, expansion) else 1
            }
        }
        layoutManager = manager
        tiles = Tiles()
        tiles.setHasStableIds(true)
        setAdapter(tiles)
        itemAnimator = null // Focus must not bounce or disappear during provider/icon updates.
        setItemViewCacheSize(2)
        clipToPadding = false
        setPadding(0, 10, 0, 14)
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        preserveFocusAfterLayout = true
    }

    fun setReferenceExpansion(enabled: Boolean) {
        referenceExpansion = enabled
        manager.spanCount = if (horizontalDock) 1 else if (enabled) GlassDockLayout.SPANS else columns
    }

    fun expandKey(key: String) {
        if (!referenceExpansion) return
        pendingExpansion = key
        removeCallbacks(applyExpansionTask)
        post(applyExpansionTask)
    }

    private fun index(value: String?): Int {
        if (value != null) for (i in items.indices) if (value == key.apply(items[i])) return i
        return -1
    }

    private fun applyExpansion() {
        if (expandedKey == pendingExpansion) return
        expansionAnimator?.cancel()
        priorExpanded = index(expandedKey)
        nextExpanded = index(pendingExpansion)
        expandedKey = pendingExpansion
        expansion = 0f
        if (MemoryProfile.effectiveTier(context, ThemePreferences.get(context)) == MemoryProfile.TIER_LIGHT) {
            expansion = 1f
            relayoutExpansion()
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 260
        animator.addUpdateListener { a ->
            expansion = a.animatedValue as Float
            relayoutExpansion()
        }
        animator.start()
        expansionAnimator = animator
    }

    private fun relayoutExpansion() {
        manager.spanSizeLookup.invalidateSpanIndexCache()
        manager.spanSizeLookup.invalidateSpanGroupIndexCache()
        requestLayout()
        refreshReflections()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(applyExpansionTask)
        expansionAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    fun setUpFromApps(action: Runnable?) {
        upFromApps = action
    }

    fun cancelPendingFocus() {
        pendingFocus = null
        pendingFooter = false
    }

    fun isHorizontalDock(): Boolean = horizontalDock

    fun setFooter(view: View) {
        footer = view
        tiles.notifyDataSetChanged()
    }

    fun footerFocusedOrPending(): Boolean = pendingFooter || footer?.hasFocus() == true

    override fun requestChildFocus(child: View?, focused: View?) {
        val cell = if (focused == null) null else findContainingItemView(focused)
        val position = if (cell == null) NO_POSITION else getChildAdapterPosition(cell)
        if (position >= 0 && position < items.size) {
            lastAppKey = key.apply(items[position])
            lastAppPosition = position
        }
        super.requestChildFocus(child, focused)
    }

    val columnCount: Int
        get() = columns

    fun appCount(): Int = items.size

    fun submit(next: List<T>, columns: Int, width: Int) {
        val preserveFooter = footerFocusedOrPending()
        val before = items
        val resized = this.columns != columns || cellWidth != width
        cellWidth = width
        this.columns = columns
        manager.spanCount = if (horizontalDock) 1 else if (referenceExpansion) GlassDockLayout.SPANS else columns
        items = ArrayList(next)
        if (referenceExpansion) {
            expansionAnimator?.cancel()
            priorExpanded = -1
            nextExpanded = index(expandedKey)
            expansion = 1f
            manager.spanSizeLookup.invalidateSpanIndexCache()
            manager.spanSizeLookup.invalidateSpanGroupIndexCache()
        }
        // Snapshot both lists: adapter positions may change again before a pending layout runs.
        val after = items
        if (resized) tiles.notifyDataSetChanged()
        else {
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = before.size + if (footer == null) 0 else 1
                override fun getNewListSize(): Int = after.size + if (footer == null) 0 else 1
                override fun areItemsTheSame(a: Int, b: Int): Boolean {
                    if (a == before.size || b == after.size) return a == before.size && b == after.size
                    return key.apply(before[a]) == key.apply(after[b])
                }

                override fun areContentsTheSame(a: Int, b: Int): Boolean {
                    return a == before.size || before[a] === after[b]
                }
            }).dispatchUpdatesTo(tiles)
        }
        if (preserveFooter) {
            pendingFocus = null
            pendingFooter = true
            post { restoreFooterFocus() }
        }
    }

    /** Deterministic row/column navigation before framework geometric focus search. */
    fun navigateKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        val horizontal = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
        val vertical = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
        if (!horizontal && !vertical) return false
        if (pendingFooter) {
            if (vertical && code == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN && !event.isCanceled) returnFromFooter()
            return true
        }
        if (!hasFocus() && pendingFocus == null) return false
        var position = -1
        val focus = pendingFocus
        if (focus != null) {
            for (i in items.indices) {
                if (focus == key.apply(items[i])) {
                    position = i
                    break
                }
            }
        }
        if (position < 0) {
            val focused = findFocus()
            val cell = if (focused == null) null else findContainingItemView(focused)
            if (cell != null) position = getChildAdapterPosition(cell)
        }
        val foot = footer
        if (position == items.size && foot != null && vertical) {
            if (event.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_UP)
                returnFromFooter()
            return true
        }
        if (position < 0 || position >= items.size) {
            if (!horizontalDock) return false
            if (event.action == KeyEvent.ACTION_DOWN && !event.isCanceled) {
                if (code == KeyEvent.KEYCODE_DPAD_UP && upFromApps != null) upFromApps?.run()
                else if (horizontal && items.isNotEmpty()) focusKey(lastAppKey)
            }
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN || event.isCanceled) return true
        if (horizontalDock && vertical) {
            pendingFocus = null
            if (code == KeyEvent.KEYCODE_DPAD_UP && upFromApps != null) upFromApps?.run()
            return true
        }
        val right = code == KeyEvent.KEYCODE_DPAD_RIGHT
        val forward = if (horizontalDock && layoutDirection == LAYOUT_DIRECTION_RTL) !right else right
        val footerVisible = foot != null && foot.visibility != GONE
        val next = if (horizontal) AppGridNavigation.horizontal(position, items.size, forward)
        else AppGridNavigation.verticalWithFooter(position, items.size, columns, code == KeyEvent.KEYCODE_DPAD_DOWN, footerVisible)
        if (next < 0) {
            pendingFocus = null
            upFromApps?.run()
        } else if (next == items.size && foot != null) enterFooter(position)
        else if (next != position) focusKey(key.apply(items[next]))
        return true
    }

    fun rebind() {
        tiles.notifyItemRangeChanged(0, items.size)
    }

    /** Focus can change during RecyclerView layout; redraw safely without invalidating its offsets then. */
    fun refreshReflections() {
        if (isComputingLayout) postInvalidateOnAnimation() else invalidateItemDecorations()
    }

    private fun enterFooter(position: Int) {
        footerReturnPosition = position
        footerReturnKey = key.apply(items[position])
        val first = manager.findFirstVisibleItemPosition()
        val anchor = manager.findViewByPosition(first)
        returnVisibleKey = if (first >= 0 && first < items.size) key.apply(items[first]) else footerReturnKey
        returnVisibleOffset = if (anchor == null) 0 else manager.getDecoratedTop(anchor) - paddingTop
        focusFooter()
    }

    private fun focusFooter() {
        val foot = footer
        if (foot == null || foot.visibility == GONE) return
        stopScroll()
        pendingFocus = null
        pendingFooter = true
        scrollToPosition(items.size)
        restoreFooterFocus()
        post { restoreFooterFocus() }
    }

    private fun returnFromFooter() {
        stopScroll()
        pendingFooter = false
        if (items.isEmpty()) {
            pendingFocus = null
            upFromApps?.run()
            return
        }
        val keys = ArrayList<String>()
        for (item in items) keys.add(key.apply(item))
        val position = AppGridNavigation.footerReturn(keys, footerReturnKey ?: lastAppKey, if (footerReturnKey == null) lastAppPosition else footerReturnPosition)
        pendingFocus = keys[position]
        val anchor = index(returnVisibleKey)
        manager.scrollToPositionWithOffset(if (anchor >= 0) anchor else position, if (anchor >= 0) returnVisibleOffset else 0)
        post { restorePendingFocus() }
    }

    private fun restoreFooterFocus() {
        val foot = footer
        if (!pendingFooter || foot == null || foot.parent == null || !foot.isShown) return
        val focused = if (foot is ContinueWatchingView) foot.focusEntry() else foot.requestFocus()
        if (focused) pendingFooter = false
    }

    fun focusKey(pkg: String?) {
        cancelPendingFocus()
        if (items.isEmpty()) {
            focusFooter()
            return
        }
        var position = 0
        for (i in items.indices) {
            if (key.apply(items[i]) == pkg) {
                position = i
                break
            }
        }
        pendingFocus = key.apply(items[position])
        restorePendingFocus()
        if (pendingFocus == null) return
        scrollToPosition(position)
        post { restorePendingFocus() }
    }

    private fun restorePendingFocus() {
        val focus = pendingFocus ?: return
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (focus == child.tag && child is FrameLayout) {
                if (child.childCount > 0) {
                    if (child.getChildAt(0).requestFocus()) pendingFocus = null
                    return
                }
            }
        }
    }

    private inner class Tiles : RecyclerView.Adapter<Cell>() {
        override fun getItemCount(): Int = items.size + if (footer == null) 0 else 1
        override fun getItemViewType(position: Int): Int = if (position == items.size) 1 else 0
        override fun getItemId(position: Int): Long {
            if (position == items.size) return Long.MIN_VALUE
            return HomeLibraryModel.stableId(key.apply(items[position]))
        }

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): Cell {
            val frame = FrameLayout(this@HomeAppGrid.context)
            frame.clipChildren = horizontalDock
            frame.clipToPadding = horizontalDock
            frame.isFocusable = false
            frame.layoutParams = RecyclerView.LayoutParams(-1, -2)
            return Cell(frame)
        }

        override fun onBindViewHolder(cell: Cell, position: Int) {
            val frame = cell.itemView as FrameLayout
            frame.removeAllViews()
            if (horizontalDock) frame.layoutParams.width = cellWidth
            if (position == items.size) {
                val foot = footer
                if (foot != null) {
                    val previousParent = foot.parent
                    if (previousParent is ViewGroup) previousParent.removeView(foot)
                    frame.tag = null
                    frame.addView(foot, FrameLayout.LayoutParams(-1, -2))
                }
            } else {
                val item = items[position]
                frame.tag = key.apply(item)
                val tile = factory.create(item, cellWidth)
                frame.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                tile.accessibilityDelegate = object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        val current = cell.bindingAdapterPosition
                        if (current >= 0) {
                            val cols = columnCount
                            info.setCollectionItemInfo(
                                AccessibilityNodeInfo.CollectionItemInfo.obtain(
                                    if (horizontalDock) 0 else current / cols, 1,
                                    if (horizontalDock) current else current % cols, 1, false
                                )
                            )
                        }
                    }
                }
                frame.addView(tile, tile.layoutParams)
            }
        }

        override fun onViewAttachedToWindow(cell: Cell) {
            restorePendingFocus()
            restoreFooterFocus()
        }

        override fun onViewRecycled(cell: Cell) {
            (cell.itemView as FrameLayout).removeAllViews()
            cell.itemView.tag = null
        }
    }

    private class Cell(item: View) : RecyclerView.ViewHolder(item)
}
