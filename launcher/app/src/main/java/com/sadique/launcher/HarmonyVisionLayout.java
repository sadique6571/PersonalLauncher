package com.sadique.launcher;

/** Fixed TV zones and geometry. No second app tray behind the reflective first app row. */
final class HarmonyVisionLayout {
    private HarmonyVisionLayout(){}
    static int clockDiameterDp(int screenHeightDp,int requested){
        int[] sizes={208,242,268};
        int saved=Math.max(0,Math.min(2,requested));
        return Math.max(156,Math.min(sizes[saved],screenHeightDp-260));
    }
    static boolean showDockPanel(){return false;}
    static boolean showDockReflections(){return true;}
    static boolean showSecondGreeting(){return false;}
    static boolean showWatchNextFooter(){return false;}
}
