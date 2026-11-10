package com.sadique.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Shaded sun/moon beside the reading, with no independent animation loop. */
final class WeatherOrbView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF destination=new RectF();
    private static volatile Bitmap moon;
    static void prepareMoon(){
        if(moon!=null)return;
        synchronized(WeatherOrbView.class){if(moon==null)moon=Bitmap.createBitmap(MoonTexture.pixels(128),128,128,Bitmap.Config.ARGB_8888);}
    }
    private Bitmap artwork;private int code=-1;private boolean day=true;
    WeatherOrbView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void condition(int value,boolean daytime){if(code!=value||day!=daytime){code=value;day=daytime;artwork=null;}invalidate();}
    private void render(){
        artwork=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(artwork);c.scale(2,2);
        if(code<0)return;
        paint.setAlpha(255);
        paint.setShader(new RadialGradient(32,29,30,day?0x55ffe3a1:0x225e82a8,Color.TRANSPARENT,Shader.TileMode.CLAMP));c.drawCircle(32,29,30,paint);paint.setShader(null);
        if(day){paint.setShader(new RadialGradient(27,24,23,0xfffff0c0,0xffffb438,Shader.TileMode.CLAMP));c.drawCircle(32,29,18,paint);paint.setShader(null);}
        else if(moon!=null){paint.setColor(Color.WHITE);paint.setFilterBitmap(true);c.drawBitmap(moon,null,new RectF(7,4,57,54),paint);}
        else {paint.setColor(0xffa2a9b0);c.drawCircle(32,29,21,paint);} // Worker prepares detail before normal weather binding.
        if(code>=2){
            paint.setAlpha(255);paint.setShader(new RadialGradient(32,45,26,new int[]{0xffb5c7d1,0xaa8da6b8,0x008da6b8},new float[]{0,.55f,1},Shader.TileMode.CLAMP));
            c.save();c.scale(1,.55f);c.drawCircle(32,78,26,paint);c.restore();paint.setShader(null);
        }
    }
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(artwork==null)render();float side=Math.min(getWidth(),getHeight());destination.set((getWidth()-side)/2,(getHeight()-side)/2,(getWidth()+side)/2,(getHeight()+side)/2);paint.setColor(Color.WHITE);paint.setFilterBitmap(true);c.drawBitmap(artwork,null,destination,paint);}
}
