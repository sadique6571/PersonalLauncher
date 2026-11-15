package com.sadique.launcher

import android.app.Activity
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

internal class LauncherGridFocus(
    private val host: Activity,
    private val gridView: () -> RecyclerView,
    private val currentAdapter: () -> AppAdapter,
    private val animationsEnabled: () -> Boolean,
    private val libraryClosed: () -> Boolean,
    private val firstTopButton: () -> View,
    private val onTileFocused: (Int, View) -> Unit,
) {
    var libraryFocusPending = false
    private var pendingFocusScroll: RecyclerView.OnScrollListener? = null
    private val grid: RecyclerView get() = gridView()
    private val adapter: AppAdapter get() = currentAdapter()

    fun focusLibrary() {
        cancelPendingFocusScroll()
        val position = adapter.appCount()
        if (!adapter.isLibraryAt(position)) return
        val holder = grid.findViewHolderForAdapterPosition(position)
        if (holder != null) {
            val button = holder.itemView.findViewById<View>(R.id.btnLibrary)
            val visible = Rect()
            if (button.getGlobalVisibleRect(visible) && visible.height() >= button.height) {
                if (button.requestFocus()) libraryFocusPending = false
                return
            }
        }
        if (!animationsEnabled()) {
            grid.scrollToPosition(position)
            grid.post {
                if (libraryClosed() && host.hasWindowFocus()) {
                    val footer = grid.findViewHolderForAdapterPosition(adapter.appCount())
                    if (footer != null && adapter.isLibraryAt(adapter.appCount())
                            && footer.itemView.findViewById<View>(R.id.btnLibrary).requestFocus()) libraryFocusPending = false
                }
            }
            return
        }
        pendingFocusScroll = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, state: Int) {
                if (state != RecyclerView.SCROLL_STATE_IDLE) return
                rv.removeOnScrollListener(this)
                if (pendingFocusScroll != this) return
                pendingFocusScroll = null
                val footer = rv.findViewHolderForAdapterPosition(position)
                if (footer != null && footer.itemView.findViewById<View>(R.id.btnLibrary).requestFocus())
                    libraryFocusPending = false
            }
        }
        grid.addOnScrollListener(pendingFocusScroll!!)
        grid.smoothScrollToPosition(position)
    }

    fun cancelPendingFocusScroll() {
        if (pendingFocusScroll != null) {
            grid.removeOnScrollListener(pendingFocusScroll!!)
            pendingFocusScroll = null
            grid.stopScroll()
        }
    }

    fun focusTile(position: Int) {
        libraryFocusPending = false
        cancelPendingFocusScroll()
        if (adapter.appCount() == 0) {
            if (adapter.isLibraryAt(0)) focusLibrary()
            else firstTopButton().requestFocus()
            return
        }
        val pos = adapter.focusablePosition(Math.max(0, Math.min(position, adapter.appCount() - 1)))
        val vh = grid.findViewHolderForAdapterPosition(pos)
        if (vh != null) {
            val card = vh.itemView.findViewById<View>(R.id.card)
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused) onTileFocused(pos, card)
            else card.requestFocus()
            return
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        if (!animationsEnabled()) {
            grid.scrollToPosition(pos)
            grid.post {
                val h = grid.findViewHolderForAdapterPosition(pos)
                if (h != null) h.itemView.findViewById<View>(R.id.card).requestFocus()
            }
            return
        }
        pendingFocusScroll = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                rv.removeOnScrollListener(this)
                if (pendingFocusScroll != this) return
                pendingFocusScroll = null
                val h = rv.findViewHolderForAdapterPosition(pos)
                val target = if (h != null) h.itemView.findViewById<View>(R.id.card) else rv.getChildAt(0)
                target?.requestFocus()
            }
        }
        grid.addOnScrollListener(pendingFocusScroll!!)
        grid.smoothScrollToPosition(pos)
    }
}
