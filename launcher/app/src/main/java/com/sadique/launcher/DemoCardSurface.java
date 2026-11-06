package com.sadique.launcher;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;

/** Four reference card silhouettes and decorations; all paints/paths are size-cached. */
final class DemoCardSurface extends GradientDrawable {
    private final int style,accent,edge; private final boolean focused; private final float unit,footer;
    private final Paint fill=new Paint(3),line=new Paint(3),detail=new Paint(3),bevel=new Paint(3);
    private final Path body=new Path(),inside=new Path(),fold=new Path();
    private float width,height,lastRadius=-1; private int lastLeft=-1,lastTop=-1;
    private final int top,bottom,opacity;
    DemoCardSurface(int radius,int unit,boolean focused,SharedPreferences prefs) {
        this.unit=unit; this.focused=focused; style=ThemeStyle.id(prefs); accent=ThemeStyle.focusAccent(prefs);
        footer=DemoGeometry.footer(style,unit); edge=TvGlassStyle.edge(prefs)>>>24;
        top=ThemeStyle.materialColor(prefs,ThemeStyle.baseTop(prefs),false);
        bottom=ThemeStyle.materialColor(prefs,ThemeStyle.baseBottom(prefs),false);
        float s=TvGlassStyle.intensity(prefs)/100f;
        int max=TvGlassStyle.finish(prefs)==1?210:TvGlassStyle.finish(prefs)==2?255:245;
        opacity=Math.round(max*(2*s-s*s));
        setColor(Color.TRANSPARENT); setCornerRadius(radius);
        line.setStyle(Paint.Style.STROKE); bevel.setStyle(Paint.Style.STROKE); bevel.setStrokeWidth(unit);
        line.setStrokeWidth(unit*(focused?new int[]{2,3,5}[Math.max(0,Math.min(2,prefs.getInt("focus_border",1)))]:1));
    }
    private void geometry() {
        Rect b=getBounds(); float r=getCornerRadius(),h=Math.max(1,b.height()-footer);
        if(width==b.width()&&height==h&&lastRadius==r&&lastLeft==b.left&&lastTop==b.top) return;
        width=b.width(); height=h; lastRadius=r; lastLeft=b.left; lastTop=b.top;
        DemoPaths.set(body,style,width,height,r);
        DemoPaths.set(inside,style,Math.max(1,width-unit*3),Math.max(1,height-unit*3),Math.max(1,r-unit));
        inside.offset(unit*1.5f,unit*1.5f);
        fill.setShader(new LinearGradient(0,0,0,height,top,bottom,Shader.TileMode.CLAMP));
        bevel.setShader(new LinearGradient(0,0,0,height,0x33ffffff,0x66000000,Shader.TileMode.CLAMP));
        fold.reset(); float f=Math.min(width*.15f,height*.22f);
        fold.moveTo(width-f,0); fold.lineTo(width-f,f); fold.lineTo(width,f); fold.close();
    }
    @Override public void draw(Canvas canvas) {
        geometry(); int save=canvas.save(); canvas.translate(lastLeft,lastTop);
        if(style==ThemeStyle.SAGE) {
            detail.setStyle(Paint.Style.FILL); detail.setColor(0xff657c50); detail.setAlpha(getAlpha()*opacity/255);
            canvas.save(); canvas.translate(0,footer); canvas.drawPath(body,detail); canvas.restore();
        }
        fill.setAlpha(opacity*getAlpha()/255); canvas.drawPath(body,fill);
        int clip=canvas.save(); canvas.clipPath(body);
        detail.setStyle(Paint.Style.STROKE); detail.setStrokeWidth(unit*.65f);
        detail.setColor(style==ThemeStyle.COBALT?0xff397aff:accent); detail.setAlpha(Math.round(getAlpha()*(style==ThemeStyle.DUNE?.33f:.45f)));
        if(style==ThemeStyle.DUNE) for(int i=0;i<4;i++) {
            // Etched arcs hug the right edge, rather than covering the app logo.
            canvas.drawArc(width*.65f+i*unit*5,-height*.5f,width*1.22f+i*unit*5,height*.85f,70,120,false,detail);
        }
        if(style==ThemeStyle.COBALT) for(int i=0;i<2;i++) canvas.drawLine(unit*10,height-unit*(8+i*4),width-unit*10,height-unit*(8+i*4),detail);
        if(style==ThemeStyle.PEARL && focused) {
            detail.setStyle(Paint.Style.FILL); detail.setColor(accent); detail.setAlpha(getAlpha());
            canvas.drawRoundRect(width-height*.22f,0,width,height*.18f,height*.08f,height*.08f,detail);
        }
        if(style==ThemeStyle.SAGE) {
            detail.setStyle(Paint.Style.FILL); detail.setColor(focused?0xfff1b283:0xffeff3d9); detail.setAlpha(getAlpha()*opacity/255);
            canvas.drawPath(fold,detail);
        }
        canvas.restoreToCount(clip);
        bevel.setAlpha(opacity*getAlpha()/255); canvas.drawPath(inside,bevel);
        line.setColor(focused?accent:style==ThemeStyle.COBALT?0xff397aff:style==ThemeStyle.PEARL?0xfffcf4eb:accent);
        line.setAlpha(focused?getAlpha():getAlpha()*edge/255); canvas.drawPath(body,line);
        if(style==ThemeStyle.PEARL) { detail.setStyle(Paint.Style.STROKE); detail.setColor(focused?accent:0xff8e9694); detail.setAlpha(getAlpha()*edge/255); canvas.drawPath(inside,detail); }
        if(focused&&(style==ThemeStyle.DUNE||style==ThemeStyle.COBALT)) {
            detail.setStyle(Paint.Style.FILL); detail.setColor(accent); detail.setAlpha(getAlpha());
            canvas.drawRoundRect(width*.4f,height+unit*3,width*.6f,height+unit*6,unit*2,unit*2,detail);
        }
        canvas.restoreToCount(save);
    }
}
