package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClassicGlassAlphaTest {
    @Test public void classicPanelAndCardCannotHideTheEntireWallpaper() {
        for(int value:new int[]{30,70,100}) {
            double remaining=(1-ClassicGlassAlpha.card(value,false)/255d)*(1-ClassicGlassAlpha.panel(value)/255d)*.88;
            assertTrue(remaining>.20);
        }
        assertEquals(0,ClassicGlassAlpha.panel(0)); assertEquals(0,ClassicGlassAlpha.card(0,false));
    }
    @Test public void everyIntensityChangesBothLayersAndFocusRemainsStronger() {
        int previousCard=-1,previousPanel=-1;
        for(int value:new int[]{30,70,100}) {
            assertTrue(ClassicGlassAlpha.card(value,false)>previousCard);
            assertTrue(ClassicGlassAlpha.panel(value)>previousPanel);
            assertTrue(ClassicGlassAlpha.card(value,true)>ClassicGlassAlpha.card(value,false));
            assertTrue(ClassicGlassAlpha.card(value,true)<255);
            previousCard=ClassicGlassAlpha.card(value,false); previousPanel=ClassicGlassAlpha.panel(value);
        }
        assertTrue(ClassicGlassAlpha.drawer(30)<ClassicGlassAlpha.drawer(100));
    }
}
