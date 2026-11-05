package com.sadique.launcher;

import android.content.SharedPreferences;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ThemePreferencesTest {
    @Test public void migrationPreservesActiveAppearanceAndDoesNotCopyItToOtherTheme() {
        for(boolean active:new boolean[]{true,false}) {
            Memory base=new Memory(); base.edit().putBoolean("tv_glass_style",active).putInt("glass_alpha",33).putString("weather_city","Okara").apply();
            ThemePreferences prefs=new ThemePreferences(base);
            assertEquals(33,prefs.getInt("glass_alpha",70));
            prefs.edit().putBoolean("tv_glass_style",!active).apply();
            assertEquals(70,prefs.getInt("glass_alpha",70));
            prefs.edit().putInt("glass_alpha",90).putInt("clock_size",42).apply();
            prefs.edit().putBoolean("tv_glass_style",active).apply();
            assertEquals(33,prefs.getInt("glass_alpha",70)); assertEquals(52,prefs.getInt("clock_size",52));
            assertEquals("Okara",prefs.getString("weather_city",""));
        }
    }
    @Test public void returningToAThemeRestoresColourFontAndLayoutAndResetIsLocal() {
        ThemePreferences prefs=new ThemePreferences(new Memory());
        prefs.edit().putInt("custom_accent",12).putInt("app_font",1).putInt("tv_columns",4).apply();
        prefs.edit().putBoolean("tv_glass_style",false).apply();
        prefs.edit().putInt("custom_accent",34).putInt("app_font",2).putInt("tv_columns",6).apply();
        prefs.edit().remove("tv_columns").apply(); assertEquals(0,prefs.getInt("tv_columns",0));
        prefs.edit().putBoolean("tv_glass_style",true).apply();
        assertEquals(12,prefs.getInt("custom_accent",0)); assertEquals(1,prefs.getInt("app_font",0)); assertEquals(4,prefs.getInt("tv_columns",0));
        prefs.edit().putBoolean("tv_glass_style",false).apply(); assertEquals(34,prefs.getInt("custom_accent",0));
    }
    @Test public void editorKeepsItsThemeEvenIfAnotherOperationSwitchesBeforeApply() {
        ThemePreferences prefs=new ThemePreferences(new Memory());
        SharedPreferences.Editor glass=prefs.edit().putInt("glass_alpha",20);
        prefs.edit().putBoolean("tv_glass_style",false).apply(); glass.apply();
        assertEquals(70,prefs.getInt("glass_alpha",70));
        prefs.edit().putBoolean("tv_glass_style",true).apply(); assertEquals(20,prefs.getInt("glass_alpha",70));
    }
    @Test public void backupContainsBothThemesAndRestoreTargetsExplicitSelectedTheme() {
        ThemePreferences prefs=new ThemePreferences(new Memory());
        prefs.edit().putInt("glass_alpha",25).apply(); prefs.edit().putBoolean("tv_glass_style",false).putInt("glass_alpha",85).apply();
        Map<String,?> backup=prefs.getAll();
        assertEquals(25,backup.get("appearance.glass.glass_alpha")); assertEquals(85,backup.get("appearance.classic.glass_alpha"));
        ThemePreferences restored=new ThemePreferences(new Memory()); SharedPreferences.Editor editor=restored.edit();
        for(Map.Entry<String,?> e:backup.entrySet()) {
            if(e.getValue() instanceof Boolean) editor.putBoolean(e.getKey(),(Boolean)e.getValue());
            else if(e.getValue() instanceof Integer) editor.putInt(e.getKey(),(Integer)e.getValue());
        }
        assertTrue(editor.commit()); assertEquals(85,restored.getInt("glass_alpha",0));
        restored.edit().putBoolean("tv_glass_style",true).apply(); assertEquals(25,restored.getInt("glass_alpha",0));
    }
    @Test public void listenersMapActiveKeysAndSharedIdentityDoesNotSplit() {
        Memory base=new Memory(); ThemePreferences prefs=new ThemePreferences(base); List<String> changed=new ArrayList<>();
        SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)->changed.add(k); prefs.registerOnSharedPreferenceChangeListener(listener);
        base.edit().putInt("appearance.classic.glass_alpha",90).apply(); assertTrue(changed.isEmpty());
        prefs.edit().putInt("glass_alpha",30).putString("name","Person").apply(); assertTrue(changed.contains("glass_alpha"));
        prefs.edit().putBoolean("tv_glass_style",false).apply(); assertEquals("Person",prefs.getString("name",""));
        int count=changed.size(); prefs.unregisterOnSharedPreferenceChangeListener(listener); prefs.edit().putInt("glass_alpha",40).apply(); assertEquals(count,changed.size());
    }
    private static final class Memory implements SharedPreferences {
        final Map<String,Object> data=new HashMap<>(); final Set<OnSharedPreferenceChangeListener> listeners=new HashSet<>();
        public Map<String,?> getAll(){return new HashMap<>(data);}
        public String getString(String k,String d){return data.containsKey(k)?(String)data.get(k):d;}
        @SuppressWarnings("unchecked") public Set<String> getStringSet(String k,Set<String> d){return data.containsKey(k)?new HashSet<>((Set<String>)data.get(k)):d;}
        public int getInt(String k,int d){return data.containsKey(k)?(Integer)data.get(k):d;}
        public long getLong(String k,long d){return data.containsKey(k)?(Long)data.get(k):d;}
        public float getFloat(String k,float d){return data.containsKey(k)?(Float)data.get(k):d;}
        public boolean getBoolean(String k,boolean d){return data.containsKey(k)?(Boolean)data.get(k):d;}
        public boolean contains(String k){return data.containsKey(k);}
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.add(l);}
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.remove(l);}
        public Editor edit(){return new Editor(){
            final Map<String,Object> pending=new LinkedHashMap<>();boolean clear;
            Editor add(String k,Object v){pending.put(k,v);return this;}
            public Editor putString(String k,String v){return add(k,v);} public Editor putStringSet(String k,Set<String> v){return add(k,v==null?null:new HashSet<>(v));}
            public Editor putInt(String k,int v){return add(k,v);} public Editor putLong(String k,long v){return add(k,v);}
            public Editor putFloat(String k,float v){return add(k,v);} public Editor putBoolean(String k,boolean v){return add(k,v);}
            public Editor remove(String k){return add(k,null);} public Editor clear(){clear=true;return this;}
            public boolean commit(){apply();return true;} public void apply(){
                if(clear)data.clear();for(Map.Entry<String,Object> e:pending.entrySet()){if(e.getValue()==null)data.remove(e.getKey());else data.put(e.getKey(),e.getValue());}
                for(String k:pending.keySet())for(OnSharedPreferenceChangeListener l:new HashSet<>(listeners))l.onSharedPreferenceChanged(Memory.this,k);
            }
        };}
    }
}
