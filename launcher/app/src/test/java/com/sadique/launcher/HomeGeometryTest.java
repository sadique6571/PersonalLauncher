package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeGeometryTest {
    @Test public void cornerChoicesHaveNoGlassOnlyMinimumAndClampInvalidImports() {
        assertEquals(4,HomeGeometry.tileRadius(-1));
        assertEquals(4,HomeGeometry.tileRadius(0));
        assertEquals(16,HomeGeometry.tileRadius(1));
        assertEquals(28,HomeGeometry.tileRadius(2));
        assertEquals(28,HomeGeometry.tileRadius(99));
    }
    @Test public void insetRimsKeepTheSameOutsideCurvatureAtEveryStrokeWidth() {
        for (int choice=0;choice<3;choice++) for (int stroke:new int[]{1,2,3,5}) {
            float radius=HomeGeometry.tileRadius(choice);
            float inset=stroke/2f;
            assertEquals(radius,HomeGeometry.insetRadius(radius,inset)+inset,.001f);
            assertTrue(HomeGeometry.insetRadius(radius,stroke*2)>=0);
        }
    }
    @Test public void roundedAvatarPreviewAndStatusBarHaveTheSameProportions() {
        assertEquals(.25f,HomeGeometry.avatarRadius(44)/44,.001f);
        assertEquals(.25f,HomeGeometry.avatarRadius(96)/96,.001f);
    }
}
