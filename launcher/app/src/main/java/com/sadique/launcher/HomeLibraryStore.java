package com.sadique.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local, bounded profiles/categories/watchlist. Never changes remote services or Android accounts. */
final class HomeLibraryStore {
    private final SharedPreferences prefs, registry;
    private static final List<String> SCOPED = Arrays.asList("name", "app_order", "hidden_apps",
            "theme_name", "custom_accent", "theme_mode", "wallpaper_mode", "wallpaper_source",
            "background_dim", "app_font", "avatar_shape", "header_bg_mode", "home_focus_pkg", "category_map", "watchlist_items");
    HomeLibraryStore(Context ctx) {
        this(ctx.getSharedPreferences("personal_home", 0), ctx.getSharedPreferences("home_local_profiles", 0));
    }
    HomeLibraryStore(SharedPreferences prefs, SharedPreferences registry) {
        this.prefs = prefs; this.registry = registry;
    }
    String activeId() { return registry.getString("active", "default"); }
    String assetName(String name) { return activeId().equals("default") ? name : "profile_" + activeId() + "_" + name; }
    List<String> profileIds() {
        List<String> ids = new ArrayList<>(); ids.add("default");
        for (String id : registry.getStringSet("ids", Collections.emptySet())) if (!id.equals("default")) ids.add(id);
        Collections.sort(ids.subList(1, ids.size())); return ids;
    }
    String profileName(String id) {
        if (id.equals(activeId())) return prefs.getString("name", "Personal");
        return registry.getString("label_" + id, id.equals("default") ? "Personal" : "Profile");
    }
    private JSONObject snapshot() {
        JSONObject json = new JSONObject();
        try {
            for (String key : SCOPED) {
                Object value = prefs.getAll().get(key);
                if (value instanceof Set) value = new JSONArray(new ArrayList<>((Set<?>) value));
                if (value != null) json.put(key, value);
            }
        } catch (org.json.JSONException ignored) { }
        return json;
    }
    void switchTo(String id) {
        if (id.equals(activeId()) || !profileIds().contains(id)) return;
        registry.edit().putString("data_" + activeId(), snapshot().toString())
                .putString("label_" + activeId(), prefs.getString("name", "Personal")).apply();
        JSONObject next;
        try { next = new JSONObject(registry.getString("data_" + id, "{}")); }
        catch (org.json.JSONException malformed) { next = new JSONObject(); }
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : SCOPED) {
            edit.remove(key);
            Object value = next.opt(key);
            if (value instanceof JSONArray && key.equals("hidden_apps")) {
                Set<String> values = new HashSet<>(); JSONArray array = (JSONArray) value;
                for (int i = 0; i < array.length(); i++) values.add(array.optString(i));
                edit.putStringSet(key, values);
            } else if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
        }
        edit.apply(); registry.edit().putString("active", id).apply();
    }
    boolean createProfile(String raw) {
        String name = HomeLibraryModel.title(raw);
        if (name.isEmpty() || profileIds().size() >= 6) return false;
        String id = java.util.UUID.randomUUID().toString();
        JSONObject data = snapshot();
        try { data.put("name", name); data.remove("watchlist_items"); data.remove("category_map"); data.remove("hidden_apps"); data.put("wallpaper_mode", "mountains"); data.put("wallpaper_source", 0); }
        catch (org.json.JSONException ignored) { return false; }
        Set<String> ids = new HashSet<>(registry.getStringSet("ids", Collections.emptySet())); ids.add(id);
        registry.edit().putStringSet("ids", ids).putString("data_" + id, data.toString()).putString("label_" + id, name).apply();
        switchTo(id); return true;
    }
    void deleteProfile(String id) {
        if (id.equals("default")) return;
        if (id.equals(activeId())) switchTo("default");
        Set<String> ids = new HashSet<>(registry.getStringSet("ids", Collections.emptySet())); ids.remove(id);
        registry.edit().putStringSet("ids", ids).remove("data_" + id).remove("label_" + id).apply();
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
    JSONArray watchlist() {
        try { return new JSONArray(prefs.getString("watchlist_items", "[]")); }
        catch (org.json.JSONException ignored) { return new JSONArray(); }
    }
    boolean saveTitle(String title, String uri, String owner, boolean sharedIntent) {
        title = HomeLibraryModel.title(title);
        if (title.isEmpty() || uri == null || uri.length() > 8192 || (!sharedIntent && !HomeLibraryModel.allowedLink(uri))) return false;
        JSONArray before = watchlist(), out = new JSONArray();
        try {
            JSONObject item = new JSONObject().put("title", title).put("uri", uri).put("owner", owner == null ? "" : owner).put("shared", sharedIntent);
            out.put(item);
            for (int i = 0; i < before.length() && out.length() < 50; i++) {
                JSONObject old = before.optJSONObject(i);
                if (old != null && !uri.equals(old.optString("uri"))) out.put(old);
            }
        } catch (org.json.JSONException ignored) { return false; }
        prefs.edit().putString("watchlist_items", out.toString()).apply(); return true;
    }
    void removeTitle(String uri) {
        JSONArray before = watchlist(), out = new JSONArray();
        for (int i = 0; i < before.length(); i++) {
            JSONObject item = before.optJSONObject(i);
            if (item != null && !uri.equals(item.optString("uri"))) out.put(item);
        }
        prefs.edit().putString("watchlist_items", out.toString()).apply();
    }
}
