package com.sadique.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Calendar;

/** Reference-style dial in its own bounded view, independent of wallpaper/gradient drawing. */
final class SweepAnalogClock extends View {
    private final Paint paint=new Paint(3);
    private final Calendar calendar=Calendar.getInstance();
    private final Path hand=new Path();
    private final RectF faceBounds=new RectF(0,0,200,200);
    private final Runnable frame=this::invalidate;
    private final int frameMs;
    private Bitmap dial;
    private boolean active;
    SweepAnalogClock(Context context){
        super(context);frameMs=MemoryProfile.effectiveTier(context,ThemePreferences.get(context))==MemoryProfile.TIER_LIGHT?33:16;
        setContentDescription("Analog clock");setFocusable(false);
    }
    void active(boolean value){active=value;if(value)calendar.setTimeZone(java.util.TimeZone.getDefault());removeCallbacks(frame);invalidate();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(frame);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE)removeCallbacks(frame);else invalidate();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);dial=null;}
    private void makeDial(){
        int size=Math.min(512,Math.max(1,Math.min(getWidth(),getHeight())));dial=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(dial);c.scale(size/200f,size/200f);paint.setStyle(Paint.Style.FILL);paint.setColor(0xff050608);c.drawRoundRect(1,1,199,199,40,40,paint);
        paint.setColor(0xfff2f3f4);c.drawCircle(100,100,92,paint);paint.setColor(0xff080a0d);paint.setStrokeCap(Paint.Cap.BUTT);
        for(int i=0;i<60;i++){int save=c.save();c.rotate(i*6,100,100);paint.setStrokeWidth(i%5==0?3f:1.2f);c.drawLine(100,12,100,i%5==0?21:17,paint);c.restoreToCount(save);}
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextSize(19);paint.setTextAlign(Paint.Align.CENTER);
        for(int i=1;i<=12;i++){double a=Math.toRadians(i*30-90);float x=100+(float)Math.cos(a)*70,y=100+(float)Math.sin(a)*70-(paint.ascent()+paint.descent())/2;c.drawText(Integer.toString(i),x,y,paint);}
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(getWidth()<=0||getHeight()<=0)return;if(dial==null)makeDial();
        float size=Math.min(getWidth(),getHeight());int save=c.save();c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/200f,size/200f);
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);paint.setFilterBitmap(true);c.drawBitmap(dial,null,faceBounds,paint);
        calendar.setTimeInMillis(System.currentTimeMillis());int hour=calendar.get(Calendar.HOUR),minute=calendar.get(Calendar.MINUTE),second=calendar.get(Calendar.SECOND),millis=calendar.get(Calendar.MILLISECOND);
        drawHand(c,AnalogClockAngles.hours(hour,minute,second,millis),49,5.8f);drawHand(c,AnalogClockAngles.minutes(minute,second,millis),70,4.6f);
        int secondsSave=c.save();c.rotate(AnalogClockAngles.seconds(second,millis),100,100);paint.setColor(0xffff9800);paint.setStrokeWidth(2f);paint.setStrokeCap(Paint.Cap.ROUND);c.drawLine(100,110,100,27,paint);c.restoreToCount(secondsSave);
        paint.setColor(0xff080a0d);c.drawCircle(100,100,5,paint);paint.setColor(0xffff9800);c.drawCircle(100,100,3.6f,paint);
        c.restoreToCount(save);
        if(active&&getWindowVisibility()==VISIBLE&&isShown()){removeCallbacks(frame);postDelayed(frame,frameMs);}
    }
    private void drawHand(Canvas c,float angle,float length,float width){int save=c.save();c.rotate(angle,100,100);paint.setColor(0xff080a0d);paint.setStyle(Paint.Style.FILL);hand.reset();hand.moveTo(100,100-length);hand.lineTo(100+width,94);hand.lineTo(100+width*.7f,110);hand.lineTo(100-width*.7f,110);hand.lineTo(100-width,94);hand.close();c.drawPath(hand,paint);c.restoreToCount(save);}
}
