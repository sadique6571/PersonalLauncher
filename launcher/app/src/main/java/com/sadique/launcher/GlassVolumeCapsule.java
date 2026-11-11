package com.sadique.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Presentation only. VolumeSlider owns the shared observer, window and auto-hide deadline. */
final class GlassVolumeCapsule extends LinearLayout {
    private final TextView percent;
    private final Track track;
    private final int foreground;
    private ValueAnimator animator;
    private float shown;
    private int previousPercent=-1;
    GlassVolumeCapsule(Context context) {
        super(context);SharedPreferences prefs=ThemePreferences.get(context);
        foreground=ThemeStyle.foreground(prefs);setId(R.id.volumeCapsule);
        setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(dp(10),dp(12),dp(10),dp(12));
        GradientDrawable shell;
        if(ThemeStyle.id(prefs)==ThemeStyle.GLASS) {
            float finish=TvGlassStyle.finish(prefs)==1?.65f:TvGlassStyle.finish(prefs)==2?1.2f:1;
            int alpha=PanelTransparency.alpha(Math.min(245,Math.round(194*TvGlassStyle.intensity(prefs)/70f*finish)),prefs,false);
            shell=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{(alpha<<24)|0x132539,(Math.min(255,alpha+26)<<24)|0x09182a});
            shell.setStroke(dp(1),TvGlassStyle.edge(prefs));
        }else if(ThemeStyle.harmony(prefs)){
            shell=HarmonyVisionSurface.status(context);
        }else if(ThemeStyle.id(prefs)==ThemeStyle.CLASSIC){
            shell=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{TvGlassStyle.tinted(0xff132539,prefs),TvGlassStyle.tinted(0xff09182a,prefs)});
            shell.setStroke(dp(1),0x55415a72);shell.setAlpha(PanelTransparency.alpha(ClassicGlassAlpha.card(TvGlassStyle.intensity(prefs),false),prefs,false));
        }else shell=TvGlassStyle.surface(dp(999),dp(1),false,prefs);
        shell.setCornerRadius(dp(999));setBackground(shell);
        percent=label("—",12,foreground);percent.setId(R.id.volPercent);addView(percent,new LayoutParams(-1,dp(24)));
        track=new Track(context,prefs);track.setId(R.id.volTrack);addView(track,new LayoutParams(dp(38),0,1));
        TextView caption=label(ThemeStyle.harmony(prefs)?"SOUND":"VOL",ThemeStyle.harmony(prefs)?9:10,ThemeStyle.muted(prefs));addView(caption,new LayoutParams(-1,dp(24)));
        setFocusable(false);
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size,int color){
        TextView text=new TextView(getContext());text.setText(value);text.setTextSize(size);text.setTextColor(color);text.setGravity(Gravity.CENTER);
        text.setTypeface(Typeface.create(ThemeStyle.fontFamily(ThemePreferences.get(getContext())),Typeface.BOLD));return text;
    }
    void state(float fraction,int value,boolean muted,int symbol,int accent,boolean animate) {
        fraction=Math.max(0,Math.min(1,fraction));
        if(previousPercent!=value){percent.setText(muted||value==0?"MUTE":value+"%");setContentDescription(getContext().getString(R.string.h_sound)+" "+percent.getText());}
        track.symbol(symbol);track.accent(accent);
        if(animator!=null)animator.cancel();
        if(animate&&previousPercent>=0&&isShown()&&shown!=fraction){
            animator=ValueAnimator.ofFloat(shown,fraction);animator.setDuration(160);
            animator.addUpdateListener(a->{shown=(float)a.getAnimatedValue();track.level(shown);});animator.start();
        }else{shown=fraction;track.level(shown);}
        if(animate&&previousPercent!=value){animate().cancel();setScaleX(1.02f);setScaleY(1.02f);animate().scaleX(1).scaleY(1).setDuration(180).start();}
        previousPercent=value;
    }
    void stopAnimation(){if(animator!=null){animator.cancel();animator=null;}animate().cancel();setScaleX(1);setScaleY(1);}
    private final class Track extends View {
        private final Paint paint=new Paint(3);
        private final Path outline=new Path();
        private final RectF bounds=new RectF();
        private final Matrix fillMatrix=new Matrix();
        private final boolean light;
        private float fraction;
        private int accent,resource;
        private Shader gradient;
        private Drawable symbol;
        Track(Context context,SharedPreferences prefs){super(context);light=ThemeStyle.light(prefs);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void level(float value){fraction=value;invalidate();}
        void accent(int value){if(gradient==null||accent!=value){accent=value;gradient=new LinearGradient(0,0,0,1,new int[]{0xffffffff,0xfff4f6fa,(ThemeStyle.id(ThemePreferences.get(getContext()))==ThemeStyle.GLASS||ThemeStyle.harmony(ThemePreferences.get(getContext())))?0xffdceeff:(value|0xff000000)},new float[]{0,.45f,1},Shader.TileMode.CLAMP);}invalidate();}
        void symbol(int value){if(value!=resource){resource=value;symbol=TvSymbols.icon(getContext(),value);place();}invalidate();}
        private void place(){if(symbol!=null){int side=dp(22),left=(getWidth()-side)/2,bottom=getHeight()-dp(14);symbol.setBounds(left,bottom-side,left+side,bottom);}}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);bounds.set(dp(1),dp(1),w-dp(1),h-dp(1));outline.reset();outline.addRoundRect(bounds,w/2f,w/2f,Path.Direction.CW);place();}
        @Override protected void onDraw(Canvas c){
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(light?0x18000000:0x18ffffff);c.drawRoundRect(bounds,getWidth()/2f,getWidth()/2f,paint);
            float fillHeight=bounds.height()*fraction,top=bounds.bottom-fillHeight;
            if(fillHeight>0&&gradient!=null){int save=c.save();c.clipPath(outline);fillMatrix.setScale(1,fillHeight);fillMatrix.postTranslate(0,top);gradient.setLocalMatrix(fillMatrix);paint.setAlpha(255);paint.setShader(gradient);c.drawRect(bounds.left,top,bounds.right,bounds.bottom,paint);paint.setShader(null);c.restoreToCount(save);}
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(light?0x24000000:0x29ffffff);c.drawRoundRect(bounds,getWidth()/2f,getWidth()/2f,paint);paint.setStyle(Paint.Style.FILL);
            if(symbol!=null){int save=c.save();c.clipRect(0,0,getWidth(),top);symbol.setColorFilter(foreground,PorterDuff.Mode.SRC_IN);symbol.draw(c);c.restoreToCount(save);
                save=c.save();c.clipRect(0,top,getWidth(),getHeight());symbol.setColorFilter(Color.luminance(accent)>.179?0xff142239:0xfff8fafc,PorterDuff.Mode.SRC_IN);symbol.draw(c);c.restoreToCount(save);}
        }
    }
}
