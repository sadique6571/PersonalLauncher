package com.sadique.launcher;

import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;

/** Copper contour matte and pearl porcelain; all decoration clips to the chosen card outline. */
final class MaterialSurface extends GlassSurface {
    private final boolean pearl;
    private final boolean focused;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path contour=new Path(), clip=new Path();
    private final RectF frame=new RectF();
    private final int accent;
    MaterialSurface(int radius,int stroke,boolean focused,SharedPreferences prefs,boolean drawer) {
        super(stroke,0); pearl=ThemeStyle.pearl(prefs); this.focused=focused; accent=ThemeStyle.accent(prefs);
        float strength=TvGlassStyle.intensity(prefs)/100f;
        int alpha=drawer?Math.round((pearl?180:155)*strength):Math.round((pearl?190:175)+55*strength);
        alpha=PanelTransparency.alpha(alpha,prefs,drawer);
        int top=pearl?0xfff9f1e8:0xff26363a, bottom=pearl?0xffe9e4dc:0xff111e24;
        setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        setColors(new int[]{(alpha<<24)|(top&0xffffff),(alpha<<24)|(bottom&0xffffff)});
        setCornerRadius(radius);
        int edge=TvGlassStyle.edge(prefs)>>>24;
        setStroke(stroke,focused?accent:((edge<<24)|(pearl?0x929b9a:0xba8e69)));
        backdropOpacity(Math.round((pearl?80:100)*strength));
    }
    @Override public void draw(Canvas canvas) {
        super.draw(canvas);
        frame.set(getBounds()); float radius=getCornerRadius();
        clip.reset();
        if(getShape()==OVAL) clip.addOval(frame,Path.Direction.CW);
        else clip.addRoundRect(frame,radius,radius,Path.Direction.CW);
        int save=canvas.save(); canvas.clipPath(clip);
        ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(Math.max(1,frame.height()/180f));
        ink.setColor((pearl?0x22929b9a:0x44c28c5f)); ink.setAlpha(Math.round((pearl?34:68)*getAlpha()/255f));
        if(!pearl) for(int i=0;i<4;i++) {
            float gap=frame.height()*.10f, x=frame.right-frame.width()*.34f+i*gap;
            contour.reset(); contour.moveTo(x,frame.top);
            contour.cubicTo(x+frame.width()*.22f,frame.centerY(),x-frame.width()*.08f,frame.bottom,frame.right,frame.bottom-gap*i);
            canvas.drawPath(contour,ink);
        }
        else {
            contour.reset(); contour.moveTo(frame.left,frame.top+2);
            contour.cubicTo(frame.centerX(),frame.top+frame.height()*.13f,frame.centerX(),frame.top+frame.height()*.13f,frame.right,frame.top+2);
            canvas.drawPath(contour,ink);
        }
        if(focused) {
            ink.setStyle(Paint.Style.FILL); ink.setColor(accent); ink.setAlpha(getAlpha());
            float tab=Math.min(frame.width(),frame.height())*.12f;
            canvas.drawRoundRect(frame.right-tab*2.4f,frame.top,frame.right,frame.top+tab,tab/3,tab/3,ink);
        }
        canvas.restoreToCount(save);
    }
}
