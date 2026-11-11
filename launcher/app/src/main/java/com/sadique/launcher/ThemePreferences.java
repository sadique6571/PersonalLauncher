package com.sadique.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** Theme-owned appearance values, with shared identity, content, permissions and device setup. */
final class ThemePreferences implements SharedPreferences {
    static final String MIGRATED="appearance_profiles_v1";
    private static final String GLASS="appearance.glass.", CLASSIC="appearance.classic.";
    private static final Set<String> VISUAL=new HashSet<>(Arrays.asList(
            "weather_widget","weather_widget_animation","hijri_adjustment","glass_resume_v3","glass_reference_v2","glass_os_defaults_v1","harmony_clock_face","harmony_clock_finish","harmony_clock_accent","harmony_clock_sweep","harmony_clock_size","harmony_daily_quote","ambient_strength","demo_defaults_v1","app_label_size","material_defaults_v1","harmony_defaults_v1","theme_name","custom_accent","app_font","glass_alpha","glass_finish","glass_edge","panel_transparency",
            "clock_size","clock_style","clock_font","clock_align","time_24h","clock_seconds","clock_glow","clock_shadow","clock_wallpaper_v2","hide_clock",
            "hide_greeting","avatar_shape","header_bg_mode","header_bg","header_blur","header_glass_v2",
            "tv_columns","phone_columns","large_tiles","tile_corners","tile_spacing","logo_size","focus_border","focus_title_size","focus_glow","app_entrance",
            "background_dim","bottom_shade","theme_mode","daynight_smooth","row_spacing","banner_poster_size","banner_autoscroll",
            "continue_watching","row_netflix","row_youtube","apps_only","ss_clock_pos",
            "wallpaper_mode","wallpaper_source","wallpaper_slideshow","wallpaper_darkness","wallpaper_custom_id","icon_tiles","uniform_tiles"));
    private static volatile ThemePreferences instance;
    private final SharedPreferences base;
    private final Map<OnSharedPreferenceChangeListener,OnSharedPreferenceChangeListener> listeners=new IdentityHashMap<>();
    static SharedPreferences get(Context context) {
        ThemePreferences result=instance;
        if (result==null) synchronized(ThemePreferences.class) {
            result=instance;
            if (result==null) instance=result=new ThemePreferences(context.getApplicationContext().getSharedPreferences("personal_home",0));
        }
        return result;
    }
    ThemePreferences(SharedPreferences base) {
        this.base=base;
        if (!base.getBoolean(MIGRATED,false)) {
            String destination=prefix(); Editor editor=base.edit();
            for(Map.Entry<String,?> entry:base.getAll().entrySet())
                if(VISUAL.contains(entry.getKey()) && !base.contains(destination+entry.getKey()))
                    put(editor,destination+entry.getKey(),entry.getValue());
            editor.putBoolean(MIGRATED,true).apply();
        }
    }
    private String prefix() { return ThemeStyle.prefix(ThemeStyle.id(base)); }
    private String key(String key) { return VISUAL.contains(key)?prefix()+key:key; }
    private static boolean scoped(String key) {
        for(int style=0;style<ThemeStyle.COUNT;style++)
            if(key.startsWith(ThemeStyle.prefix(style))) return true;
        return false;
    }
    @Override public Map<String,?> getAll() {
        Map<String,?> snapshot=base.getAll(); Map<String,Object> all=new HashMap<>(snapshot);
        Object selected=snapshot.get("visual_style");
        String active=ThemeStyle.prefix(selected instanceof Integer?(Integer)selected:Boolean.FALSE.equals(snapshot.get("tv_glass_style"))?ThemeStyle.CLASSIC:ThemeStyle.GLASS);
        for(String name:VISUAL) all.remove(name); // Do not export stale pre-migration appearance values.
        for(Map.Entry<String,?> entry:snapshot.entrySet())
            if(entry.getKey().startsWith(active)) all.put(entry.getKey().substring(active.length()),entry.getValue());
        return all; // Retain all explicit namespaces so export/import preserves every theme.
    }
    @Override public String getString(String key,String fallback) { return base.getString(key(key),fallback); }
    @Override public Set<String> getStringSet(String key,Set<String> fallback) {
        Set<String> value=base.getStringSet(key(key),fallback); return value==null?null:new HashSet<>(value);
    }
    @Override public int getInt(String key,int fallback) { return base.getInt(key(key),fallback); }
    @Override public long getLong(String key,long fallback) { return base.getLong(key(key),fallback); }
    @Override public float getFloat(String key,float fallback) { return base.getFloat(key(key),fallback); }
    @Override public boolean getBoolean(String key,boolean fallback) { return base.getBoolean(key(key),fallback); }
    @Override public boolean contains(String key) { return base.contains(key(key)); }
    @Override public Editor edit() { return new Changes(prefix()); }
    @Override public synchronized void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        if(listeners.containsKey(listener)) return;
        OnSharedPreferenceChangeListener bridge=(prefs,key)-> {
            String active=prefix();
            if(key==null) listener.onSharedPreferenceChanged(this,null);
            else if(key.startsWith(active)) listener.onSharedPreferenceChanged(this,key.substring(active.length()));
            else if(!scoped(key) && !VISUAL.contains(key)) listener.onSharedPreferenceChanged(this,key);
        };
        listeners.put(listener,bridge); base.registerOnSharedPreferenceChangeListener(bridge);
    }
    @Override public synchronized void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        OnSharedPreferenceChangeListener bridge=listeners.remove(listener);
        if(bridge!=null) base.unregisterOnSharedPreferenceChangeListener(bridge);
    }
    private static void put(Editor editor,String key,Object value) {
        if(value==null) editor.remove(key);
        else if(value instanceof String) editor.putString(key,(String)value);
        else if(value instanceof Boolean) editor.putBoolean(key,(Boolean)value);
        else if(value instanceof Integer) editor.putInt(key,(Integer)value);
        else if(value instanceof Long) editor.putLong(key,(Long)value);
        else if(value instanceof Float) editor.putFloat(key,(Float)value);
        else if(value instanceof Set) {
            Set<String> copy=new HashSet<>(); for(Object item:(Set<?>)value) copy.add((String)item);
            editor.putStringSet(key,copy);
        }
    }
    private final class Changes implements Editor {
        private final String selected;
        private final Map<String,Object> pending=new LinkedHashMap<>();
        private boolean clear;
        Changes(String selected) { this.selected=selected; }
        private Editor value(String key,Object value) { pending.put(key,value); return this; }
        @Override public Editor putString(String key,String value) { return value(key,value); }
        @Override public Editor putStringSet(String key,Set<String> value) { return value(key,value==null?null:new HashSet<>(value)); }
        @Override public Editor putInt(String key,int value) { return value(key,value); }
        @Override public Editor putLong(String key,long value) { return value(key,value); }
        @Override public Editor putFloat(String key,float value) { return value(key,value); }
        @Override public Editor putBoolean(String key,boolean value) { return value(key,value); }
        @Override public Editor remove(String key) { return value(key,null); }
        @Override public Editor clear() { clear=true; return this; }
        private Editor changes() {
            String destination=selected;
            Object style=pending.get("visual_style"),legacy=pending.get("tv_glass_style");
            if(style instanceof Integer) destination=ThemeStyle.prefix(ThemeStyle.clamp((Integer)style));
            else if(legacy instanceof Boolean) destination=(Boolean)legacy?GLASS:CLASSIC;
            Editor editor=base.edit(); if(clear) editor.clear().putBoolean(MIGRATED,true);
            if(style instanceof Integer) editor.putBoolean("tv_glass_style",ThemeStyle.clamp((Integer)style)!=ThemeStyle.CLASSIC);
            else if(legacy instanceof Boolean) editor.putInt("visual_style",(Boolean)legacy?ThemeStyle.GLASS:ThemeStyle.CLASSIC);
            for(Map.Entry<String,Object> entry:pending.entrySet())
                put(editor,VISUAL.contains(entry.getKey())?destination+entry.getKey():entry.getKey(),entry.getValue());
            return editor;
        }
        @Override public boolean commit() { return changes().commit(); }
        @Override public void apply() { changes().apply(); }
    }
}
