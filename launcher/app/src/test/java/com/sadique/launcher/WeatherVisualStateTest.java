package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class WeatherVisualStateTest {
    @Test public void everySupportedWmoCodeUsesTheCorrectAtmosphere(){
        int[][] groups={{0,1},{2},{3},{45,48},{51,53,55,56,57,61,63,65,66,67,80,81,82},{71,73,75,77,85,86},{95,96,99}};
        for(int kind=0;kind<groups.length;kind++)for(int code:groups[kind])assertEquals("WMO "+code,kind+1,WeatherVisualState.kind(code));
    }
    @Test public void unknownAndDryConditionsNeverInventPrecipitation(){
        for(int code:new int[]{-1,4,50,60,70,78,83,94,97,100,Integer.MAX_VALUE}){assertEquals(WeatherVisualState.UNKNOWN,WeatherVisualState.kind(code));assertEquals(0,WeatherVisualState.drops(code));}
        for(int code:new int[]{0,1,2,3,45,48,71,75,85})assertEquals(0,WeatherVisualState.drops(code));
    }
    @Test public void rainIntensityIsBoundedAndDistinguishesDrizzle(){assertTrue(WeatherVisualState.drops(51)<WeatherVisualState.drops(63));assertTrue(WeatherVisualState.drops(63)<WeatherVisualState.drops(65));for(int code=0;code<110;code++)assertTrue(WeatherVisualState.drops(code)<=42);}
}
