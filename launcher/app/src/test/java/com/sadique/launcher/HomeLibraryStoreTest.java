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
    @Test public void profileSwitchIsolatesLayoutAndDoesNotResetGlobalPreferences() {
        home.edit().putString("name", "Original").putString("app_order", "[\"one\",\"two\"]")
                .putBoolean("home_long_press_home", true).putInt("perf_mode", 1)
                .putStringSet("hidden_apps", Collections.singleton("private")).apply();
        store.category("video", "Videos");
        assertTrue(store.createProfile("Family")); String family = store.activeId();
        assertEquals("Family", home.getString("name", ""));
        assertTrue(home.getStringSet("hidden_apps", Collections.emptySet()).isEmpty());
        assertEquals("", store.category("video"));
        home.edit().putString("app_order", "[\"two\",\"one\"]").putInt("theme_name", 2).apply();
        store.switchTo("default");
        assertEquals("Original", home.getString("name", "")); assertEquals("[\"one\",\"two\"]", home.getString("app_order", ""));
        assertTrue(home.getStringSet("hidden_apps", Collections.emptySet()).contains("private"));
        assertTrue(home.getBoolean("home_long_press_home", false)); assertEquals(1, home.getInt("perf_mode", 0));
        assertEquals("Videos", store.category("video"));
        store.switchTo(family); assertEquals(2, home.getInt("theme_name", 0));
    }
    @Test public void profileAssetsPreserveExistingDefaultFilesAndSeparateNewPictures() {
        assertEquals("personal-avatar.png", store.assetName("personal-avatar.png"));
        assertTrue(store.createProfile("Other"));
        assertTrue(store.assetName("personal-avatar.png").startsWith("profile_"));
        store.deleteProfile(store.activeId()); assertEquals("default", store.activeId());
        assertEquals("personal-avatar.png", store.assetName("personal-avatar.png"));
    }
    @Test public void profilesAreBoundedAndUnknownProfilesAreIgnored() {
        assertFalse(store.createProfile(" "));
        for (int i = 0; i < 5; i++) assertTrue(store.createProfile("Profile " + i));
        assertFalse(store.createProfile("Seventh"));
        String active = store.activeId(); store.switchTo("not-registered"); assertEquals(active, store.activeId());
        store.deleteProfile("default"); assertTrue(store.profileIds().contains("default"));
    }
    @Test public void categoriesCanBeRenamedAndClearedWithoutChangingOtherApps() {
        store.category("video", "Videos"); store.category("tool", "Tools"); store.category("video", "Family");
        assertEquals("Family", store.category("video")); assertEquals("Tools", store.category("tool"));
        store.category("video", ""); assertEquals("", store.category("video")); assertEquals(1, store.categories().size());
    }
    @Test public void watchlistIsBoundedDeduplicatedAndLocalToProfile() {
        assertFalse(store.saveTitle("", "https://example.com", "", false));
        assertFalse(store.saveTitle("Movie", "file:///secret", "", false));
        for (int i = 0; i < 70; i++) assertTrue(store.saveTitle("Movie " + i, "https://example.com/" + i, "", false));
        assertEquals(50, store.watchlist().length());
        assertTrue(store.saveTitle("Updated", "https://example.com/69", "", false));
        assertEquals(50, store.watchlist().length()); assertEquals("Updated", store.watchlist().optJSONObject(0).optString("title"));
        store.removeTitle("https://example.com/69"); assertEquals(49, store.watchlist().length());
        assertTrue(store.createProfile("Family")); assertEquals(0, store.watchlist().length());
        store.switchTo("default"); assertEquals(49, store.watchlist().length());
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
