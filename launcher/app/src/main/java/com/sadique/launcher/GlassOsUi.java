package com.sadique.launcher;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;

final class GlassOsUi {
    private GlassOsUi() { }
    static GradientDrawable controls(Activity host){
        GradientDrawable d=panel(host,26);
        boolean transparent=PanelTransparency.enabled(ThemePreferences.get(host));
        d.setColors(new int[]{transparent?0xeb0a0e18:0xff0a0e18,transparent?0xf00c111e:0xff0c111e});
        d.setStroke(GlassSettings.dp(host,1),0x38ffffff);return d;
    }
    static GradientDrawable panel(Activity host,int radius) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xa80e1320,0xb00a101b});
        d.setCornerRadius(GlassSettings.dp(host,radius));d.setStroke(GlassSettings.dp(host,1),0x28ffffff);
        if(!PanelTransparency.enabled(ThemePreferences.get(host)))d.setAlpha(255);
        if(!PanelTransparency.enabled(ThemePreferences.get(host)))d.setColors(new int[]{0xff0e1320,0xff0a101b});
        return d;
    }
}
