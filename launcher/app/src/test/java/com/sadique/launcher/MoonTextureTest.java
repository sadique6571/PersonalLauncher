package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class MoonTextureTest {
    @Test public void sphereHasTransparentOutsideAndVisibleGreyTerrain(){
        int[] pixels=MoonTexture.pixels(128);assertEquals(0,pixels[0]);assertEquals(0,pixels[127]);
        int darkest=255,brightest=0,visible=0,dark=0;
        for(int pixel:pixels)if((pixel>>>24)>240){int grey=(pixel>>16)&255;darkest=Math.min(darkest,grey);brightest=Math.max(brightest,grey);visible++;if(grey<150)dark++;assertTrue(grey<=226);}
        assertTrue(visible>9000);assertTrue(brightest-darkest>70);assertTrue(dark>visible/8);assertTrue((pixels[64*128+64]>>>24)>240);
    }
    @Test public void textureIsStableAndBounded(){assertArrayEquals(MoonTexture.pixels(64),MoonTexture.pixels(64));try{MoonTexture.pixels(1024);fail();}catch(IllegalArgumentException expected){}}
}
