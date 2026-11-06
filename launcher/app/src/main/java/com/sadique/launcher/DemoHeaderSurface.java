package com.sadique.launcher;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
/** Pearl's shaped porcelain bar, Cobalt's profile grooves, and open Dune/Sage headers. */
final class DemoHeaderSurface extends GlassSurface {
    private final int style,accent,alpha; private final Paint paint=new Paint(3),edge=new Paint(3);
    private final Path shape=new Path(); private final GradientDrawable frost; private Shader pearlFill; private int width=-1,height=-1;
    DemoHeaderSurface(SharedPreferences prefs,boolean frosted,int radius,int stroke) {
        super(stroke,0);
        style=ThemeStyle.id(prefs); accent=ThemeStyle.focusAccent(prefs);
        alpha=PanelTransparency.alpha(MaterialAppearance.alpha(TvGlassStyle.finish(prefs),TvGlassStyle.intensity(prefs),false,false),prefs,false);
        frost=frosted?new MaterialSurface(radius,stroke,false,prefs,false,2):null;
        setColor(Color.TRANSPARENT); edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(1);
    }
    @Override void backdrop(Bitmap image,int w,int h,int x,int y) {
        if(frost instanceof GlassSurface) ((GlassSurface)frost).backdrop(image,w,h,x,y);
    }
    @Override public void draw(Canvas canvas) {
        Rect b=getBounds(); int w=b.width(),h=b.height(); int save=canvas.save(); canvas.translate(b.left,b.top);
        if(frost!=null) { frost.setBounds(0,0,w,h); frost.draw(canvas); }
        if(style==ThemeStyle.PEARL) {
            if(width!=w||height!=h) {
                width=w; height=h; shape.reset(); shape.moveTo(0,0); shape.lineTo(w,0); shape.lineTo(w,h*.72f);
                shape.cubicTo(w*.87f,h*1.15f,w*.77f,h,w*.70f,h*.65f);
                shape.cubicTo(w*.64f,h*.13f,w*.58f,h*.26f,w*.5f,h*.26f);
                shape.cubicTo(w*.42f,h*.26f,w*.36f,h*.13f,w*.30f,h*.65f);
                shape.cubicTo(w*.23f,h,w*.13f,h*1.15f,0,h*.72f); shape.close();
                pearlFill=new LinearGradient(0,0,0,Math.max(1,h),0xfffcf1e8,0xffeae1db,Shader.TileMode.CLAMP);
            }
            paint.setShader(pearlFill); paint.setAlpha(alpha*getAlpha()/255); canvas.drawPath(shape,paint);
            edge.setColor(0x99ffffff); edge.setAlpha(getAlpha()); canvas.drawPath(shape,edge);
        } else if(style==ThemeStyle.DUNE) {
            edge.setColor(accent); edge.setAlpha(getAlpha()/4);
            for(int i=0;i<4;i++) canvas.drawArc(-w*.05f,-h*(1.9f+i*.15f),w*.38f,h*(1.1f+i*.15f),20,120,false,edge);
        } else if(style==ThemeStyle.COBALT) {
            int stripes=canvas.save(); canvas.clipRect(0,0,w*.30f,h);
            paint.setColor(0x44182d73); paint.setAlpha(alpha*getAlpha()/255); canvas.drawRect(0,0,w*.30f,h,paint);
            edge.setColor(0x773b7eff); edge.setAlpha(getAlpha());
            for(int x=-h;x<w*.30f;x+=Math.max(4,h/12)) canvas.drawLine(x,0,x+h*.65f,h,edge);
            canvas.restoreToCount(stripes);
        } else if(style==ThemeStyle.SAGE) {
            paint.setShader(null); paint.setColor(0xffc5d4ac); paint.setAlpha(getAlpha());
            shape.reset(); shape.moveTo(w-h*.25f,0); shape.lineTo(w,h*.25f); shape.lineTo(w,0); shape.close(); canvas.drawPath(shape,paint);
        }
        if(w>h*8) {
            paint.setShader(null); paint.setAlpha(getAlpha()); paint.setColor(style==ThemeStyle.DUNE?accent:style==ThemeStyle.COBALT?0xffd5e2ff:0xff183126);
            paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); paint.setTextSize(h*.17f); paint.setTextAlign(Paint.Align.CENTER);
            String name=style==ThemeStyle.DUNE?"DUNE LOOM":style==ThemeStyle.PEARL?"PEARL CURRENT":style==ThemeStyle.COBALT?"COBALT RAIL":"SAGE FOLD";
            canvas.drawText(name,w/2f,h*.2f,paint);
        }
        canvas.restoreToCount(save);
    }
}
