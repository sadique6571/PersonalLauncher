package com.sadique.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;

/**
 * Huawei Vision-inspired home materials are intentionally separate from Glass OS.
 * Light-reflecting app plates, contrasting feature cards, and a restrained status rail
 * replace a generic glass drawer. No blur/bitmaps required.
 */
final class HarmonyVisionSurface {
    private HarmonyVisionSurface() {}
    private static int dp(Context context,float value){
        return Math.max(1,Math.round(context.getResources().getDisplayMetrics().density*value));
    }
    private static int material(Context ctx,int color,boolean app){
        int alpha=PanelTransparency.alpha((color>>>24)&255,ThemePreferences.get(ctx),app);
        return (alpha<<24)|(color&0xffffff);
    }
    static GradientDrawable feature(Context ctx,int index,boolean focused){
        int[][] palette={
                {0xe9285977,0xd70c2947}, // live weather
                {0xd8545a75,0xc51b244a}, // today / time
                {0xdb675c73,0xc62b2444}  // discover / scenery
        };
        int[] colors=palette[Math.floorMod(index,palette.length)];
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{material(ctx,colors[0],false),material(ctx,colors[1],false)});
        d.setCornerRadius(dp(ctx,24));
        d.setStroke(dp(ctx,focused?2:1),focused?0xfff7fbff:0x74d2e6ef);
        return d;
    }
    static GradientDrawable app(Context ctx,int accent,boolean focused,int radius){
        SharedPreferences prefs=ThemePreferences.get(ctx);
        float intensity=TvGlassStyle.intensity(prefs)/100f;
        int base=Math.min(251,Math.round(155+intensity*69));
        int top=MaterialAppearance.mix(0xffd5e8ee,accent,.09f);
        int bottom=MaterialAppearance.mix(0xff658fa4,accent,.11f);
        int fadeTop=focused?Math.min(253,base+28):base;
        int fadeBottom=focused?Math.min(247,base+5):Math.max(105,base-32);
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{material(ctx,(fadeTop<<24)|(top&0xffffff),true),
                        material(ctx,(fadeBottom<<24)|(bottom&0xffffff),true)});
        d.setCornerRadius(radius);
        d.setStroke(dp(ctx,focused?2.2f:1),focused?0xfffaffff:0x91e3f2f6);
        return d;
    }
    static GradientDrawable control(Context ctx,boolean focused){
        int[] colors=focused?new int[]{0xe95c819c,0xe5264767}:new int[]{0xc82a4b64,0xcb132b47};
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{material(ctx,colors[0],false),material(ctx,colors[1],false)});
        d.setCornerRadius(dp(ctx,25));
        d.setStroke(dp(ctx,focused?2:1),focused?0xfffaffff:0x779dbdd0);
        return d;
    }
    static GradientDrawable status(Context ctx){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{material(ctx,0x9b29485f,false),material(ctx,0x8b0b2036,false)});
        d.setCornerRadius(dp(ctx,999));
        d.setStroke(dp(ctx,1),0x65d5e9f5);
        return d;
    }
    static GradientDrawable dock(Context ctx) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{material(ctx, 0xcf203952, true), material(ctx, 0xef0b192a, true)});
        d.setCornerRadius(dp(ctx, 28));
        d.setStroke(dp(ctx, 1), 0x7aa7c5db);
        return d;
    }
    static GradientDrawable sheet(Context ctx){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{material(ctx,0xf825415d,false),material(ctx,0xf90d1c34,false)});
        d.setCornerRadius(dp(ctx,30));
        d.setStroke(dp(ctx,1),0x99b9d5e6);
        return d;
    }
}
