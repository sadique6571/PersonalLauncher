package com.sadique.launcher;

/** Testable material controls shared by Dune and Pearl. Finish and intensity affect both glass layers. */
final class MaterialAppearance {
    private MaterialAppearance() { }
    static int alpha(int finish,int intensity,boolean drawer,boolean focused) {
        int value=Math.max(0,Math.min(100,intensity));
        if(drawer) return Math.min(255,GlassTransparency.tintAlpha(finish,value)+(focused?Math.round(30*value/100f):0));
        int max=finish==1?190:finish==2?255:230;
        return Math.min(255,Math.round(max*value/100f)+(focused?Math.round(12*value/100f):0));
    }
    static int tint(int base,int accent,boolean focused) {
        return mix(base,accent,focused?.22f:.12f);
    }
    static int mix(int a,int b,float fraction) {
        int red=Math.round(((a>>16)&255)*(1-fraction)+((b>>16)&255)*fraction);
        int green=Math.round(((a>>8)&255)*(1-fraction)+((b>>8)&255)*fraction);
        int blue=Math.round((a&255)*(1-fraction)+(b&255)*fraction);
        return 0xff000000|(red<<16)|(green<<8)|blue;
    }
}
