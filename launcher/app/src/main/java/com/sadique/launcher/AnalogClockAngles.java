package com.sadique.launcher;
/** Fractional seconds drive every hand; the renderer never advances by one-second steps. */
final class AnalogClockAngles {
    private AnalogClockAngles(){}
    static float seconds(int second,int millis){return 6f*(second+millis/1000f);}
    static float minutes(int minute,int second,int millis){return 6f*(minute+(second+millis/1000f)/60f);}
    static float hours(int hour,int minute,int second,int millis){return 30f*(hour%12+(minute+(second+millis/1000f)/60f)/60f);}
}
