package com.sadique.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;

/** App-tinted reference card. App logos are separate, so resizing never stretches a bitmap plate. */
final class GlassAppCard {
    private GlassAppCard(){}
    static GradientDrawable surface(Context context,int accent,boolean focused,int radius){
        SharedPreferences prefs=ThemePreferences.get(context);
        boolean harmony=ThemeStyle.harmony(prefs);
        int top=harmony?MaterialAppearance.mix(0xff365775,accent,.08f):MaterialAppearance.mix(0xff132539,accent,.24f);
        int bottom=harmony?MaterialAppearance.mix(0xff152c47,accent,.06f):MaterialAppearance.mix(0xff040916,accent,.14f);
        int alpha=Math.min(250,(harmony?132:180)+Math.round(TvGlassStyle.intensity(prefs)*(harmony?.72f:.65f)));
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{(alpha<<24)|(top&0xffffff),(alpha<<24)|(bottom&0xffffff)});
        d.setCornerRadius(radius);
        int stroke=Math.max(1,Math.round(context.getResources().getDisplayMetrics().density*(focused?2.5f:1)));
        d.setStroke(stroke,focused?0xffe7f7ff:harmony?0x779ac6e4:TvGlassStyle.edge(prefs));return d;
    }
}
