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
    @Test public void transparencyIsIndependentAndDrawerNeverBecomesSolid() {
        ThemePreferences prefs=new ThemePreferences(new Memory());
        assertTrue(PanelTransparency.enabled(prefs));
        prefs.edit().putInt("glass_alpha",30).putInt("glass_finish",1).putBoolean("panel_transparency",false).apply();
        for(int alpha:new int[]{0,24,70,150,245}) {
            assertEquals(255,PanelTransparency.alpha(alpha,prefs,false));
            assertEquals(alpha,PanelTransparency.alpha(alpha,prefs,true));
        }
        prefs.edit().putBoolean("tv_glass_style",false).apply();
        assertTrue(PanelTransparency.enabled(prefs));
        prefs.edit().putInt("glass_alpha",70).putBoolean("panel_transparency",false).apply();
        assertEquals(255,PanelTransparency.alpha(ClassicGlassAlpha.card(70,false),prefs,false));
        assertEquals(49,PanelTransparency.alpha(ClassicGlassAlpha.drawer(70),prefs,true));
        prefs.edit().putBoolean("panel_transparency",true).apply();
        assertEquals(105,PanelTransparency.alpha(ClassicGlassAlpha.card(70,false),prefs,false));
        prefs.edit().putBoolean("tv_glass_style",true).apply();
        assertFalse(PanelTransparency.enabled(prefs));
        assertEquals(30,prefs.getInt("glass_alpha",0)); assertEquals(1,prefs.getInt("glass_finish",0));
        prefs.edit().putBoolean("panel_transparency",true).apply();
        assertEquals(24,PanelTransparency.alpha(24,prefs,false));
    }
    @Test public void missingHeaderGlassReturnsOnceAndExplicitOffSurvivesThemeChanges() {
        ThemePreferences prefs=new ThemePreferences(new Memory());
        prefs.edit().putInt("header_bg_mode",0).apply();
        assertEquals(1,HeaderAppearance.mode(prefs));
        prefs.edit().putInt("header_bg_mode",0).putInt("avatar_shape",1).apply();
        assertEquals(0,HeaderAppearance.mode(prefs));
        prefs.edit().putBoolean("tv_glass_style",false).apply();
        assertEquals(1,HeaderAppearance.mode(prefs));
        assertEquals(0,prefs.getInt("avatar_shape",0));
        prefs.edit().putInt("header_bg_mode",2).apply();
        assertEquals(2,HeaderAppearance.mode(prefs));
        prefs.edit().putBoolean("tv_glass_style",true).apply();
        assertEquals(0,HeaderAppearance.mode(prefs));
        assertEquals(1,prefs.getInt("avatar_shape",0));
    }
    @Test public void edgeSettingsRetainDistinctStrengthsInBothThemes() {
        ThemePreferences prefs=new ThemePreferences(new Memory());
        for (boolean glass:new boolean[]{true,false}) {
            prefs.edit().putBoolean("tv_glass_style",glass).apply();
            int last=-1;
            for(int edge=0;edge<3;edge++) {
                prefs.edit().putInt("glass_edge",edge).apply();
                int alpha=TvGlassStyle.edge(prefs)>>>24;
                assertTrue(alpha>last); last=alpha;
            }
        }
    }
    @Test public void allFourThemesRestoreTheirOwnAppearanceWithoutSplittingIdentityOrWallpaper() {
        ThemePreferences prefs=new ThemePreferences(new Memory());
        prefs.edit().putString("name","Profile").putInt("wallpaper_source",1).apply();
        for(int style=0;style<4;style++) {
            prefs.edit().putInt("visual_style",style).putInt("glass_alpha",20+style*20)
                    .putInt("avatar_shape",style%2).putBoolean("panel_transparency",style%2==0).apply();
        }
        for(int style=3;style>=0;style--) {
            prefs.edit().putInt("visual_style",style).apply();
            assertEquals(style,ThemeStyle.id(prefs));
            assertEquals(20+style*20,prefs.getInt("glass_alpha",0));
            assertEquals(style%2,prefs.getInt("avatar_shape",0));
            assertEquals(style%2==0,PanelTransparency.enabled(prefs));
            assertEquals("Profile",prefs.getString("name","")); assertEquals(1,prefs.getInt("wallpaper_source",0));
        }
    }
    @Test public void newStyleBackupRestoresSelectedNamespaceRegardlessOfLegacyBoolean() {
        ThemePreferences prefs=new ThemePreferences(new Memory());
        prefs.edit().putInt("visual_style",2).putInt("glass_alpha",31).apply();
        prefs.edit().putInt("visual_style",3).putInt("glass_alpha",67).apply();
        Map<String,?> backup=prefs.getAll();
        assertEquals(31,backup.get("appearance.dune.glass_alpha"));
        assertEquals(67,backup.get("appearance.pearl.glass_alpha"));
        ThemePreferences restored=new ThemePreferences(new Memory()); SharedPreferences.Editor editor=restored.edit();
        for(Map.Entry<String,?> entry:backup.entrySet()) {
            if(entry.getValue() instanceof Boolean) editor.putBoolean(entry.getKey(),(Boolean)entry.getValue());
            if(entry.getValue() instanceof Integer) editor.putInt(entry.getKey(),(Integer)entry.getValue());
        }
        editor.apply(); assertEquals(3,ThemeStyle.id(restored)); assertEquals(67,restored.getInt("glass_alpha",0));
        restored.edit().putInt("visual_style",2).apply(); assertEquals(31,restored.getInt("glass_alpha",0));
    }
    @Test public void staleEditorsAndInactiveNewThemeChangesDoNotLeakIntoCurrentTheme() {
        Memory base=new Memory(); ThemePreferences prefs=new ThemePreferences(base);
        prefs.edit().putInt("visual_style",2).apply();
        SharedPreferences.Editor dune=prefs.edit().putInt("glass_alpha",42);
        prefs.edit().putInt("visual_style",3).apply(); dune.apply();
        assertEquals(70,prefs.getInt("glass_alpha",70));
        List<String> changes=new ArrayList<>(); prefs.registerOnSharedPreferenceChangeListener((p,k)->changes.add(k));
        base.edit().putInt("appearance.dune.glass_alpha",55).apply(); assertTrue(changes.isEmpty());
        prefs.edit().putInt("glass_alpha",80).apply(); assertTrue(changes.contains("glass_alpha"));
        prefs.edit().putInt("visual_style",2).apply(); assertEquals(55,prefs.getInt("glass_alpha",0));
    }
    @Test public void newThemePaletteHasReadableTextAndLegacySelectorRemainsCompatible() {
        ThemePreferences prefs=new ThemePreferences(new Memory());
        for(int style:new int[]{ThemeStyle.DUNE,ThemeStyle.PEARL}) {
            prefs.edit().putInt("visual_style",style).apply();
            int background=style==ThemeStyle.PEARL?0xffe9e4dc:0xff111e24;
            double a=ThemeStyle.luminance(background),b=ThemeStyle.luminance(ThemeStyle.foreground(prefs));
            assertTrue((Math.max(a,b)+.05)/(Math.min(a,b)+.05)>7);
            assertTrue(ThemeStyle.custom(prefs));
            assertEquals(style==ThemeStyle.PEARL,ThemeStyle.pearl(prefs));
        }
        prefs.edit().putBoolean("tv_glass_style",false).apply(); assertEquals(ThemeStyle.CLASSIC,ThemeStyle.id(prefs));
        prefs.edit().putBoolean("tv_glass_style",true).apply(); assertEquals(ThemeStyle.GLASS,ThemeStyle.id(prefs));
    }
    @Test public void customBlackOrWhiteAccentCannotHideMaterialFocusIndicator() {
        ThemePreferences prefs=new ThemePreferences(new Memory());
        for(int style:new int[]{ThemeStyle.DUNE,ThemeStyle.PEARL}) for(int color:new int[]{0xff000000,0xffffffff,0xffedab75,0xff006d72}) {
            prefs.edit().putInt("visual_style",style).putInt("theme_name",3).putInt("custom_accent",color).apply();
            int background=style==ThemeStyle.PEARL?0xffe9e4dc:0xff111e24;
            double a=ThemeStyle.luminance(background),b=ThemeStyle.luminance(ThemeStyle.focusAccent(prefs));
            assertTrue((Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=3);
        }
    }
    @Test public void bothMaterialsHonorFontClockAlignmentAndPreserveAccentIdentity() {
        ThemePreferences prefs=new ThemePreferences(new Memory());
        for(int style:new int[]{ThemeStyle.DUNE,ThemeStyle.PEARL}) {
            prefs.edit().putInt("visual_style",style).putInt("app_font",1).putInt("clock_font",2).putInt("clock_align",0).apply();
            assertFalse(ThemeStyle.fixedTypography(prefs));
            assertEquals("serif",ThemeStyle.fontFamily(prefs));
            assertEquals("monospace",ThemeStyle.clockFontFamily(prefs));
            assertEquals(0,ThemeStyle.clockAlignment(prefs));
            prefs.edit().putInt("clock_align",2).apply(); assertEquals(2,ThemeStyle.clockAlignment(prefs));
            Set<Integer> accents=new HashSet<>();
            for(int color:new int[]{0xffff5252,0xffffea00,0xff69f0ae,0xff508dff}) {
                prefs.edit().putInt("theme_name",3).putInt("custom_accent",color).apply();
                assertEquals(color,ThemeStyle.accent(prefs)); accents.add(ThemeStyle.focusAccent(prefs));
            }
            assertEquals(4,accents.size());
            prefs.edit().putBoolean("panel_transparency",false).apply();
            assertEquals(255,PanelTransparency.alpha(MaterialAppearance.alpha(0,30,false,false),prefs,false));
            assertEquals(MaterialAppearance.alpha(0,30,true,false),PanelTransparency.alpha(MaterialAppearance.alpha(0,30,true,false),prefs,true));
        }
        prefs.edit().putInt("visual_style",ThemeStyle.GLASS).putInt("clock_align",0).apply();
        assertTrue(ThemeStyle.fixedTypography(prefs)); assertEquals(1,ThemeStyle.clockAlignment(prefs));
    }
    @Test public void solidPanelToggleCannotChangeDrawerPanelOrInnerCardOpacityInAnyStyle() {
        ThemePreferences prefs=new ThemePreferences(new Memory());
        for(int style=0;style<4;style++) {
            prefs.edit().putInt("visual_style",style).apply();
            for(int finish=0;finish<3;finish++) for(boolean focused:new boolean[]{false,true}) {
                int glass=MaterialAppearance.alpha(finish,70,true,focused);
                prefs.edit().putBoolean("panel_transparency",true).apply();
                int enabled=PanelTransparency.alpha(glass,prefs,true);
                prefs.edit().putBoolean("panel_transparency",false).apply();
                assertEquals(enabled,PanelTransparency.alpha(glass,prefs,true));
                assertEquals(255,PanelTransparency.alpha(glass,prefs,false));
            }
        }
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
