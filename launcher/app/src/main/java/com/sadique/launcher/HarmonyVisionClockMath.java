package com.sadique.launcher;

/** Pure clock geometry and angular continuity for the three Harmony watch faces. */
final class HarmonyVisionClockMath {
    private HarmonyVisionClockMath() {}
    static int clampFace(int face){return Math.max(0,Math.min(3,face));}
    static double secondAngle(int second,int millis,boolean smooth){
        return ((second+(smooth?Math.max(0,Math.min(999,millis))/1000d:0))%60)*6;
    }
    static double minuteAngle(int minute,int second,int millis){
        return (minute+(second+Math.max(0,Math.min(999,millis))/1000d)/60d)*6;
    }
    static double hourAngle(int hour,int minute,int second,int millis){
        return ((hour%12)+(minute+(second+Math.max(0,Math.min(999,millis))/1000d)/60d)/60d)*30;
    }
}
