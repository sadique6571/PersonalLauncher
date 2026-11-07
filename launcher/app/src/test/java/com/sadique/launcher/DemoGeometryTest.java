package com.sadique.launcher;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class DemoGeometryTest {
    @Test public void everyDemoOutlineIsClosedAndInsideTheArtworkBounds() {
        Set<String> shapes=new HashSet<>();
        for(int style=ThemeStyle.DUNE;style<ThemeStyle.COUNT;style++) {
            float[][] outline=DemoGeometry.outline(style,320,123,16);
            assertEquals(0,(int)outline[0][0]); assertEquals(3,(int)outline[outline.length-1][0]);
            for(float[] segment:outline) for(int i=1;i<segment.length;i+=2) {
                assertTrue(Float.isFinite(segment[i])); assertTrue(Float.isFinite(segment[i+1]));
                assertTrue(segment[i]>=0 && segment[i]<=320); assertTrue(segment[i+1]>=0 && segment[i+1]<=123);
            }
            shapes.add(Arrays.deepToString(outline));
        }
        assertEquals(4,shapes.size());
    }
    @Test public void allCornerChoicesRemainDistinctForEachCardFamily() {
        for(int style=2;style<ThemeStyle.COUNT;style++) {
            Set<String> choices=new HashSet<>();
            for(int radius:new int[]{4,16,28}) choices.add(Arrays.deepToString(DemoGeometry.outline(style,260,100,radius)));
            assertEquals(3,choices.size());
        }
    }
    @Test public void referencesHaveWideCardsAndSpaceForTheirOwnFocusMarks() {
        assertEquals(2.6f,DemoGeometry.ratio(),.001f);
        assertTrue(DemoGeometry.footer(ThemeStyle.DUNE,1)>0);
        assertTrue(DemoGeometry.footer(ThemeStyle.COBALT,1)>0);
        assertEquals(0,DemoGeometry.footer(ThemeStyle.PEARL,1),.001f);
    }
}
