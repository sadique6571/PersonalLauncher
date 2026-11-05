package com.sadique.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/** The volume capsule, rotated horizontally: the entire body fills, with readable text over both halves. */
final class DownloadCapsule extends View {
    private final GlassCapsule capsule;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private String caption="Checking GitHub", fitted=caption;
    private int percent=-1;
    private float baseline;
    DownloadCapsule(Context context) {
        super(context); capsule=new GlassCapsule(context);
        text.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        text.setTextSize(14*getResources().getDisplayMetrics().scaledDensity); text.setTextAlign(Paint.Align.CENTER);
        setFocusable(false); setClickable(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    void progress(String message, int percent) {
        this.percent=percent;
        caption=message+(percent<0 ? "" : " · "+percent+"%");
        setContentDescription(caption); fit(); invalidate();
    }
    private void fit() {
        float margin=16*getResources().getDisplayMetrics().density;
        fitted=TextUtils.ellipsize(caption,text,Math.max(1,getWidth()-margin*2),TextUtils.TruncateAt.END).toString();
        baseline=(getHeight()-text.ascent()-text.descent())/2f;
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh); capsule.size(w,h); fit();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float fraction=Math.max(0,Math.min(100,percent))/100f;
        boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;
        capsule.draw(canvas,fraction,false,rtl);
        text.setColor(0xffffffff); canvas.drawText(fitted,getWidth()/2f,baseline,text);
        // Repaint only the text over the full-height white fill, as the volume symbol does.
        if (fraction>0) {
            int save=canvas.save();
            if (rtl) canvas.clipRect(getWidth()*(1-fraction),0,getWidth(),getHeight());
            else canvas.clipRect(0,0,getWidth()*fraction,getHeight());
            text.setColor(0xff142239); canvas.drawText(fitted,getWidth()/2f,baseline,text);
            canvas.restoreToCount(save);
        }
    }
}
