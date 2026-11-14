package com.sadique.launcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject
import java.util.TreeSet

/** App categories and the asset paths retained for existing installations. */
class HomeLibraryStore(private val prefs: SharedPreferences, private val registry: SharedPreferences) {
    constructor(context: Context) : this(
        ThemePreferences.get(context),
        context.getSharedPreferences("home_local_profiles", Context.MODE_PRIVATE)
    )

    fun assetName(name: String): String {
        val id = registry.getString("active", "default")
        return if (id == "default") name else "profile_${id}_$name"
    }

    private fun categoryMap(): JSONObject {
        return try {
            JSONObject(prefs.getString("category_map", "{}"))
        } catch (_: JSONException) {
            JSONObject()
        }
    }

    fun category(pkg: String?): String = categoryMap().optString(pkg, "")

    fun categories(): MutableList<String> {
        val map = categoryMap()
        val names = TreeSet<String>()
        for (key in map.keys()) {
            val value = map.optString(key)
            if (value.isNotEmpty()) names.add(value)
        }
        return ArrayList(names)
    }

    fun category(pkg: String?, raw: String?) {
        val map = categoryMap()
        val name = HomeLibraryModel.title(raw)
        try {
            if (name.isEmpty()) map.remove(pkg) else map.put(pkg, name)
        } catch (_: JSONException) {
            return
        }
        prefs.edit().putString("category_map", map.toString()).apply()
    }
}
