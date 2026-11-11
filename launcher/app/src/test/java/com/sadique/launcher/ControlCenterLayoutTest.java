package com.sadique.launcher;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.Assert.*;

public class ControlCenterLayoutTest {
    @Test public void everyThemeHasDistinctLayoutAndEveryQuickActionExactlyOnce() {
        HashSet<String> arrangements=new HashSet<>();
        for(int style=0;style<ThemeStyle.COUNT;style++) {
            int[][] rows=ControlCenterLayout.rows(style);
            assertTrue("Theme repeats another layout: "+style,arrangements.add(Arrays.deepToString(rows)));
            boolean[] seen=new boolean[6];
            for(int[] row:rows) {
                assertTrue(row.length>=1&&row.length<=3);
                for(int action:row) {
                    assertTrue(action>=0&&action<6);
                    assertFalse("Duplicate action",seen[action]); seen[action]=true;
                }
            }
            for(boolean present:seen) assertTrue("Missing quick action",present);
        }
    }
    @Test public void harmonyVisionHasItsOwnRemoteFriendlyControls() {
        assertArrayEquals(new int[]{0,1},ControlCenterLayout.rows(ThemeStyle.HARMONY)[0]);
        assertArrayEquals(new int[]{2,4},ControlCenterLayout.rows(ThemeStyle.HARMONY)[1]);
        assertArrayEquals(new int[]{3,5},ControlCenterLayout.rows(ThemeStyle.HARMONY)[2]);
        assertEquals(28,ControlCenterLayout.radius(ThemeStyle.HARMONY));
        assertEquals("appearance.harmony.",ThemeStyle.prefix(ThemeStyle.HARMONY));
        assertEquals(ThemeStyle.HARMONY,ThemeStyle.clamp(ThemeStyle.HARMONY));
    }
    @Test public void verticalNavigationStaysInAdjacentRowAndMaintainsLeftToRightOrder() {
        for(int count=1;count<=3;count++) for(int target=1;target<=3;target++) {
            int previous=-1;
            for(int col=0;col<count;col++) {
                int next=ControlCenterLayout.neighbour(col,count,target);
                assertTrue(next>=0&&next<target); assertTrue(next>=previous); previous=next;
                if(count==target) assertEquals(col,next);
            }
        }
    }
}
