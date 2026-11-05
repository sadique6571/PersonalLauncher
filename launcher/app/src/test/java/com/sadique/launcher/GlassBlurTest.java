package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class GlassBlurTest {
    @Test public void uniformWallpaperDoesNotChangeColour() {
        int[] pixels=new int[15]; java.util.Arrays.fill(pixels,0xff527a96);
        GlassBlur.blur(pixels,5,3,5);
        for (int pixel:pixels) assertEquals(0xff527a96,pixel);
    }
    @Test public void clampedEdgesAndBothAxesHaveTheSameResult() {
        int[] horizontal={0xff000000,0xffffffff}, vertical=horizontal.clone();
        GlassBlur.blur(horizontal,2,1,1); GlassBlur.blur(vertical,1,2,1);
        assertArrayEquals(new int[]{0xff7a7a7a,0xff838383},horizontal);
        assertArrayEquals(horizontal,vertical);
    }
    @Test public void zeroRadiusLeavesInputUntouched() {
        int[] pixels={0xffaabbcc}; GlassBlur.blur(pixels,1,1,0);
        assertEquals(0xffaabbcc,pixels[0]);
    }
    @Test public void invalidBoundsAreRejected() {
        try { GlassBlur.blur(new int[3],2,2,1); fail(); } catch (IllegalArgumentException expected) { }
        try { GlassBlur.blur(new int[1],1,1,-1); fail(); } catch (IllegalArgumentException expected) { }
    }
}
