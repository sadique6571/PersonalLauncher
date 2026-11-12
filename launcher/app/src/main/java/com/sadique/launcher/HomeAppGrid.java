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
    private final boolean horizontalDock;
    private boolean pendingFooter;
    private String pendingFocus;
    private String lastAppKey,footerReturnKey,returnVisibleKey;
    private int lastAppPosition,footerReturnPosition,returnVisibleOffset;
    private int cellWidth,columns=4;
    private boolean referenceExpansion;
    private String expandedKey,pendingExpansion;
    private int priorExpanded=-1,nextExpanded=-1;
    private float expansion=1;
    private android.animation.ValueAnimator expansionAnimator;
    private final Runnable applyExpansion=this::applyExpansion;
    void setReferenceExpansion(boolean enabled){referenceExpansion=enabled;manager.setSpanCount(horizontalDock ? 1 : enabled ? GlassDockLayout.SPANS : columns);}
    void expandKey(String key){if(!referenceExpansion)return;pendingExpansion=key;removeCallbacks(applyExpansion);post(applyExpansion);}
    private int index(String value){if(value!=null)for(int i=0;i<items.size();i++)if(value.equals(key.apply(items.get(i))))return i;return -1;}
    private void applyExpansion(){
        if(java.util.Objects.equals(expandedKey,pendingExpansion))return;
        if(expansionAnimator!=null)expansionAnimator.cancel();
        priorExpanded=index(expandedKey);nextExpanded=index(pendingExpansion);expandedKey=pendingExpansion;expansion=0;
        if(MemoryProfile.effectiveTier(getContext(),ThemePreferences.get(getContext()))==MemoryProfile.TIER_LIGHT){expansion=1;relayoutExpansion();return;}
        expansionAnimator=android.animation.ValueAnimator.ofFloat(0,1);expansionAnimator.setDuration(260);
        expansionAnimator.addUpdateListener(a->{expansion=(float)a.getAnimatedValue();relayoutExpansion();});expansionAnimator.start();
    }
    private void relayoutExpansion(){manager.getSpanSizeLookup().invalidateSpanIndexCache();manager.getSpanSizeLookup().invalidateSpanGroupIndexCache();requestLayout();refreshReflections();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(applyExpansion);if(expansionAnimator!=null)expansionAnimator.cancel();super.onDetachedFromWindow();}
    private Runnable upFromApps;
    void setUpFromApps(Runnable action) { upFromApps = action; }
    void cancelPendingFocus() { pendingFocus = null; pendingFooter = false; }

    HomeAppGrid(Context context, Function<T, String> key, Factory<T> factory, boolean horizontalDock) {
        super(context);
        this.key = key; this.factory = factory;
        this.horizontalDock = horizontalDock;
        manager = new GridLayoutManager(context, horizontalDock ? 1 : 4,
                horizontalDock ? HORIZONTAL : VERTICAL, false) {
            @Override public void onLayoutCompleted(RecyclerView.State state) {
                super.onLayoutCompleted(state);
                if (pendingFocus != null) post(HomeAppGrid.this::restorePendingFocus);
            }
            @Override public int getColumnCountForAccessibility(RecyclerView.Recycler recycler,RecyclerView.State state){return horizontalDock ? Math.max(1, items.size()) : columns;}
        };
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                if (position >= items.size()) return manager.getSpanCount();
                return referenceExpansion?GlassDockLayout.span(position,columns,priorExpanded,nextExpanded,expansion):1;
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

    boolean isHorizontalDock() { return horizontalDock; }

    void setFooter(View view) {
        footer = view;
        adapter.notifyDataSetChanged();
    }

    boolean footerFocusedOrPending(){return pendingFooter||(footer!=null&&footer.hasFocus());}
    @Override public void requestChildFocus(View child,View focused){
        View cell=focused==null?null:findContainingItemView(focused);
        int position=cell==null?NO_POSITION:getChildAdapterPosition(cell);
        if(position>=0&&position<items.size()){lastAppKey=key.apply(items.get(position));lastAppPosition=position;}
        super.requestChildFocus(child,focused);
    }
    int getColumnCount() { return columns; }
    int appCount() { return items.size(); }

    void submit(List<T> next, int columns, int width) {
        boolean preserveFooter=footerFocusedOrPending();
        List<T> before = items;
        boolean resized = this.columns != columns || cellWidth != width;
        cellWidth = width;
        this.columns=columns;manager.setSpanCount(horizontalDock ? 1 : referenceExpansion ? GlassDockLayout.SPANS : columns);
        items = new ArrayList<>(next);
        if(referenceExpansion){if(expansionAnimator!=null)expansionAnimator.cancel();priorExpanded=-1;nextExpanded=index(expandedKey);expansion=1;manager.getSpanSizeLookup().invalidateSpanIndexCache();manager.getSpanSizeLookup().invalidateSpanGroupIndexCache();}
        // Snapshot both lists: adapter positions may change again before a pending layout runs.
        List<T> after = items;
        if (resized) adapter.notifyDataSetChanged();
        else {
            DiffUtil.calculateDiff(new DiffUtil.Callback() {
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
        if(preserveFooter){pendingFocus=null;pendingFooter=true;post(this::restoreFooterFocus);}
    }

    /** Deterministic row/column navigation before framework geometric focus search. */
    boolean navigateKey(android.view.KeyEvent event) {
        int code = event.getKeyCode();
        boolean horizontal = code == android.view.KeyEvent.KEYCODE_DPAD_LEFT || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean vertical = code == android.view.KeyEvent.KEYCODE_DPAD_UP || code == android.view.KeyEvent.KEYCODE_DPAD_DOWN;
        if (!horizontal && !vertical) return false;
        if(pendingFooter){
            if(vertical&&code==android.view.KeyEvent.KEYCODE_DPAD_UP&&event.getAction()==android.view.KeyEvent.ACTION_DOWN&&!event.isCanceled())returnFromFooter();
            return true;
        }
        if(!hasFocus()&&pendingFocus==null)return false;
        int position = -1;
        if (pendingFocus != null) for (int i = 0; i < items.size(); i++)
            if (pendingFocus.equals(key.apply(items.get(i)))) { position = i; break; }
        if (position < 0) {
            View focused = findFocus();
            View cell = focused == null ? null : findContainingItemView(focused);
            if (cell != null) position = getChildAdapterPosition(cell);
        }
        if (position == items.size() && footer != null && vertical) {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && code == android.view.KeyEvent.KEYCODE_DPAD_UP)
                returnFromFooter();
            return true;
        }
        if (position < 0 || position >= items.size()) return false;
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN || event.isCanceled()) return true;
        if (horizontalDock && vertical) {
            pendingFocus = null;
            if (code == android.view.KeyEvent.KEYCODE_DPAD_UP && upFromApps != null) upFromApps.run();
            return true;
        }
        boolean right = code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean forward = horizontalDock && getLayoutDirection() == LAYOUT_DIRECTION_RTL ? !right : right;
        int next = horizontal ? AppGridNavigation.horizontal(position, items.size(), forward)
                : AppGridNavigation.verticalWithFooter(position,items.size(),columns,code==android.view.KeyEvent.KEYCODE_DPAD_DOWN,footer!=null&&footer.getVisibility()!=GONE);
        if (next < 0) {
            pendingFocus = null;
            if (upFromApps != null) upFromApps.run();
        } else if(next==items.size()&&footer!=null)enterFooter(position);
        else if (next != position) focusKey(key.apply(items.get(next)));
        return true;
    }

    void rebind() { adapter.notifyItemRangeChanged(0, items.size()); }
    /** Focus can change during RecyclerView layout; redraw safely without invalidating its offsets then. */
    void refreshReflections() { if(isComputingLayout())postInvalidateOnAnimation();else invalidateItemDecorations(); }
    private void enterFooter(int position){
        footerReturnPosition=position;footerReturnKey=key.apply(items.get(position));
        int first=manager.findFirstVisibleItemPosition();View anchor=manager.findViewByPosition(first);
        returnVisibleKey=first>=0&&first<items.size()?key.apply(items.get(first)):footerReturnKey;
        returnVisibleOffset=anchor==null?0:manager.getDecoratedTop(anchor)-getPaddingTop();
        focusFooter();
    }
    private void focusFooter(){
        if(footer==null||footer.getVisibility()==GONE)return;
        stopScroll();pendingFocus=null;pendingFooter=true;scrollToPosition(items.size());restoreFooterFocus();post(this::restoreFooterFocus);
    }
    private void returnFromFooter(){
        stopScroll();pendingFooter=false;
        if(items.isEmpty()){pendingFocus=null;if(upFromApps!=null)upFromApps.run();return;}
        List<String> keys=new ArrayList<>();for(T item:items)keys.add(key.apply(item));
        int position=AppGridNavigation.footerReturn(keys,footerReturnKey==null?lastAppKey:footerReturnKey,footerReturnKey==null?lastAppPosition:footerReturnPosition);
        pendingFocus=keys.get(position);
        int anchor=index(returnVisibleKey);
        manager.scrollToPositionWithOffset(anchor>=0?anchor:position,anchor>=0?returnVisibleOffset:0);
        post(this::restorePendingFocus);
    }
    private void restoreFooterFocus(){
        if(!pendingFooter||footer==null||footer.getParent()==null||!footer.isShown())return;
        boolean focused=footer instanceof ContinueWatchingView?((ContinueWatchingView)footer).focusEntry():footer.requestFocus();
        if(focused)pendingFooter=false;
    }
    void focusKey(String pkg) {
        cancelPendingFocus();
        if (items.isEmpty()) { focusFooter(); return; }
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
                    if(((FrameLayout) child).getChildAt(0).requestFocus())pendingFocus=null; return;
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
            frame.setClipChildren(horizontalDock); frame.setClipToPadding(horizontalDock);
            frame.setFocusable(false);
            frame.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new Cell(frame);
        }
        @Override public void onBindViewHolder(Cell cell, int position) {
            FrameLayout frame = (FrameLayout) cell.itemView;
            frame.removeAllViews();
            if (horizontalDock) frame.getLayoutParams().width = cellWidth;
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
                        if (current >= 0) {
                            int columns = getColumnCount();
                            info.setCollectionItemInfo(android.view.accessibility.AccessibilityNodeInfo.CollectionItemInfo.obtain(
                                    horizontalDock ? 0 : current / columns, 1, horizontalDock ? current : current % columns, 1, false));
                        }
                    }
                });
                frame.addView(tile, tile.getLayoutParams());
            }
        }
        @Override public void onViewAttachedToWindow(Cell cell) { restorePendingFocus(); restoreFooterFocus(); }
        @Override public void onViewRecycled(Cell cell) {
            ((FrameLayout) cell.itemView).removeAllViews(); cell.itemView.setTag(null);
        }
    }
    private static final class Cell extends ViewHolder { Cell(View item) { super(item); } }
}
