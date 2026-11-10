package com.sadique.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;

/** A theme-native weather card. Data and lifecycle belong to the existing Home weather controller. */
final class WeatherWidgetView extends LinearLayout {
    private final Activity host;private final SharedPreferences prefs;
    private final TextView city,temperature,condition,details;
    private final WeatherConditionView glyph;
    private boolean active;
    private WeatherForecast last;private String lastName;private boolean lastUnit;
    WeatherWidgetView(Activity host,Runnable open){
        super(host);this.host=host;prefs=ThemePreferences.get(host);setOrientation(VERTICAL);setPadding(dp(14),dp(12),dp(14),dp(12));
        setFocusable(true);setClickable(true);setBackground(surface(false));setOnFocusChangeListener((v,f)->setBackground(surface(f)));setOnClickListener(v->open.run());
        city=label("",12);city.setSingleLine();city.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(city);
        LinearLayout current=new LinearLayout(host);current.setGravity(Gravity.CENTER_VERTICAL);
        temperature=label("—",34);temperature.setTypeface(temperature.getTypeface(),Typeface.BOLD);temperature.setSingleLine();
        current.addView(temperature,new LayoutParams(0,-2,1));glyph=new WeatherConditionView(host);current.addView(glyph,new LayoutParams(dp(56),dp(56)));addView(current,new LayoutParams(-1,dp(58)));
        condition=label("Unavailable",13);condition.setSingleLine();condition.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(condition);
        details=label("",10);details.setMaxLines(2);details.setTextColor(ThemeStyle.muted(prefs));LayoutParams detailParams=new LayoutParams(-1,-2);detailParams.topMargin=dp(7);addView(details,detailParams);
        TextView hint=label("OK · Hourly / daily forecast",9);hint.setTextColor(ThemeStyle.muted(prefs));hint.setPadding(0,dp(8),0,0);addView(hint);
        bind(null,prefs.getString("weather_city",LandscapeModel.CITY),prefs.getBoolean("weather_fahrenheit",false));
    }
    private int dp(float n){return GlassSettings.dp(host,n);}
    private TextView label(String text,int size){return GlassSettings.label(host,text,size,ThemeStyle.foreground(prefs));}
    private GradientDrawable surface(boolean focused){
        if(ThemeStyle.id(prefs)==ThemeStyle.GLASS){GradientDrawable d=GlassOsUi.panel(host,22);if(focused)d.setStroke(dp(2),GlassSettings.themeAccent(host));return d;}
        GradientDrawable d=GlassSettings.background(host,focused);d.setCornerRadius(dp(20));return d;
    }
    void bind(WeatherForecast model,String name,boolean fahrenheit){
        if(model==last&&name.equals(lastName)&&fahrenheit==lastUnit)return;
        last=model;lastName=name;lastUnit=fahrenheit;
        city.setText(name);temperature.setText(model==null?"—":String.format(Locale.getDefault(),"%.0f°%s",model.temperature,fahrenheit?"F":"C"));
        condition.setText(model==null?"Unavailable":model.condition);
        if(model==null){details.setText("Weather is unavailable");glyph.condition(-1,true);}
        else{
            String feels=Double.isFinite(model.feels)?String.format(Locale.getDefault(),"Feels like %.0f°",model.feels):"";
            String humidity=Double.isFinite(model.humidity)?String.format(Locale.getDefault(),"Humidity %.0f%%",model.humidity):"";
            String wind=Double.isFinite(model.wind)?String.format(Locale.getDefault(),"Wind %.0f km/h",model.wind):"";
            details.setText(feels+(feels.isEmpty()||humidity.isEmpty()?"":" · ")+humidity+((feels.isEmpty()&&humidity.isEmpty())||wind.isEmpty()?"":"\n")+wind);
            glyph.condition(model.code,model.day);
        }
        glyph.active(active&&model!=null,prefs.getBoolean("weather_widget_animation",true));
        setContentDescription(name+". "+(model==null?"Weather unavailable":model.summary(fahrenheit))+". Open weather forecast");
    }
    void active(boolean value){active=value;glyph.active(value&& !"Unavailable".contentEquals(condition.getText()),prefs.getBoolean("weather_widget_animation",true));}
}
