package com.sadique.launcher;

import android.graphics.*;

/** A raised capsule edge from cached gradients, with no shadow bitmap or blur pass. */
final class CapsuleRelief {
    private final Paint sheen=new Paint(Paint.ANTI_ALIAS_FLAG),edge=new Paint(Paint.ANTI_ALIAS_FLAG),inner=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF body=new RectF(),lip=new RectF(),inset=new RectF();
    private final float stroke;
    private float radius;
    CapsuleRelief(float density) {
        stroke=Math.max(1,density);
        edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(stroke);
        inner.setStyle(Paint.Style.STROKE); inner.setStrokeWidth(stroke);
    }
    void size(int width,int height) {
        body.set(0,0,width,height); radius=Math.min(width,height)/2f;
        lip.set(body); lip.inset(stroke/2,stroke/2);
        inset.set(body); inset.inset(stroke*2,stroke*2);
        sheen.setShader(new LinearGradient(0,0,0,Math.max(1,height),
                new int[]{0x26ffffff,0x00ffffff,0x19000000},new float[]{0,.4f,1},Shader.TileMode.CLAMP));
        edge.setShader(new LinearGradient(0,0,Math.max(1,width),Math.max(1,height),
                new int[]{0xb0ffffff,0x18ffffff,0x70000000},new float[]{0,.5f,1},Shader.TileMode.CLAMP));
        inner.setShader(new LinearGradient(0,0,Math.max(1,width),Math.max(1,height),
                0x33000000,0x66ffffff,Shader.TileMode.CLAMP));
    }
    void draw(Canvas canvas) {
        canvas.drawRoundRect(body,radius,radius,sheen);
        float outside=HomeGeometry.insetRadius(radius,stroke/2);
        canvas.drawRoundRect(lip,outside,outside,edge);
        float inside=HomeGeometry.insetRadius(radius,stroke*2);
        canvas.drawRoundRect(inset,inside,inside,inner);
    }
}
