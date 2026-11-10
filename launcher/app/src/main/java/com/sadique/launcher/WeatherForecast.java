package com.sadique.launcher;

import org.json.*;
import java.util.*;

/** Bounded forecast snapshot. Missing observations are NaN, never invented zero readings. */
final class WeatherForecast {
    static final class Hour {
        final String time;final double temperature;final int code;final double rain;
        Hour(String t,double temp,int c,double r){time=t;temperature=temp;code=c;rain=r;}
    }
    static final class Day {
        final String date;final double low,high,rain;final int code;
        Day(String d,double l,double h,int c,double r){date=d;low=l;high=h;code=c;rain=r;}
    }
    final double temperature,feels,humidity,wind;
    final int code;final boolean day;final String condition,source;
    final List<Hour> hours;final List<Day> days;
    WeatherForecast(double t,double f,double h,double w,int c,boolean day,String condition,String source,List<Hour> hours,List<Day> days){
        temperature=t;feels=f;humidity=h;wind=w;code=c;this.day=day;this.condition=condition;this.source=source;
        this.hours=Collections.unmodifiableList(new ArrayList<>(hours));this.days=Collections.unmodifiableList(new ArrayList<>(days));
    }
    String summary(boolean fahrenheit){return String.format(Locale.getDefault(),"%.0f°%s · %s",temperature,fahrenheit?"F":"C",condition);}
    static String url(double lat,double lon,boolean fahrenheit){
        LandscapeModel.weatherUrl(lat,lon,fahrenheit); // Validate coordinates before interpolating.
        return "https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon
            +"&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m"
            +"&hourly=temperature_2m,weather_code,precipitation_probability"
            +"&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
            +"&forecast_days=7&forecast_hours=24&wind_speed_unit=kmh&temperature_unit="+(fahrenheit?"fahrenheit":"celsius")+"&timezone=auto";
    }
    static WeatherForecast fetch(double lat,double lon,boolean fahrenheit)throws Exception{return fetch(lat,lon,fahrenheit,OnlineContent::json);}
    static WeatherForecast fetch(double lat,double lon,boolean fahrenheit,WeatherClient.Loader loader)throws Exception{
        String primaryUrl=url(lat,lon,fahrenheit);
        try{return parse(loader.json(primaryUrl));}
        catch(Exception primary){
            if(Thread.currentThread().isInterrupted())throw primary;
            try{return fallback(loader.json("https://wttr.in/"+lat+","+lon+"?format=j1&lang=en"),fahrenheit);}
            catch(Exception secondary){secondary.addSuppressed(primary);throw secondary;}
        }
    }
    private static double number(JSONObject object,String key){double value=object.optDouble(key,Double.NaN);return Double.isFinite(value)?value:Double.NaN;}
    private static double value(JSONArray values,int index){double value=values==null?Double.NaN:values.optDouble(index,Double.NaN);return Double.isFinite(value)?value:Double.NaN;}
    private static double probability(double value){return value>=0&&value<=100?value:Double.NaN;}
    static WeatherForecast parse(String json)throws JSONException{
        JSONObject root=new JSONObject(json),current=root.getJSONObject("current");double temperature=number(current,"temperature_2m");
        if(!Double.isFinite(temperature))throw new IllegalArgumentException("Missing temperature");
        int code=current.optInt("weather_code",-1);String now=current.optString("time","");
        List<Hour> hours=new ArrayList<>();JSONObject hourly=root.optJSONObject("hourly");
        if(hourly!=null){JSONArray times=hourly.optJSONArray("time"),temps=hourly.optJSONArray("temperature_2m"),codes=hourly.optJSONArray("weather_code"),rain=hourly.optJSONArray("precipitation_probability");
            for(int i=0;times!=null&&i<Math.min(192,times.length())&&hours.size()<12;i++){
                String time=times.optString(i,"");double temp=value(temps,i);
                if(!time.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}.*")||!Double.isFinite(temp)||(!now.isEmpty()&&time.compareTo(now)<0))continue;
                hours.add(new Hour(time,temp,codes==null?-1:codes.optInt(i,-1),probability(value(rain,i))));
            }
        }
        List<Day> days=new ArrayList<>();JSONObject daily=root.optJSONObject("daily");
        if(daily!=null){JSONArray dates=daily.optJSONArray("time"),low=daily.optJSONArray("temperature_2m_min"),high=daily.optJSONArray("temperature_2m_max"),codes=daily.optJSONArray("weather_code"),rain=daily.optJSONArray("precipitation_probability_max");
            for(int i=0;dates!=null&&i<Math.min(7,dates.length());i++){
                String date=dates.optString(i,"");double l=value(low,i),h=value(high,i);
                if(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||!Double.isFinite(l)||!Double.isFinite(h)||l>h)continue;
                days.add(new Day(date,l,h,codes==null?-1:codes.optInt(i,-1),probability(value(rain,i))));
            }
        }
        return new WeatherForecast(temperature,number(current,"apparent_temperature"),probability(number(current,"relative_humidity_2m")),number(current,"wind_speed_10m"),code,current.optInt("is_day",1)==1,LandscapeModel.condition(code),"Open-Meteo",hours,days);
    }
    static WeatherForecast fallback(String json,boolean fahrenheit)throws JSONException{
        JSONObject c=new JSONObject(json).getJSONArray("current_condition").getJSONObject(0);
        double temperature=number(c,fahrenheit?"temp_F":"temp_C");if(!Double.isFinite(temperature))throw new IllegalArgumentException("Missing temperature");
        String condition=HomeLibraryModel.title(c.getJSONArray("weatherDesc").getJSONObject(0).getString("value"));if(condition.isEmpty())throw new IllegalArgumentException("Missing condition");
        String text=condition.toLowerCase(Locale.ROOT);int code=text.contains("thunder")?95:text.contains("snow")?71:text.contains("rain")?61:text.contains("drizzle")?51:text.contains("fog")||text.contains("mist")?45:text.contains("overcast")?3:text.contains("cloud")?2:text.contains("sun")||text.contains("clear")?0:-1;
        return new WeatherForecast(temperature,number(c,fahrenheit?"FeelsLikeF":"FeelsLikeC"),probability(number(c,"humidity")),number(c,"windspeedKmph"),code,!text.contains("night"),condition,"wttr.in",Collections.emptyList(),Collections.emptyList());
    }
    String encode()throws JSONException{
        JSONObject current=new JSONObject().put("temperature_2m",temperature).put("weather_code",code).put("is_day",day?1:0);
        put(current,"apparent_temperature",feels);put(current,"relative_humidity_2m",humidity);put(current,"wind_speed_10m",wind);
        JSONArray ht=new JSONArray(),hv=new JSONArray(),hc=new JSONArray(),hr=new JSONArray();
        for(Hour hour:hours){ht.put(hour.time);hv.put(hour.temperature);hc.put(hour.code);hr.put(Double.isFinite(hour.rain)?hour.rain:JSONObject.NULL);}
        JSONArray dt=new JSONArray(),dl=new JSONArray(),dh=new JSONArray(),dc=new JSONArray(),dr=new JSONArray();
        for(Day d:days){dt.put(d.date);dl.put(d.low);dh.put(d.high);dc.put(d.code);dr.put(Double.isFinite(d.rain)?d.rain:JSONObject.NULL);}
        return new JSONObject().put("current",current).put("hourly",new JSONObject().put("time",ht).put("temperature_2m",hv).put("weather_code",hc).put("precipitation_probability",hr))
            .put("daily",new JSONObject().put("time",dt).put("temperature_2m_min",dl).put("temperature_2m_max",dh).put("weather_code",dc).put("precipitation_probability_max",dr))
            .put("widget_source",source).put("widget_condition",condition).toString();
    }
    static WeatherForecast decode(String raw)throws JSONException{
        JSONObject json=new JSONObject(raw);WeatherForecast forecast=parse(raw);
        return new WeatherForecast(forecast.temperature,forecast.feels,forecast.humidity,forecast.wind,forecast.code,forecast.day,json.optString("widget_condition",forecast.condition),json.optString("widget_source",forecast.source),forecast.hours,forecast.days);
    }
    private static void put(JSONObject json,String key,double value)throws JSONException{if(Double.isFinite(value))json.put(key,value);}
}
