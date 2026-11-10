package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class ArtworkFitTest {
    @Test public void largeMarksNeverExceedTheirCacheBitmap(){
        for(int w:new int[]{32,128,256,512})for(int h:new int[]{32,128,256,512})for(float size:new float[]{.8f,1,1.2f}){
            float scale=ArtworkFit.scale(w,h,size);assertTrue(scale>0);assertTrue(w*scale<=240.001f);assertTrue(h*scale<=240.001f);
            assertEquals(w/(float)h,(w*scale)/(h*scale),.0001f);
        }
        assertEquals(220f/256,ArtworkFit.scale(256,256,1),.0001f);
        assertEquals(240f/256,ArtworkFit.scale(256,256,1.2f),.0001f);
    }
}
