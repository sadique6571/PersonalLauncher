package com.sadique.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;

/** Atmospheric sky behind readable weather; theme focus styling remains independent. */
final class WeatherWidgetView extends FrameLayout {
    private final Activity host;private final SharedPreferences prefs;
    private final TextView city,temperature,condition,details;
    private final WeatherConditionView scene;
    private boolean active;
    private WeatherForecast last;private String lastName;private boolean lastUnit;
    WeatherWidgetView(Activity host,Runnable open){
        super(host);this.host=host;prefs=ThemePreferences.get(host);setMinimumHeight(dp(184));setClipToOutline(true);
        GradientDrawable shape=new GradientDrawable();shape.setColor(0xff0c2035);shape.setCornerRadius(dp(22));setBackground(shape);
        scene=new WeatherConditionView(host);addView(scene,new LayoutParams(-1,-1));
        LinearLayout copy=new LinearLayout(host);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(14),dp(12),dp(14),dp(12));
        city=label("",12);city.setSingleLine();city.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(city);
        temperature=label("—",40);temperature.setTypeface(temperature.getTypeface(),Typeface.BOLD);temperature.setSingleLine();copy.addView(temperature);
        condition=label("Unavailable",13);condition.setSingleLine();condition.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(condition);
        details=label("",10);details.setMaxLines(2);LinearLayout.LayoutParams detailParams=new LinearLayout.LayoutParams(-1,-2);detailParams.topMargin=dp(7);copy.addView(details,detailParams);
        TextView hint=label("OK · Hourly / daily forecast",9);hint.setTextColor(0xffc8d9e6);hint.setPadding(0,dp(8),0,0);copy.addView(hint);
        addView(copy,new LayoutParams(-1,-2,Gravity.BOTTOM));
        setFocusable(true);setClickable(true);setForeground(edge(false));setOnFocusChangeListener((v,f)->setForeground(edge(f)));setOnClickListener(v->open.run());
        bind(null,prefs.getString("weather_city",LandscapeModel.CITY),prefs.getBoolean("weather_fahrenheit",false));
    }
    private int dp(float n){return GlassSettings.dp(host,n);}
    private TextView label(String text,int size){TextView t=GlassSettings.label(host,text,size,Color.WHITE);t.setShadowLayer(dp(3),0,dp(1),0xcc000000);return t;}
    private GradientDrawable edge(boolean focus){GradientDrawable d=new GradientDrawable();d.setColor(Color.TRANSPARENT);d.setCornerRadius(dp(22));d.setStroke(dp(focus?2:1),focus?ThemeStyle.focusAccent(prefs):0x55d5e4f5);return d;}
    void bind(WeatherForecast model,String name,boolean fahrenheit){
        if(model==last&&name.equals(lastName)&&fahrenheit==lastUnit)return;
        last=model;lastName=name;lastUnit=fahrenheit;
        city.setText(name);temperature.setText(model==null?"—":String.format(Locale.getDefault(),"%.0f°%s",model.temperature,fahrenheit?"F":"C"));condition.setText(model==null?"Unavailable":model.condition);
        if(model==null){details.setText("Weather is unavailable");scene.condition(-1,true);}
        else{
            String feels=Double.isFinite(model.feels)?String.format(Locale.getDefault(),"Feels like %.0f°",model.feels):"";
            String humidity=Double.isFinite(model.humidity)?String.format(Locale.getDefault(),"Humidity %.0f%%",model.humidity):"";
            String wind=Double.isFinite(model.wind)?String.format(Locale.getDefault(),"Wind %.0f km/h",model.wind):"";
            details.setText(feels+(feels.isEmpty()||humidity.isEmpty()?"":" · ")+humidity+((feels.isEmpty()&&humidity.isEmpty())||wind.isEmpty()?"":"\n")+wind);scene.condition(model.code,model.day);
        }
        scene.active(active&&model!=null,prefs.getBoolean("weather_widget_animation",true));setContentDescription(name+". "+(model==null?"Weather unavailable":model.summary(fahrenheit))+". Open weather forecast");
    }
    void active(boolean value){active=value;scene.active(value&&last!=null,prefs.getBoolean("weather_widget_animation",true));}
}
