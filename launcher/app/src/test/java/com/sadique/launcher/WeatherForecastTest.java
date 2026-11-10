package com.sadique.launcher;
import java.io.IOException;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class WeatherForecastTest {
    private static final String CURRENT="{\"current\":{\"time\":\"2026-11-10T09:15\",\"temperature_2m\":24,\"weather_code\":2,\"is_day\":0}}";
    private static final String FALLBACK="{\"current_condition\":[{\"temp_C\":\"22\",\"temp_F\":\"72\",\"FeelsLikeF\":\"70\",\"humidity\":\"42\",\"windspeedKmph\":\"9\",\"weatherDesc\":[{\"value\":\"Partly cloudy\"}]}]}";
    @Test public void validatesCoordinatesBeforeAnyRequest() throws Exception {
        for(double lat:new double[]{Double.NaN,91,Double.POSITIVE_INFINITY})try{
            WeatherForecast.fetch(lat,0,false,url->{fail("Invalid coordinates reached loader");return CURRENT;});fail();
        }catch(IllegalArgumentException expected){}
        String url=WeatherForecast.url(30.81,73.45,true);
        assertTrue(url.contains("temperature_unit=fahrenheit"));assertTrue(url.contains("timezone=auto"));assertTrue(url.contains("forecast_days=7"));
    }
    @Test public void optionalObservationsStayMissingRatherThanZero()throws Exception{
        WeatherForecast f=WeatherForecast.parse(CURRENT);assertEquals(24,f.temperature,0);assertFalse(f.day);
        assertTrue(Double.isNaN(f.feels));assertTrue(Double.isNaN(f.humidity));assertTrue(Double.isNaN(f.wind));
        assertTrue(f.hours.isEmpty());assertTrue(f.days.isEmpty());
        try{WeatherForecast.parse("{\"current\":{}}");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void filtersPastAndMalformedHoursAndBoundsBothForecasts()throws Exception{
        JSONObject json=new JSONObject(CURRENT);JSONArray times=new JSONArray(),temps=new JSONArray(),rain=new JSONArray();
        times.put("bad");temps.put(23);rain.put(30);
        times.put("2026-11-10T08:00");temps.put(23);rain.put(30);
        for(int h=10;h<24;h++){times.put(String.format(java.util.Locale.ROOT,"2026-11-10T%02d:00",h));temps.put(h);rain.put(h==10?150:20);}
        json.put("hourly",new JSONObject().put("time",times).put("temperature_2m",temps).put("precipitation_probability",rain));
        JSONArray dates=new JSONArray(),low=new JSONArray(),high=new JSONArray();for(int d=10;d<20;d++){dates.put("2026-11-"+d);low.put(10);high.put(20);}
        json.put("daily",new JSONObject().put("time",dates).put("temperature_2m_min",low).put("temperature_2m_max",high));
        WeatherForecast f=WeatherForecast.parse(json.toString());assertEquals(12,f.hours.size());assertEquals("2026-11-10T10:00",f.hours.get(0).time);
        assertTrue(Double.isNaN(f.hours.get(0).rain));assertEquals(7,f.days.size());
        WeatherForecast restored=WeatherForecast.decode(f.encode());assertEquals(f.hours.size(),restored.hours.size());assertEquals(7,restored.days.size());assertFalse(restored.day);
    }
    @Test public void fallbackHasActualCurrentDataAndNoInventedForecast()throws Exception{
        WeatherForecast f=WeatherForecast.fetch(0,0,true,url->{if(url.contains("open-meteo"))throw new IOException("503");return FALLBACK;});
        assertEquals(72,f.temperature,0);assertEquals(70,f.feels,0);assertEquals(9,f.wind,0);assertTrue(f.hours.isEmpty());assertTrue(f.days.isEmpty());
        WeatherForecast cache=WeatherForecast.decode(f.encode());assertEquals("wttr.in",cache.source);assertEquals("Partly cloudy",cache.condition);assertEquals(42,cache.humidity,0);
    }
    @Test public void failedProvidersPreserveDiagnosis()throws Exception{
        try{WeatherForecast.fetch(0,0,false,url->{throw new IOException(url);});fail();}
        catch(IOException failure){assertEquals(1,failure.getSuppressed().length);}
    }
}
