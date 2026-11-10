package com.sadique.launcher;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Small native weather animation; no bitmap, network work or full-screen effects. */
final class WeatherConditionView extends View {
    private final Paint paint=new Paint(3);
    private final Path cloud=new Path(),moon=new Path(),bolt=new Path();
    private int code=-1;private boolean day=true,active,animation=true;
    private final int foreground,frameMs;
    private final Runnable frame=this::invalidate;
    WeatherConditionView(Context context){
        super(context);foreground=ThemeStyle.foreground(ThemePreferences.get(context));frameMs=MemoryProfile.effectiveTier(context,ThemePreferences.get(context))==MemoryProfile.TIER_LIGHT?125:66;
        cloud.moveTo(15,42);cloud.cubicTo(1,41,2,23,15,23);cloud.cubicTo(16,7,42,6,46,24);cloud.cubicTo(63,21,66,42,50,42);cloud.close();
        moon.addCircle(29,25,16,Path.Direction.CW);Path cut=new Path();cut.addCircle(36,18,15,Path.Direction.CW);moon.op(cut,Path.Op.DIFFERENCE);
        bolt.moveTo(35,28);bolt.lineTo(24,46);bolt.lineTo(34,44);bolt.lineTo(28,61);bolt.lineTo(46,38);bolt.lineTo(35,40);bolt.close();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void condition(int code,boolean day){this.code=code;this.day=day;invalidate();}
    void active(boolean value,boolean animation){active=value;this.animation=animation;removeCallbacks(frame);invalidate();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(frame);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE)removeCallbacks(frame);else invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);int save=canvas.save();canvas.scale(getWidth()/64f,getHeight()/64f);
        float phase=active&&animation?(SystemClock.uptimeMillis()%1400)/1400f:0;paint.setStyle(Paint.Style.FILL);paint.setShader(null);
        if(code<0){paint.setColor(foreground);paint.setTextSize(28);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("—",32,38,paint);}
        else{
            if(code<=2){
                paint.setColor(day?0xffffd166:0xffc4dcff);
                if(day){canvas.drawCircle(27,24,12,paint);paint.setStrokeWidth(2.3f);int sun=canvas.save();canvas.rotate(active&&animation?(SystemClock.uptimeMillis()%20000)*.018f:0,27,24);for(int i=0;i<8;i++){canvas.drawLine(27,5,27,9,paint);canvas.rotate(45,27,24);}canvas.restoreToCount(sun);}
                else canvas.drawPath(moon,paint);
            }
            if(code>=2){paint.setColor(foreground);paint.setAlpha(225);int cloudSave=canvas.save();if(active&&animation)canvas.translate((float)Math.sin(phase*Math.PI*2)*1.4f,0);canvas.drawPath(cloud,paint);canvas.restoreToCount(cloudSave);paint.setAlpha(255);}
            if(code>=51&&code<=67||code>=80&&code<=82||code>=95){paint.setColor(0xff65bfff);paint.setStrokeWidth(2.6f);for(int i=0;i<3;i++){float y=42+((phase+i*.31f)%1)*15;canvas.drawLine(20+i*12,y,17+i*12,y+5,paint);}}
            if(code>=71&&code<=77||code==85||code==86){paint.setColor(0xffe8f5ff);for(int i=0;i<3;i++)canvas.drawCircle(19+i*12,45+((phase+i*.29f)%1)*13,2,paint);}
            if(code==45||code==48){paint.setColor(0xffb0c4d8);paint.setStrokeWidth(2);canvas.drawLine(12,48,51,48,paint);canvas.drawLine(18,54,57,54,paint);}
            if(code>=95){paint.setColor(0xffffdc67);canvas.drawPath(bolt,paint);}
        }
        canvas.restoreToCount(save);
        if(active&&animation&&code>=0&&getWindowVisibility()==VISIBLE&&isShown()){removeCallbacks(frame);postDelayed(frame,frameMs);}
    }
}
