package com.sadique.launcher;

/** Horizontal keys follow the visible row-major app order, stopping at the list boundaries. */
final class AppGridNavigation {
    private AppGridNavigation() { }
    static int vertical(int position, int count, int columns, boolean down) {
        if (count <= 0 || columns <= 0 || position < 0 || position >= count) return -1;
        if (!down) return position < columns ? -1 : position - columns;
        if (position / columns == (count - 1) / columns) return position;
        return Math.min(count - 1, position + columns);
    }
    static int verticalWithFooter(int position,int count,int columns,boolean down,boolean footerEnabled){
        int next=vertical(position,count,columns,down);
        return down&&footerEnabled&&next==position&&position>=0&&position<count?count:next;
    }
    /** Stable identity wins over list order; a removed app falls back to its nearest old position. */
    static int footerReturn(java.util.List<String> keys,String enteredFrom,int oldPosition){
        if(keys.isEmpty())return -1;
        int found=enteredFrom==null?-1:keys.indexOf(enteredFrom);
        return found>=0?found:Math.max(0,Math.min(keys.size()-1,oldPosition));
    }
    static int horizontal(int position, int count, boolean right) {
        if (count <= 0 || position < 0 || position >= count) return -1;
        return Math.max(0, Math.min(count - 1, position + (right ? 1 : -1)));
    }
}
