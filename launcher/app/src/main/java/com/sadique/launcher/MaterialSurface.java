package com.sadique.launcher;

import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;

/** Copper contour matte and pearl porcelain; all decoration clips to the chosen card outline. */
final class MaterialSurface extends GlassSurface {
    private final boolean pearl;
    private final boolean focused;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip=new Path();
    private final Path[] contours={new Path(),new Path(),new Path(),new Path()};
    private final Paint tabInk=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tab=new RectF();
    private float cachedRadius=-1;
    private int cachedShape=-1;
    private final int decorationAlpha;
    private final RectF frame=new RectF();
    private final int accent;
    MaterialSurface(int radius,int stroke,boolean focused,SharedPreferences prefs,boolean drawer,int finish) {
        super(stroke,0); pearl=ThemeStyle.light(prefs); this.focused=focused; accent=ThemeStyle.focusAccent(prefs);
        int intensity=TvGlassStyle.intensity(prefs);
        decorationAlpha=Math.round((pearl?34:68)*intensity/100f);
        ink.setStyle(Paint.Style.STROKE); ink.setColor(accent);
        tabInk.setColor(accent);
        int alpha=PanelTransparency.alpha(MaterialAppearance.alpha(finish,intensity,drawer,focused),prefs,drawer);
        int top=ThemeStyle.materialColor(prefs,ThemeStyle.baseTop(prefs),focused);
        int bottom=ThemeStyle.materialColor(prefs,ThemeStyle.baseBottom(prefs),focused);
        setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        setColors(new int[]{(alpha<<24)|(top&0xffffff),(alpha<<24)|(bottom&0xffffff)});
        setCornerRadius(radius);
        int edge=TvGlassStyle.edge(prefs)>>>24;
        setStroke(stroke,focused?accent:((edge<<24)|(pearl?0x929b9a:0xba8e69)));
        backdropOpacity(GlassTransparency.backdropAlpha(finish,intensity));
    }
    private void geometry() {
        Rect bounds=getBounds(); float radius=getCornerRadius(); int shape=getShape();
        if(frame.left==bounds.left && frame.top==bounds.top && frame.right==bounds.right && frame.bottom==bounds.bottom
                && cachedRadius==radius && cachedShape==shape) return;
        frame.set(bounds); cachedRadius=radius; cachedShape=shape;
        clip.reset();
        if(shape==OVAL) clip.addOval(frame,Path.Direction.CW);
        else clip.addRoundRect(frame,radius,radius,Path.Direction.CW);
        ink.setStrokeWidth(Math.max(1,frame.height()/180f));
        if(!pearl) for(int i=0;i<4;i++) {
            float gap=frame.height()*.10f,x=frame.right-frame.width()*.34f+i*gap;
            Path contour=contours[i]; contour.reset(); contour.moveTo(x,frame.top);
            contour.cubicTo(x+frame.width()*.22f,frame.centerY(),x-frame.width()*.08f,frame.bottom,frame.right,frame.bottom-gap*i);
        }
        else {
            Path contour=contours[0]; contour.reset(); contour.moveTo(frame.left,frame.top+2);
            contour.cubicTo(frame.centerX(),frame.top+frame.height()*.13f,frame.centerX(),frame.top+frame.height()*.13f,frame.right,frame.top+2);
        }
        float size=Math.min(frame.width(),frame.height())*.12f;
        tab.set(frame.right-size*2.4f,frame.top,frame.right,frame.top+size);
    }
    @Override public void draw(Canvas canvas) {
        super.draw(canvas); geometry();
        int save=canvas.save(); canvas.clipPath(clip);
        ink.setAlpha(decorationAlpha*getAlpha()/255);
        for(int i=0;i<(pearl?1:4);i++) canvas.drawPath(contours[i],ink);
        if(focused) {
            tabInk.setAlpha(getAlpha()); float round=tab.height()/3;
            canvas.drawRoundRect(tab,round,round,tabInk);
        }
        canvas.restoreToCount(save);
    }
}
