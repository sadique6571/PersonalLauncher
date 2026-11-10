package com.sadique.launcher;

import android.graphics.*;

/** Reference's blue sky gradient and lower-right focused-app halo, without full-screen blur. */
final class GlassOsBackdrop {
    private final Paint paint=new Paint(3);
    private Shader base,left,right,focus;
    private int width,height,accent,strength=-1;
    void draw(Canvas canvas,int w,int h,int color,int intensity) {
        if(w<=0||h<=0)return;
        if(w!=width||h!=height||color!=accent||intensity!=strength){
            width=w;height=h;accent=color;strength=intensity;
            base=new LinearGradient(0,0,0,h,new int[]{0xff041329,0xff071b34,0xff030b18},new float[]{0,.48f,1},Shader.TileMode.CLAMP);
            left=new RadialGradient(w*.22f,h*.28f,w*.52f,0x6b145d85,0x00145d85,Shader.TileMode.CLAMP);
            right=new RadialGradient(w*.80f,h*.24f,w*.48f,0x3840d9ff,0x0040d9ff,Shader.TileMode.CLAMP);
            focus=new RadialGradient(w*.72f,h*.78f,w*.38f,(color&0xffffff)|(Math.round(122*intensity/100f)<<24),color&0xffffff,Shader.TileMode.CLAMP);
        }
        paint.setShader(base);canvas.drawRect(0,0,w,h,paint);
        paint.setShader(left);canvas.drawRect(0,0,w,h,paint);
        paint.setShader(right);canvas.drawRect(0,0,w,h,paint);
        paint.setShader(focus);canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
    }
}
