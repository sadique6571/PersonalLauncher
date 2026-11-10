package com.sadique.launcher;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;

final class GlassOsUi {
    private GlassOsUi() { }
    static GradientDrawable controls(Activity host){
        GradientDrawable d=panel(host,26);
        android.content.SharedPreferences prefs=ThemePreferences.get(host);
        int alpha=PanelTransparency.alpha(Math.min(250,Math.round(235*alpha(prefs)/168f)),prefs,false);
        d.setColors(new int[]{(alpha<<24)|0x0a0e18,(alpha<<24)|0x0c111e});return d;
    }
    static GradientDrawable panel(Activity host,int radius) {
        android.content.SharedPreferences prefs=ThemePreferences.get(host);
        int alpha=PanelTransparency.alpha(alpha(prefs),prefs,false);
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{(alpha<<24)|0x0e1320,(alpha<<24)|0x0a101b});
        d.setCornerRadius(GlassSettings.dp(host,radius));
        int edge=Math.round((TvGlassStyle.edge(prefs)>>>24)*.47f);
        d.setStroke(GlassSettings.dp(host,1),(edge<<24)|0xffffff);return d;
    }
    private static int alpha(android.content.SharedPreferences prefs){
        float finish=TvGlassStyle.finish(prefs)==1?.65f:TvGlassStyle.finish(prefs)==2?1.2f:1;
        return Math.min(245,Math.round(168*TvGlassStyle.intensity(prefs)/70f*finish));
    }
}
