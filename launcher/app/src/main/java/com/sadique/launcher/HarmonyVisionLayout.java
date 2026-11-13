package com.sadique.launcher;

/** Bounded TV geometry across densities; native weather widget fills right-hand zone. */
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
        return screenHeightDp < 450 ? 58 : screenHeightDp < 660 ? 68 : 82;
    }
    static int dockTileHeightDp(int screenHeightDp){
        return dockPlateDp(screenHeightDp) + DOCK_CAPTION_HEIGHT_DP + 4;
    }
    static final int DOCK_CAPTION_HEIGHT_DP = 24;
    static final float DOCK_FOCUS_SCALE = 1.16f;
    static final int DOCK_FOCUS_LIFT_DP = 10;
    static final int DOCK_HEADROOM_DP = 22;

    static int dockCellWidthDp(int screenHeightDp) {
        return dockPlateDp(screenHeightDp) + 34;
    }

    static int dockHeightDp(int screenHeightDp) {
        return DOCK_HEADROOM_DP + dockTileHeightDp(screenHeightDp) + 18;
    }

    static int dockSidePadding(int viewportWidth, int cellWidth, int itemCount) {
        int visible = Math.min(itemCount, viewportWidth / cellWidth);
        return visible == 0 ? 0 : (viewportWidth - visible * cellWidth) / 2;
    }
    static boolean showNativeWeatherWidget(){return true;}
    static boolean showSecondGreeting(){return false;}
    /** Keep the app-library as a bounded, focusable grid instead of one long horizontal strip. */
    static int drawerColumns(int availableWidthDp){return Math.max(2,Math.min(5,availableWidthDp/152));}
    static int drawerHeightDp(int screenHeightDp){return Math.max(230,Math.min(420,screenHeightDp-84));}
}
