package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class GlassTransparencyTest {
    @Test public void zeroIntensityMakesBothLayersTransparent() {
        for(int finish=0;finish<3;finish++) {
            assertEquals(0,GlassTransparency.backdropAlpha(finish,0));
            assertEquals(0,GlassTransparency.tintAlpha(finish,0));
            assertEquals(0,GlassTransparency.blurRadius(finish,0));
        }
    }
    @Test public void everySettingRetainsSomeOriginalWallpaper() {
        for(int finish=0;finish<3;finish++) for(int intensity=0;intensity<=100;intensity++) {
            assertTrue(GlassTransparency.backdropAlpha(finish,intensity)<255);
            assertTrue(GlassTransparency.tintAlpha(finish,intensity)<255);
        }
        assertTrue(originalFraction(0,30)>.70);
        assertTrue(originalFraction(0,70)>.40);
        assertTrue(originalFraction(1,100)>.50);
    }
    @Test public void airyNormalAndSolidChangeBothLayersAndClearDoesNotBlur() {
        for(int finish=0;finish<3;finish++) {
            assertTrue(GlassTransparency.backdropAlpha(finish,30)<GlassTransparency.backdropAlpha(finish,70));
            assertTrue(GlassTransparency.backdropAlpha(finish,70)<GlassTransparency.backdropAlpha(finish,100));
            assertTrue(GlassTransparency.tintAlpha(finish,30)<GlassTransparency.tintAlpha(finish,70));
            assertTrue(GlassTransparency.tintAlpha(finish,70)<GlassTransparency.tintAlpha(finish,100));
        }
        assertEquals(0,GlassTransparency.blurRadius(1,100));
    }
    private double originalFraction(int finish,int intensity) {
        return (1-GlassTransparency.backdropAlpha(finish,intensity)/255d)
                *(1-GlassTransparency.tintAlpha(finish,intensity)/255d);
    }
}
