package com.sadique.launcher;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeLibraryStoreTest {
    private final MemoryPrefs home = new MemoryPrefs(), registry = new MemoryPrefs();
    private final HomeLibraryStore store = new HomeLibraryStore(home, registry);
    @Test public void updatePreservesExistingPicturesWithoutSwitchingProfiles() {
        assertEquals("personal-avatar.png", store.assetName("personal-avatar.png"));
        registry.edit().putString("active", "existing-id").apply();
        assertEquals("profile_existing-id_personal-avatar.png", store.assetName("personal-avatar.png"));
        assertEquals("profile_existing-id_personal-wallpaper.jpg", store.assetName("personal-wallpaper.jpg"));
        assertEquals("existing-id", registry.getString("active", ""));
    }
    @Test public void categoriesCanBeRenamedAndClearedWithoutChangingOtherApps() {
        store.category("video", "Videos"); store.category("tool", "Tools"); store.category("video", "Family");
        assertEquals("Family", store.category("video")); assertEquals("Tools", store.category("tool"));
        store.category("video", ""); assertEquals("", store.category("video")); assertEquals(1, store.categories().size());
    }
    /** In-memory preferences: tests exercise production persistence without Android framework calls. */
    private static final class MemoryPrefs implements SharedPreferences {
        private final Map<String,Object> data = new HashMap<>();
        @Override public Map<String,?> getAll() { return new HashMap<>(data); }
        @Override public String getString(String k, String d) { return data.containsKey(k) ? (String)data.get(k) : d; }
        @Override @SuppressWarnings("unchecked") public Set<String> getStringSet(String k, Set<String> d) { return data.containsKey(k) ? new HashSet<>((Set<String>)data.get(k)) : d; }
        @Override public int getInt(String k, int d) { return data.containsKey(k) ? (Integer)data.get(k) : d; }
        @Override public long getLong(String k, long d) { return data.containsKey(k) ? (Long)data.get(k) : d; }
        @Override public float getFloat(String k, float d) { return data.containsKey(k) ? (Float)data.get(k) : d; }
        @Override public boolean getBoolean(String k, boolean d) { return data.containsKey(k) ? (Boolean)data.get(k) : d; }
        @Override public boolean contains(String k) { return data.containsKey(k); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public Editor edit() {
            return new Editor() {
                private final Map<String,Object> updates = new HashMap<>(); private boolean clear;
                @Override public Editor putString(String k,String v) { updates.put(k,v); return this; }
                @Override public Editor putStringSet(String k,Set<String> v) { updates.put(k,new HashSet<>(v)); return this; }
                @Override public Editor putInt(String k,int v) { updates.put(k,v); return this; }
                @Override public Editor putLong(String k,long v) { updates.put(k,v); return this; }
                @Override public Editor putFloat(String k,float v) { updates.put(k,v); return this; }
                @Override public Editor putBoolean(String k,boolean v) { updates.put(k,v); return this; }
                @Override public Editor remove(String k) { updates.put(k,null); return this; }
                @Override public Editor clear() { clear=true; return this; }
                @Override public boolean commit() { apply(); return true; }
                @Override public void apply() { if(clear)data.clear(); for(Map.Entry<String,Object> e:updates.entrySet()) { if(e.getValue()==null)data.remove(e.getKey()); else data.put(e.getKey(),e.getValue()); } }
            };
        }
    }
}
