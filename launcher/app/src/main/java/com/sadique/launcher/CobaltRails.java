package com.sadique.launcher;
import android.graphics.*;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
/** Thin connected rails behind visible app rows; no animation or blur layer. */
final class CobaltRails extends RecyclerView.ItemDecoration {
    private final Paint ink=new Paint(3); private final float unit;
    CobaltRails(float density) { unit=density; }
    @Override public void onDraw(Canvas c,RecyclerView parent,RecyclerView.State state) {
        int lastRow=-1; int columns=((androidx.recyclerview.widget.GridLayoutManager)parent.getLayoutManager()).getSpanCount();
        for(int i=0;i<parent.getChildCount();i++) {
            View child=parent.getChildAt(i); int pos=parent.getChildAdapterPosition(child);
            if(pos<0 || child.getTag()==null || pos/columns==lastRow) continue;
            lastRow=pos/columns; float y=child.getBottom()-unit*12;
            if(child instanceof android.view.ViewGroup && ((android.view.ViewGroup)child).getChildCount()>0) {
                View tile=((android.view.ViewGroup)child).getChildAt(0);
                y=child.getTop()+tile.getTop()+tile.getHeight()-DemoGeometry.footer(ThemeStyle.COBALT,unit);
            }
            ink.setColor(0x553379ff); ink.setStrokeWidth(unit*4); c.drawLine(0,y,parent.getWidth(),y,ink);
            ink.setColor(0xff4b8aff); ink.setStrokeWidth(unit); c.drawLine(0,y,parent.getWidth(),y,ink);
        }
    }
}
