package com.sadique.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/** One bounded viewport for app tiles and the full-width content shelf. Never nested in a ScrollView. */
final class HomeAppGrid<T> extends RecyclerView {
    interface Factory<T> { View create(T item, int width); }
    private final GridLayoutManager manager;
    private final Tiles adapter;
    private final Function<T, String> key;
    private final Factory<T> factory;
    private List<T> items = Collections.emptyList();
    private View footer;
    private String pendingFocus;
    private int cellWidth;
    private Runnable upFromApps;
    void setUpFromApps(Runnable action) { upFromApps = action; }
    void cancelPendingFocus() { pendingFocus = null; }

    HomeAppGrid(Context context, Function<T, String> key, Factory<T> factory) {
        super(context);
        this.key = key; this.factory = factory;
        manager = new GridLayoutManager(context, 4);
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return position < items.size() ? 1 : manager.getSpanCount();
            }
        });
        setLayoutManager(manager);
        adapter = new Tiles();
        adapter.setHasStableIds(true);
        setAdapter(adapter);
        setItemAnimator(null); // Focus must not bounce or disappear during provider/icon updates.
        setItemViewCacheSize(2);
        setClipToPadding(false);
        setPadding(0, 10, 0, 14);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setPreserveFocusAfterLayout(true);
    }

    void setFooter(View view) {
        footer = view;
        adapter.notifyDataSetChanged();
    }

    int getColumnCount() { return manager.getSpanCount(); }
    int appCount() { return items.size(); }

    void submit(List<T> next, int columns, int width) {
        List<T> before = items;
        boolean resized = manager.getSpanCount() != columns || cellWidth != width;
        cellWidth = width;
        manager.setSpanCount(columns);
        items = new ArrayList<>(next);
        // Snapshot both lists: adapter positions may change again before a pending layout runs.
        List<T> after = items;
        if (resized) adapter.notifyDataSetChanged();
        else DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return before.size() + (footer == null ? 0 : 1); }
            @Override public int getNewListSize() { return after.size() + (footer == null ? 0 : 1); }
            @Override public boolean areItemsTheSame(int a, int b) {
                if (a == before.size() || b == after.size()) return a == before.size() && b == after.size();
                return key.apply(before.get(a)).equals(key.apply(after.get(b)));
            }
            @Override public boolean areContentsTheSame(int a, int b) {
                return a == before.size() || before.get(a) == after.get(b);
            }
        }).dispatchUpdatesTo(adapter);
    }

    /** Deterministic row/column navigation before framework geometric focus search. */
    boolean navigateKey(android.view.KeyEvent event) {
        int code = event.getKeyCode();
        boolean horizontal = code == android.view.KeyEvent.KEYCODE_DPAD_LEFT || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean vertical = code == android.view.KeyEvent.KEYCODE_DPAD_UP || code == android.view.KeyEvent.KEYCODE_DPAD_DOWN;
        if (!horizontal && !vertical) return false;
        int position = -1;
        if (pendingFocus != null) for (int i = 0; i < items.size(); i++)
            if (pendingFocus.equals(key.apply(items.get(i)))) { position = i; break; }
        if (position < 0) {
            View focused = findFocus();
            View cell = focused == null ? null : findContainingItemView(focused);
            if (cell != null) position = getChildAdapterPosition(cell);
        }
        if (position == items.size() && footer != null && vertical) {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && code == android.view.KeyEvent.KEYCODE_DPAD_UP && !items.isEmpty())
                focusKey(key.apply(items.get(items.size() - 1)));
            return true;
        }
        if (position < 0 || position >= items.size()) return false;
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN || event.isCanceled()) return true;
        int next = horizontal ? AppGridNavigation.horizontal(position, items.size(), code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
                : AppGridNavigation.vertical(position, items.size(), manager.getSpanCount(), code == android.view.KeyEvent.KEYCODE_DPAD_DOWN);
        if (next < 0) {
            pendingFocus = null;
            if (upFromApps != null) upFromApps.run();
        } else if (next != position) focusKey(key.apply(items.get(next)));
        else if (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN && footer != null && footer.isShown() && footer.hasFocusable()) {
            pendingFocus = null; footer.requestFocus();
        }
        return true;
    }

    void rebind() { adapter.notifyItemRangeChanged(0, items.size()); }
    /** Focus can change during RecyclerView layout; redraw safely without invalidating its offsets then. */
    void refreshReflections() { if(isComputingLayout())postInvalidateOnAnimation();else invalidateItemDecorations(); }
    void focusKey(String pkg) {
        if (items.isEmpty()) { if (footer != null) footer.requestFocus(); return; }
        int position = 0;
        for (int i = 0; i < items.size(); i++) if (key.apply(items.get(i)).equals(pkg)) { position = i; break; }
        pendingFocus = key.apply(items.get(position));
        restorePendingFocus();
        if (pendingFocus == null) return;
        scrollToPosition(position);
        post(this::restorePendingFocus);
    }

    private void restorePendingFocus() {
        if (pendingFocus == null) return;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (pendingFocus.equals(child.getTag()) && child instanceof FrameLayout) {
                if (((FrameLayout) child).getChildCount() > 0) {
                    ((FrameLayout) child).getChildAt(0).requestFocus(); pendingFocus = null; return;
                }
            }
        }
    }

    private final class Tiles extends Adapter<Cell> {
        @Override public int getItemCount() { return items.size() + (footer == null ? 0 : 1); }
        @Override public int getItemViewType(int position) { return position == items.size() ? 1 : 0; }
        @Override public long getItemId(int position) {
            if (position == items.size()) return Long.MIN_VALUE;
            return HomeLibraryModel.stableId(key.apply(items.get(position)));
        }
        @Override public Cell onCreateViewHolder(ViewGroup parent, int type) {
            FrameLayout frame = new FrameLayout(getContext());
            frame.setClipChildren(false); frame.setClipToPadding(false);
            frame.setFocusable(false);
            frame.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new Cell(frame);
        }
        @Override public void onBindViewHolder(Cell cell, int position) {
            FrameLayout frame = (FrameLayout) cell.itemView;
            frame.removeAllViews();
            if (position == items.size()) {
                if (footer.getParent() instanceof ViewGroup) ((ViewGroup) footer.getParent()).removeView(footer);
                frame.setTag(null); frame.addView(footer, new FrameLayout.LayoutParams(-1, -2));
            } else {
                T item = items.get(position);
                frame.setTag(key.apply(item));
                View tile = factory.create(item, cellWidth);
                frame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                    @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        int current = cell.getBindingAdapterPosition();
                        if (current != RecyclerView.NO_POSITION) {
                            int columns = manager.getSpanCount();
                            info.setCollectionItemInfo(android.view.accessibility.AccessibilityNodeInfo.CollectionItemInfo.obtain(
                                    current / columns, 1, current % columns, 1, false));
                        }
                    }
                });
                frame.addView(tile, tile.getLayoutParams());
            }
        }
        @Override public void onViewAttachedToWindow(Cell cell) { restorePendingFocus(); }
        @Override public void onViewRecycled(Cell cell) {
            ((FrameLayout) cell.itemView).removeAllViews(); cell.itemView.setTag(null);
        }
    }
    private static final class Cell extends ViewHolder { Cell(View item) { super(item); } }
}
