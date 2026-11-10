package com.sadique.launcher;
/** Exact WMO groups: unknown codes must never display invented storms. */
final class WeatherVisualState {
    static final int UNKNOWN=0,CLEAR=1,PARTLY=2,CLOUD=3,FOG=4,RAIN=5,SNOW=6,STORM=7;
    static int kind(int code){
        switch(code){
            case 0:case 1:return CLEAR;case 2:return PARTLY;case 3:return CLOUD;
            case 45:case 48:return FOG;
            case 51:case 53:case 55:case 56:case 57:case 61:case 63:case 65:case 66:case 67:case 80:case 81:case 82:return RAIN;
            case 71:case 73:case 75:case 77:case 85:case 86:return SNOW;
            case 95:case 96:case 99:return STORM;
            default:return UNKNOWN;
        }
    }
    static int drops(int code){if(kind(code)==STORM||code==65||code==67||code==82)return 42;if(code==51||code==53||code==56)return 12;return kind(code)==RAIN?26:0;}
}
