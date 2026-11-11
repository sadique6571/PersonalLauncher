package com.sadique.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;

/** App-tinted reference card. App logos are separate, so resizing never stretches a bitmap plate. */
final class GlassAppCard {
    private GlassAppCard(){}
    static GradientDrawable surface(Context context,int accent,boolean focused,int radius){
        SharedPreferences prefs=ThemePreferences.get(context);
        if(ThemeStyle.harmony(prefs))return HarmonyVisionSurface.app(context,accent,focused,radius);
        int top=MaterialAppearance.mix(0xff132539,accent,.24f);
        int bottom=MaterialAppearance.mix(0xff040916,accent,.14f);
        int alpha=Math.min(250,180+Math.round(TvGlassStyle.intensity(prefs)*.65f));
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{(alpha<<24)|(top&0xffffff),(alpha<<24)|(bottom&0xffffff)});
        d.setCornerRadius(radius);
        int stroke=Math.max(1,Math.round(context.getResources().getDisplayMetrics().density*(focused?2.5f:1)));
        d.setStroke(stroke,focused?0xffffffff:TvGlassStyle.edge(prefs));return d;
    }
}
