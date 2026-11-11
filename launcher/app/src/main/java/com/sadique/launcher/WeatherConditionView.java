package com.sadique.launcher;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Photographic atmosphere with bounded, low-rate precipitation; no full-screen blur. */
final class WeatherConditionView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds=new RectF(0,0,320,220);
    private final Runnable frame=this::invalidate;
    private final int frameMs;
    private Bitmap backdrop;
    private int code=-1;private boolean day=true,active,animation=true;
    WeatherConditionView(Context context){super(context);frameMs=MemoryProfile.effectiveTier(context,ThemePreferences.get(context))==MemoryProfile.TIER_LIGHT?125:66;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void condition(int next,boolean daytime){if(code!=next||day!=daytime){code=next;day=daytime;backdrop=null;}invalidate();}
    void active(boolean value,boolean animation){active=value;this.animation=animation;removeCallbacks(frame);invalidate();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(frame);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE)removeCallbacks(frame);else invalidate();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);backdrop=null;}
    private void makeBackdrop(){
        paint.setAlpha(255);
        int width=Math.max(1,Math.min(512,getWidth())),height=Math.max(1,Math.round(width*220f/320));
        backdrop=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(backdrop);c.scale(width/320f,height/220f);
        int top=code<0?0xff152131:!day?0xff071529:code>=3?0xff294657:0xff206491;
        int bottom=code<0?0xff07111f:!day?0xff14283a:code>=3?0xff50646b:0xff8eafb7;
        paint.setShader(new LinearGradient(0,0,0,220,top,bottom,Shader.TileMode.CLAMP));c.drawRect(bounds,paint);paint.setShader(null);
        Bitmap photo=WeatherImages.scene(WeatherVisualState.kind(code));
        if(photo!=null){
            float scale=Math.max(320f/photo.getWidth(),220f/photo.getHeight());
            float w=photo.getWidth()*scale,h=photo.getHeight()*scale;
            if(!day)paint.setColorFilter(new PorterDuffColorFilter(0xff53647c,PorterDuff.Mode.MULTIPLY));
            c.drawBitmap(photo,null,new RectF((320-w)/2,(220-h)/2,(320+w)/2,(220+h)/2),paint);paint.setColorFilter(null);
        }
        paint.setShader(new LinearGradient(0,0,0,220,new int[]{0x18030b17,0x55030b17,0xee030b17},new float[]{0,.48f,1},Shader.TileMode.CLAMP));c.drawRect(bounds,paint);paint.setShader(null);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(getWidth()<=0||getHeight()<=0)return;if(backdrop==null)makeBackdrop();
        int save=c.save();c.scale(getWidth()/320f,getHeight()/220f);paint.setAlpha(255);c.drawBitmap(backdrop,null,bounds,paint);
        float seconds=active&&animation?(SystemClock.uptimeMillis()%60000)/1000f:0;
        int kind=WeatherVisualState.kind(code);
        int drops=WeatherVisualState.drops(code);
        // Depth-dependent speed, opacity and length: fine precipitation rather than blue stripes.
        if(drops>0){
            for(int i=0;i<drops;i++){
                float depth=.35f+(i%7)/10f,speed=70+depth*110;
                float x=(i*73%350)-10,y=(i*47+seconds*speed)%248-20,len=4+depth*12;
                paint.setColor(Color.WHITE);paint.setAlpha((int)(24+depth*54));paint.setStrokeWidth(.35f+depth*.6f);
                c.drawLine(x,y,x-1-depth*2,y+len,paint);
            }
            paint.setAlpha(255);
        }
        if(kind==WeatherVisualState.SNOW){
            for(int i=0;i<24;i++){
                float depth=.3f+(i%5)*.15f,x=(i*59)%320+(float)Math.sin(seconds*.35f+i)*9,y=(i*29+seconds*(8+depth*12))%230-10;
                paint.setColor(Color.WHITE);paint.setAlpha((int)(70+depth*100));c.drawCircle(x,y,.4f+depth,paint);
            }paint.setAlpha(255);
        }
        if(kind==WeatherVisualState.STORM){
            float cycle=seconds%19f;
            if(cycle>12&&cycle<12.16f){paint.setColor(0x20dce7f3);c.drawRect(bounds,paint);}
        }
        c.restoreToCount(save);
        if(active&&animation&&(drops>0||kind==WeatherVisualState.SNOW||kind==WeatherVisualState.STORM)&&getWindowVisibility()==VISIBLE&&isShown()){removeCallbacks(frame);postDelayed(frame,frameMs);}
    }
}
