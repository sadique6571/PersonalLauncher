package com.sadique.launcher;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;

final class GlassOsUi {
    private GlassOsUi() { }
    static GradientDrawable panel(Activity host,int radius) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xa80e1320,0xb00a101b});
        d.setCornerRadius(GlassSettings.dp(host,radius));d.setStroke(GlassSettings.dp(host,1),0x28ffffff);
        if(!PanelTransparency.enabled(ThemePreferences.get(host)))d.setAlpha(255);
        if(!PanelTransparency.enabled(ThemePreferences.get(host)))d.setColors(new int[]{0xff0e1320,0xff0a101b});
        return d;
    }
}
