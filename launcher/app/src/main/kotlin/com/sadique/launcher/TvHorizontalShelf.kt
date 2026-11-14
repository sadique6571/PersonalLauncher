package com.sadique.launcher

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.HorizontalScrollView

/** Scrolls only far enough to bring focus into view with a small margin. */
class TvHorizontalShelf(context: Context) : HorizontalScrollView(context) {
    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        clipToPadding = false
        isSmoothScrollingEnabled = true
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        if (focused == null) return
        post {
            if (!focused.hasFocus() || width == 0) return@post
            val rect = Rect()
            focused.getDrawingRect(rect)
            offsetDescendantRectToMyCoords(focused, rect)
            val margin = Math.round(20 * resources.displayMetrics.density)
            val left = scrollX + margin
            val right = scrollX + width - margin
            val delta = when {
                rect.left < left -> rect.left - left
                rect.right > right -> rect.right - right
                else -> 0
            }
            if (delta != 0) smoothScrollBy(delta, 0)
        }
    }
}
