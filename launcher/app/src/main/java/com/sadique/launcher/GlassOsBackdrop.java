package com.sadique.launcher;

import android.graphics.*;

/** HTML reference's gradient and two accent halos. Shaders are rebuilt only when inputs change. */
final class GlassOsBackdrop {
    private final Paint paint=new Paint(3);
    private Shader base,halo,ambient;
    private int width,height,accent,strength=-1;
    void draw(Canvas canvas,int w,int h,int color,int intensity) {
        if(w<=0||h<=0) return;
        if(w!=width||h!=height||color!=accent||intensity!=strength) {
            width=w; height=h; accent=color; strength=intensity;
            base=new LinearGradient(0,0,w,h,new int[]{0xff080d16,0xff121927,0xff080c15},null,Shader.TileMode.CLAMP);
            int a=(color&0xffffff)|((Math.round(72*intensity/100f))<<24);
            int b=(color&0xffffff)|((Math.round(102*intensity/100f))<<24);
            halo=new RadialGradient(w*.82f,h*.28f,w*.55f,a,color&0xffffff,Shader.TileMode.CLAMP);
            ambient=new RadialGradient(w*.45f,h*.62f,w*.40f,b,color&0xffffff,Shader.TileMode.CLAMP);
        }
        paint.setShader(base); canvas.drawRect(0,0,w,h,paint);
        paint.setShader(halo); canvas.drawRect(0,0,w,h,paint);
        paint.setShader(ambient); canvas.drawRect(0,0,w,h,paint); paint.setShader(null);
    }
}
