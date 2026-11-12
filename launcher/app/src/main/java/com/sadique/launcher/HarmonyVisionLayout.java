package com.sadique.launcher;

/** Deterministic, bounded 720p TV geometry; native weather widget fills right-hand zone. */
final class HarmonyVisionLayout {
    private HarmonyVisionLayout(){}
    static int clockDiameterDp(int screenHeightDp,int requested){
        int[] sizes={186,216,242};
        int chosen=Math.max(0,Math.min(2,requested));
        return Math.max(148,Math.min(sizes[chosen],screenHeightDp-295));
    }
    static int dockColumns(int availableWidthDp){
        return Math.max(4,Math.min(8,availableWidthDp/108));
    }
    static int dockPlateDp(int screenHeightDp){
        return screenHeightDp<450?58:68;
    }
    static int dockTileHeightDp(int screenHeightDp){
        return dockPlateDp(screenHeightDp)+25;
    }
    static int dockHeightDp(int screenHeightDp){
        return dockTileHeightDp(screenHeightDp)+34;
    }
    static boolean showDockPanel(){return false;}
    static boolean showDockReflections(){return false;}
    static boolean expandAppOnFocus(){return false;}
    static boolean showNativeWeatherWidget(){return true;}
    static boolean showSecondGreeting(){return false;}
    static boolean showWatchNextFooter(){return true;}
    /** Keep the app-library as a bounded, focusable grid instead of one long horizontal strip. */
    static int drawerColumns(int availableWidthDp){return Math.max(2,Math.min(5,availableWidthDp/152));}
    static int drawerHeightDp(int screenHeightDp){return Math.max(230,Math.min(420,screenHeightDp-84));}
    static int heroBottomGapDp(){return 12;}
}
