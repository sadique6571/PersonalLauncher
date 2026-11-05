package com.sadique.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** App categories and read-only legacy asset routing; Home now has one editable identity. */
final class HomeLibraryStore {
    private final SharedPreferences prefs, registry;
    HomeLibraryStore(Context context) {
        this(ThemePreferences.get(context), context.getSharedPreferences("home_local_profiles", 0));
    }
    HomeLibraryStore(SharedPreferences prefs, SharedPreferences registry) { this.prefs = prefs; this.registry = registry; }
    String assetName(String name) {
        String id = registry.getString("active", "default");
        // Keep the current avatar/wallpaper on update, without exposing the removed profile system.
        return id.equals("default") ? name : "profile_" + id + "_" + name;
    }
    private JSONObject categoryMap() {
        try { return new JSONObject(prefs.getString("category_map", "{}")); }
        catch (org.json.JSONException ignored) { return new JSONObject(); }
    }
    String category(String pkg) { return categoryMap().optString(pkg, ""); }
    List<String> categories() {
        JSONObject map = categoryMap(); Set<String> names = new java.util.TreeSet<>();
        java.util.Iterator<String> keys = map.keys();
        while (keys.hasNext()) { String value = map.optString(keys.next()); if (!value.isEmpty()) names.add(value); }
        return new ArrayList<>(names);
    }
    void category(String pkg, String raw) {
        JSONObject map = categoryMap(); String name = HomeLibraryModel.title(raw);
        try { if (name.isEmpty()) map.remove(pkg); else map.put(pkg, name); }
        catch (org.json.JSONException ignored) { return; }
        prefs.edit().putString("category_map", map.toString()).apply();
    }
}
