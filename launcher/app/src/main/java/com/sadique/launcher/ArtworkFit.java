package com.sadique.launcher;
/** Fit the complete source mark inside a 256px cache image, including at Large logo size. */
final class ArtworkFit {
    private ArtworkFit(){}
    static float scale(int width,int height,float requested){
        if(width<=0||height<=0||!Float.isFinite(requested))throw new IllegalArgumentException("Invalid logo dimensions");
        float normal=Math.min(220f/width,220f/height)*Math.max(.5f,Math.min(1.2f,requested));
        return Math.min(normal,Math.min(240f/width,240f/height));
    }
}
