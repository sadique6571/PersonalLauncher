package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.widget.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Forecast detail uses ordinary remote-focusable rows, never a WebView. */
final class WeatherForecastPanel {
    private final Activity host;private final LinearLayout rows;private final Runnable refresh,city;
    private AlertDialog dialog;
    private WeatherForecast last;private String lastName;private boolean lastUnit;
    WeatherForecastPanel(Activity host,Runnable refresh,Runnable city){this.host=host;this.refresh=refresh;this.city=city;rows=HomeLibraryUi.rows(host);}
    void show(WeatherForecast data,String name,boolean fahrenheit){render(data,name,fahrenheit);dialog=GlassSettings.panel(host,"Weather forecast",rows);dialog.show();}
    void update(WeatherForecast data,String name,boolean fahrenheit){if(dialog!=null&&dialog.isShowing())render(data,name,fahrenheit);}
    boolean isShowing(){return dialog!=null&&dialog.isShowing();}
    void close(){if(dialog!=null)dialog.dismiss();}
    private void render(WeatherForecast data,String name,boolean fahrenheit){
        if(data==last&&name.equals(lastName)&&fahrenheit==lastUnit)return;
        last=data;lastName=name;lastUnit=fahrenheit;
        boolean restore=rows.hasFocus();rows.removeAllViews();
        rows.addView(GlassSettings.label(host,name,18,ThemeStyle.foreground(ThemePreferences.get(host))));
        rows.addView(GlassSettings.label(host,data==null?"Unavailable":data.summary(fahrenheit),15,ThemeStyle.foreground(ThemePreferences.get(host))));
        rows.addView(GlassSettings.row(host,"Refresh weather",null,R.drawable.ic_brightness,GlassSettings.themeAccent(host),refresh));
        rows.addView(GlassSettings.row(host,"Change weather city",null,R.drawable.ic_picture,GlassSettings.themeAccent(host),city));
        if(data!=null){
            rows.addView(GlassSettings.label(host,"Hourly forecast",14,GlassSettings.themeAccent(host)));
            if(data.hours.isEmpty())rows.addView(GlassSettings.label(host,"Hourly forecast unavailable",12,ThemeStyle.muted(ThemePreferences.get(host))));
            for(WeatherForecast.Hour hour:data.hours){
                String title=hour.time.length()>=16?hour.time.substring(11,16):hour.time;
                row(title+" · "+String.format(Locale.getDefault(),"%.0f°%s",hour.temperature,fahrenheit?"F":"C"),LandscapeModel.condition(hour.code)+rain(hour.rain));
            }
            rows.addView(GlassSettings.label(host,"Daily forecast",14,GlassSettings.themeAccent(host)));
            if(data.days.isEmpty())rows.addView(GlassSettings.label(host,"Daily forecast unavailable",12,ThemeStyle.muted(ThemePreferences.get(host))));
            for(WeatherForecast.Day day:data.days){String title=day.date;try{title=LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EEE, d MMM",Locale.ENGLISH));}catch(java.time.DateTimeException ignored){}
                row(title+String.format(Locale.getDefault()," · %.0f° / %.0f°",day.high,day.low),LandscapeModel.condition(day.code)+rain(day.rain));}
            rows.addView(GlassSettings.label(host,"Weather data: "+data.source,10,ThemeStyle.muted(ThemePreferences.get(host))));
        }
        if(restore&&rows.getChildCount()>2)rows.getChildAt(2).requestFocus();
    }
    private String rain(double value){return Double.isFinite(value)?String.format(Locale.getDefault()," · Rain %.0f%%",value):"";}
    private void row(String title,String detail){rows.addView(GlassSettings.row(host,title,detail,R.drawable.ic_brightness,GlassSettings.themeAccent(host),()->{}));}
}
