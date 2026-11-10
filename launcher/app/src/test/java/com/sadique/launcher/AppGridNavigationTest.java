package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class AppGridNavigationTest {
    @Test public void footerReturnsToItsEntryAppRatherThanTheLastApp() {
        java.util.List<String> apps=java.util.Arrays.asList("a","b","c","d","e","f","g","h");
        for(int columns=2;columns<=6;columns++)for(int from=0;from<apps.size();from++){
            if(AppGridNavigation.verticalWithFooter(from,apps.size(),columns,true,true)==apps.size())
                assertEquals(from,AppGridNavigation.footerReturn(apps,apps.get(from),from));
        }
        assertEquals(1,AppGridNavigation.footerReturn(apps,"b",7));
    }
    @Test public void refreshAndReorderPreserveIdentityAndRemovedAppsHaveBoundedFallback() {
        java.util.List<String> reordered=java.util.Arrays.asList("c","e","b","a");
        assertEquals(2,AppGridNavigation.footerReturn(reordered,"b",1));
        assertEquals(3,AppGridNavigation.footerReturn(reordered,"removed",7));
        assertEquals(0,AppGridNavigation.footerReturn(reordered,null,-1));
        assertEquals(-1,AppGridNavigation.footerReturn(java.util.Collections.emptyList(),"b",1));
    }
    @Test public void upReachesToolbarOnlyFromFirstRowAndDownStaysInPartialLastRow() {
        assertEquals(-1, AppGridNavigation.vertical(3, 10, 4, false));
        assertEquals(0, AppGridNavigation.vertical(4, 10, 4, false));
        assertEquals(3, AppGridNavigation.vertical(7, 10, 4, false));
        assertEquals(9, AppGridNavigation.vertical(7, 10, 4, true));
        assertEquals(8, AppGridNavigation.vertical(8, 10, 4, true));
        assertEquals(9, AppGridNavigation.vertical(9, 10, 4, true));
        assertEquals(-1, AppGridNavigation.vertical(0, 0, 4, false));
    }
    @Test public void horizontalTravelCrossesRowsInBothDirections() {
        assertEquals(4, AppGridNavigation.horizontal(3, 10, true));
        assertEquals(3, AppGridNavigation.horizontal(4, 10, false));
        int position = 9;
        for (int i = 8; i >= 0; i--) { position = AppGridNavigation.horizontal(position, 10, false); assertEquals(i, position); }
    }
    @Test public void boundariesDoNotEscapeAndEmptyOrInvalidPositionsAreRejected() {
        assertEquals(0, AppGridNavigation.horizontal(0, 10, false));
        assertEquals(9, AppGridNavigation.horizontal(9, 10, true));
        assertEquals(0, AppGridNavigation.horizontal(0, 1, true));
        assertEquals(-1, AppGridNavigation.horizontal(0, 0, true));
        assertEquals(-1, AppGridNavigation.horizontal(-1, 10, true));
        assertEquals(-1, AppGridNavigation.horizontal(10, 10, false));
    }
}
