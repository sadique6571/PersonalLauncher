package com.sadique.launcher;
import android.content.Context;
import android.graphics.*;
/** Bundled illustrative photographs, decoded once on the existing weather worker (never network/UI). */
final class WeatherImages {
    static volatile Bitmap sun,cloud,clouds,overcast,storm,fog;
    private static volatile boolean ready;
    static synchronized void prepare(Context context){
        if(ready)return;
        sun=disc(decode(context,R.drawable.weather_sun_sdo,128),59.5f);
        clouds=decode(context,R.drawable.weather_clouds_noaa,512);
        overcast=decode(context,R.drawable.weather_overcast_noaa,512);
        storm=decode(context,R.drawable.weather_storm_noaa,512);
        fog=decode(context,R.drawable.weather_fog_noaa,512);
        if(clouds!=null){
            Bitmap small=Bitmap.createScaledBitmap(clouds,160,107,true);
            cloud=Bitmap.createBitmap(160,107,Bitmap.Config.ARGB_8888);
            int[] pixels=new int[160*107];small.getPixels(pixels,0,160,0,0,160,107);
            // Blue-sky chroma removal preserves photographic cloud edges and surface shading.
            for(int y=0;y<107;y++)for(int x=0;x<160;x++){
                int i=y*160+x,p=pixels[i],red=Color.red(p),green=Color.green(p),blue=Color.blue(p);
                float neutral=1f-Math.min(1f,Math.max(0,(blue-red-9)/35f));
                float edge=Math.min(1f,Math.min(Math.min(x,159-x)/12f,Math.min(y,106-y)/10f));
                if(y>82)edge*=Math.max(0,(100-y)/18f);
                pixels[i]=(Math.round(255*neutral*Math.max(0,edge))<<24)|(p&0xffffff);
            }
            cloud.setPixels(pixels,0,160,0,0,160,107);
        }
        ready=true;
    }
    private static Bitmap decode(Context c,int id,int limit){
        BitmapFactory.Options o=new BitmapFactory.Options();o.inScaled=false;o.inJustDecodeBounds=true;
        BitmapFactory.decodeResource(c.getResources(),id,o);o.inSampleSize=1;
        while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>limit)o.inSampleSize*=2;
        o.inJustDecodeBounds=false;return BitmapFactory.decodeResource(c.getResources(),id,o);
    }
    private static Bitmap disc(Bitmap source,float radius){
        if(source==null)return null;Bitmap result=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        BitmapShader s=new BitmapShader(source,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);Matrix m=new Matrix();m.setScale(128f/source.getWidth(),128f/source.getHeight());s.setLocalMatrix(m);p.setShader(s);new Canvas(result).drawCircle(64,64,radius,p);return result;
    }
    static Bitmap scene(int kind){switch(kind){case WeatherVisualState.PARTLY:return clouds;case WeatherVisualState.CLOUD:case WeatherVisualState.SNOW:return overcast;case WeatherVisualState.FOG:return fog;case WeatherVisualState.RAIN:case WeatherVisualState.STORM:return storm;default:return null;}}
}
