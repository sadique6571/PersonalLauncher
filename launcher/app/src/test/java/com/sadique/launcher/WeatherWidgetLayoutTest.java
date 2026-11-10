package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class WeatherWidgetLayoutTest {
    @Test public void widgetOverridesWithoutChangingSavedAlignment(){
        for(int saved=0;saved<=2;saved++){
            assertEquals(0,WeatherWidgetLayout.clockAlignment(true,saved));
            assertEquals(saved,WeatherWidgetLayout.clockAlignment(false,saved));
        }
    }
    @Test public void boundsFitThe1080pTvStageAndSmallPreviews(){
        for(int width:new int[]{320,700,896,1216,1856}){
            int widget=WeatherWidgetLayout.width(width,true),stats=WeatherWidgetLayout.telemetryWidth(width);
            assertTrue(widget>=180&&widget<=260);assertTrue(stats>=180&&stats<=240);
            if(width>=700)assertTrue(widget+stats+12+160<width);
            assertTrue(WeatherWidgetLayout.width(width,false)<=300);
        }
    }
}
