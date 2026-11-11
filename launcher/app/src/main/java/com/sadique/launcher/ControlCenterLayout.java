package com.sadique.launcher;

/** Small, deterministic layout policy. Indices are Wi-Fi, Bluetooth, inputs, display, sound, screensaver. */
final class ControlCenterLayout {
    private ControlCenterLayout() { }
    static int[][] rows(int style) {
        switch (style) {
            case 1: return new int[][]{{0},{1},{2},{3},{4},{5}}; // Classic: readable list
            case 2: return new int[][]{{3},{0,1},{2,4,5}}; // Dune: picture feature + compact tools
            case 3: return new int[][]{{0,1},{3},{2,4,5}}; // Pearl: paired connectivity + picture feature
            case 4: return new int[][]{{0,1,2},{3,4,5}}; // Cobalt: compact instrument grid
            case 5: return new int[][]{{0,1},{2,3,4},{5}}; // Sage: folded tiles + screensaver feature
            case 6: return new int[][]{{5},{0,1,2},{3,4}}; // Pine: screensaver feature + utility rails
            case 7: return new int[][]{{0,1,2},{4},{3,5}}; // Amber: sound feature between tool rails
            case 8: return new int[][]{{0,1},{2,4},{3,5}}; // Harmony: large paired connection, source/sound, display/scenery cards
            default: return new int[][]{{0,1},{2,3},{4,5}}; // Apple: balanced control tiles
        }
    }
    static boolean stacked(int style) { return style == 0 || style == 3 || style == 5; }
    static int radius(int style) { return new int[]{22,10,6,24,8,14,12,6,28}[Math.max(0,Math.min(8,style))]; }
    /** Align vertical D-pad movement by the centres of equal-width cards. */
    static int neighbour(int column, int count, int targetCount) {
        return Math.min(targetCount-1, (2*column+1)*targetCount/(2*count));
    }
}
