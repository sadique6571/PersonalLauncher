package com.sadique.launcher;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/** Circular photo with the reference's white-to-focused-app ring. Shared profile owns the bitmap. */
final class AppAccentAvatar extends ImageView {
    private final Paint photo=new Paint(3),ring=new Paint(3),dot=new Paint(3);
    private final Path clip=new Path();
    private final Matrix matrix=new Matrix();
    private Drawable last;
    private int accent=0xffe50914;
    private boolean online;
    private int cachedWidth,cachedHeight;
    AppAccentAvatar(Context context) { super(context); setScaleType(ScaleType.CENTER_CROP); }
    void accent(int color) { accent=color; rebuildRing(); invalidate(); }
    void online(boolean value) { online=value; invalidate(); }
    private float dp(float n) { return n*getResources().getDisplayMetrics().density; }
    private void rebuildRing() {
        ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(dp(1.5f));
        ring.setShader(new LinearGradient(0,0,Math.max(1,getWidth()),Math.max(1,getHeight()),0xffffffff,accent,Shader.TileMode.CLAMP));
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        last=null; rebuildRing(); clip.reset(); clip.addCircle(w/2f,h/2f,Math.max(0,Math.min(w,h)/2f-dp(2)),Path.Direction.CW);
    }
    @Override protected void onDraw(Canvas c) {
        Drawable drawable=getDrawable();
        if(drawable!=last||cachedWidth!=getWidth()||cachedHeight!=getHeight()) {
            last=drawable; cachedWidth=getWidth();cachedHeight=getHeight(); photo.setShader(null);
            if(drawable instanceof BitmapDrawable) {
                Bitmap b=((BitmapDrawable)drawable).getBitmap();
                if(b!=null&&!b.isRecycled()) {
                    BitmapShader shader=new BitmapShader(b,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
                    float s=Math.max(getWidth()/(float)b.getWidth(),getHeight()/(float)b.getHeight());
                    matrix.setScale(s,s); matrix.postTranslate((getWidth()-b.getWidth()*s)/2,(getHeight()-b.getHeight()*s)/2);
                    shader.setLocalMatrix(matrix);photo.setShader(shader);
                }
            }
        }
        int save=c.save();c.clipPath(clip);
        if(photo.getShader()!=null)c.drawRect(0,0,getWidth(),getHeight(),photo);else super.onDraw(c);
        c.restoreToCount(save);
        c.drawCircle(getWidth()/2f,getHeight()/2f,Math.max(0,Math.min(getWidth(),getHeight())/2f-dp(1)),ring);
        float x=getWidth()*.83f,y=getHeight()*.83f;
        dot.setColor(0xff0e1320);c.drawCircle(x,y,dp(4.5f),dot);
        dot.setColor(online?0xff22c55e:0xffef4444);c.drawCircle(x,y,dp(3),dot);
    }
}
