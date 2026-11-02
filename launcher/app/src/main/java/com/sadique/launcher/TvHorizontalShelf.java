package com.sadique.launcher;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.HorizontalScrollView;

/** TV shelf: focus scrolls the minimum distance, with a small breathing space at either edge. */
final class TvHorizontalShelf extends HorizontalScrollView {
    TvHorizontalShelf(Context context) {
        super(context);
        setHorizontalScrollBarEnabled(false); setFillViewport(false);
        setClipToPadding(false); setSmoothScrollingEnabled(true);
    }
    @Override public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (focused == null) return;
        post(() -> {
            if (!focused.hasFocus() || getWidth() == 0) return;
            Rect rect = new Rect(); focused.getDrawingRect(rect); offsetDescendantRectToMyCoords(focused, rect);
            int margin = Math.round(20 * getResources().getDisplayMetrics().density);
            int left = getScrollX() + margin, right = getScrollX() + getWidth() - margin;
            int delta = rect.left < left ? rect.left - left : rect.right > right ? rect.right - right : 0;
            if (delta != 0) smoothScrollBy(delta, 0);
        });
    }
}
