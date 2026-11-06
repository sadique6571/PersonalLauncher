package com.sadique.launcher;

import java.util.*;

/** Shared native/SVG geometry commands: 0 move, 1 line, 2 quadratic, 3 close. */
final class DemoGeometry {
    private DemoGeometry() { }
    static int[] clockSizes(boolean tv) { return tv?new int[]{64,84,104}:new int[]{44,60,76}; }
    static float ratio() { return 2.6f; }
    static float footer(int style,float density) { return style==ThemeStyle.DUNE||style==ThemeStyle.COBALT?8*density:style==ThemeStyle.SAGE?3*density:0; }
    static float[][] outline(int style,float w,float h,float radius) {
        float c=Math.max(1,Math.min(radius,Math.min(w,h)*.45f));
        List<float[]> p=new ArrayList<>();
        if(style==ThemeStyle.DUNE) {
            c=Math.min(c+h*.06f,h*.32f);
            p.add(new float[]{0,c,0}); p.add(new float[]{1,w-c,0}); p.add(new float[]{1,w,c});
            p.add(new float[]{1,w,h-c}); p.add(new float[]{1,w-c,h}); p.add(new float[]{1,c,h});
            p.add(new float[]{1,0,h-c}); p.add(new float[]{1,0,c*1.6f});
            p.add(new float[]{1,c*.45f,c}); p.add(new float[]{1,c*.7f,c}); p.add(new float[]{1,c,0});
        } else if(style==ThemeStyle.PEARL) {
            c=Math.min(c*.7f,h*.3f); float d=c*.4f;
            p.add(new float[]{0,c,0}); p.add(new float[]{1,w-c,0});
            p.add(new float[]{2,w-d,0,w-d,d}); p.add(new float[]{2,w,d,w,c});
            p.add(new float[]{1,w,h-c}); p.add(new float[]{2,w,h-d,w-d,h-d}); p.add(new float[]{2,w-d,h,w-c,h});
            p.add(new float[]{1,c,h}); p.add(new float[]{2,d,h,d,h-d}); p.add(new float[]{2,0,h-d,0,h-c});
            p.add(new float[]{1,0,c}); p.add(new float[]{2,0,d,d,d}); p.add(new float[]{2,d,0,c,0});
        } else {
            c=Math.min(c*.7f,h*.4f); p.add(new float[]{0,c,0});
            if(style==ThemeStyle.SAGE) { float f=Math.min(w*.15f,h*.22f); p.add(new float[]{1,w-f,0}); p.add(new float[]{1,w,f}); }
            else { p.add(new float[]{1,w-c,0}); p.add(new float[]{2,w,0,w,c}); }
            p.add(new float[]{1,w,h-c}); p.add(new float[]{2,w,h,w-c,h});
            p.add(new float[]{1,c,h}); p.add(new float[]{2,0,h,0,h-c});
            p.add(new float[]{1,0,c}); p.add(new float[]{2,0,0,c,0});
        }
        p.add(new float[]{3}); return p.toArray(new float[0][]);
    }
}
