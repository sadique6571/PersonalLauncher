package com.sadique.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Shaded sun/moon beside the reading, with no independent animation loop. */
final class WeatherOrbView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF destination=new RectF();
    private static volatile Bitmap moon;
    // NASA SVS, LRO/LOLA/LROC surface visualization; fixed weather artwork, not today's phase.
    // Source: https://svs.gsfc.nasa.gov/5048/ (phase_full.1571_print.jpg), credit shown in forecast.
    static void prepareMoon(Context context){
        if(moon!=null)return;
        synchronized(WeatherOrbView.class){
            if(moon!=null)return;
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=8;options.inScaled=false;
            Bitmap source=BitmapFactory.decodeResource(context.getResources(),R.drawable.weather_moon_nasa,options);
            if(source==null)return;
            Bitmap result=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(result);
            Paint photo=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);BitmapShader shader=new BitmapShader(source,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            Matrix transform=new Matrix();transform.setScale(128f/source.getWidth(),128f/source.getHeight());shader.setLocalMatrix(transform);photo.setShader(shader);
            c.drawCircle(64,64,54.5f,photo);moon=result;
        }
    }
    private Bitmap artwork;private int code=-1;private boolean day=true;
    WeatherOrbView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void condition(int value,boolean daytime){if(code!=value||day!=daytime){code=value;day=daytime;artwork=null;}invalidate();}
    private void render(){
        artwork=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(artwork);c.scale(2,2);
        if(code<0)return;
        paint.setAlpha(255);paint.setColor(Color.WHITE);paint.setFilterBitmap(true);
        int kind=WeatherVisualState.kind(code);
        if(kind==WeatherVisualState.UNKNOWN)return;
        if(kind==WeatherVisualState.CLEAR||kind==WeatherVisualState.PARTLY){
            Bitmap disc=day?WeatherImages.sun:moon;
            if(disc!=null)c.drawBitmap(disc,null,new RectF(3,0,61,58),paint);
        }
        if(kind!=WeatherVisualState.CLEAR&&WeatherImages.cloud!=null){
            paint.setColorFilter(new PorterDuffColorFilter(kind==WeatherVisualState.STORM||kind==WeatherVisualState.RAIN?0xff82909b:0xffe3e8eb,PorterDuff.Mode.MULTIPLY));
            c.drawBitmap(WeatherImages.cloud,null,new RectF(0,kind==WeatherVisualState.PARTLY?24:12,64,63),paint);paint.setColorFilter(null);
            if(kind==WeatherVisualState.RAIN||kind==WeatherVisualState.STORM){
                paint.setColor(0xffa9c4d3);paint.setStrokeWidth(.8f);for(int i=0;i<5;i++)c.drawLine(17+i*7,52,15+i*7,59,paint);
            }else if(kind==WeatherVisualState.SNOW){paint.setColor(0xfff0f5f7);for(int i=0;i<4;i++)c.drawCircle(19+i*8,56,1.2f,paint);}
            else if(kind==WeatherVisualState.FOG){paint.setColor(0xffb5c1c9);paint.setStrokeWidth(1.2f);for(int i=0;i<3;i++)c.drawLine(14,48+i*4,52-i*3,48+i*4,paint);}
        }
    }
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(artwork==null)render();float side=Math.min(getWidth(),getHeight());destination.set((getWidth()-side)/2,(getHeight()-side)/2,(getWidth()+side)/2,(getHeight()+side)/2);paint.setColor(Color.WHITE);paint.setFilterBitmap(true);c.drawBitmap(artwork,null,destination,paint);}
}
