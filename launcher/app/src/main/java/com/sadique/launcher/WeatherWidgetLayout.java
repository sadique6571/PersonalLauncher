package com.sadique.launcher;
/** Weather is a visual override; the user's saved clock alignment is never rewritten. */
final class WeatherWidgetLayout {
    private WeatherWidgetLayout(){}
    static int clockAlignment(boolean enabled,int saved){return enabled?0:Math.max(0,Math.min(2,saved));}
    static int width(int available,boolean glass){return Math.max(180,Math.min(glass?260:300,Math.round(available*(glass?.25f:.34f))));}
    static int telemetryWidth(int available){return Math.max(180,Math.min(240,Math.round(available*.23f)));}
}
