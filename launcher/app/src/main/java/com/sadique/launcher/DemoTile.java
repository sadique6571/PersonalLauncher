package com.sadique.launcher;
import android.content.Context;
import android.graphics.*;
import android.widget.FrameLayout;
/** Clip only artwork/labels; leave the card's own folded edge and focus marker visible. */
final class DemoTile extends FrameLayout {
    private final Path clip=new Path(); private final int style; private final float radius,footer;
    DemoTile(Context context,int style,float radius) {
        super(context); this.style=style; this.radius=radius;
        footer=DemoGeometry.footer(style,Math.max(1,Math.round(getResources().getDisplayMetrics().density)));
        setClipToOutline(false);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh); DemoPaths.set(clip,style,w,Math.max(1,h-footer),radius);
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        int save=canvas.save(); canvas.clipPath(clip); super.dispatchDraw(canvas); canvas.restoreToCount(save);
    }
}
