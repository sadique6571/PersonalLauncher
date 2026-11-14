package com.sadique.launcher

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import java.util.IdentityHashMap

/** Stores each theme's look alongside shared identity, content and device setup. */
class ThemePreferences(private val base: SharedPreferences) : SharedPreferences {
    private val listeners = IdentityHashMap<OnSharedPreferenceChangeListener, OnSharedPreferenceChangeListener>()

    init {
        if (!base.getBoolean(MIGRATED, false)) {
            val destination = prefix()
            val editor = base.edit()
            for ((key, value) in base.all) {
                if (key in VISUAL && !base.contains(destination + key)) put(editor, destination + key, value)
            }
            editor.putBoolean(MIGRATED, true).apply()
        }
    }

    private fun prefix(): String = ThemeStyle.prefix(ThemeStyle.id(base))
    private fun scopedKey(key: String): String = if (key in VISUAL) prefix() + key else key

    override fun getAll(): Map<String, *> {
        val snapshot = base.all
        val values = HashMap(snapshot)
        val selected = snapshot["visual_style"] as? Int
        val fallback = if (snapshot["tv_glass_style"] == false) ThemeStyle.CLASSIC else ThemeStyle.GLASS
        val active = ThemeStyle.prefix(selected ?: fallback)
        for (key in VISUAL) values.remove(key)
        for ((key, value) in snapshot) {
            if (key.startsWith(active)) values[key.removePrefix(active)] = value
        }
        return values
    }

    override fun getString(key: String, defValue: String?): String? = base.getString(scopedKey(key), defValue)

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        return base.getStringSet(scopedKey(key), defValues)?.let(::HashSet)
    }

    override fun getInt(key: String, defValue: Int): Int = base.getInt(scopedKey(key), defValue)
    override fun getLong(key: String, defValue: Long): Long = base.getLong(scopedKey(key), defValue)
    override fun getFloat(key: String, defValue: Float): Float = base.getFloat(scopedKey(key), defValue)
    override fun getBoolean(key: String, defValue: Boolean): Boolean = base.getBoolean(scopedKey(key), defValue)
    override fun contains(key: String): Boolean = base.contains(scopedKey(key))
    override fun edit(): Editor = Changes(prefix())

    @Synchronized
    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        if (listeners.containsKey(listener)) return
        val bridge = OnSharedPreferenceChangeListener { _, key ->
            val active = prefix()
            when {
                key == null -> listener.onSharedPreferenceChanged(this, null)
                key.startsWith(active) -> listener.onSharedPreferenceChanged(this, key.removePrefix(active))
                !scoped(key) && key !in VISUAL -> listener.onSharedPreferenceChanged(this, key)
            }
        }
        listeners[listener] = bridge
        base.registerOnSharedPreferenceChangeListener(bridge)
    }

    @Synchronized
    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        val bridge = listeners.remove(listener) ?: return
        base.unregisterOnSharedPreferenceChangeListener(bridge)
    }

    private inner class Changes(private val selected: String) : Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private var clear = false

        private fun value(key: String, value: Any?): Editor {
            pending[key] = value
            return this
        }

        override fun putString(key: String, value: String?): Editor = value(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?): Editor = value(key, values?.let(::HashSet))
        override fun putInt(key: String, value: Int): Editor = value(key, value)
        override fun putLong(key: String, value: Long): Editor = value(key, value)
        override fun putFloat(key: String, value: Float): Editor = value(key, value)
        override fun putBoolean(key: String, value: Boolean): Editor = value(key, value)
        override fun remove(key: String): Editor = value(key, null)
        override fun clear(): Editor {
            clear = true
            return this
        }

        private fun changes(): Editor {
            val style = pending["visual_style"] as? Int
            val legacy = pending["tv_glass_style"] as? Boolean
            val destination = when {
                style != null -> ThemeStyle.prefix(style)
                legacy != null -> if (legacy) GLASS else CLASSIC
                else -> selected
            }
            val editor = base.edit()
            if (clear) editor.clear().putBoolean(MIGRATED, true)
            if (style != null) editor.putBoolean("tv_glass_style", ThemeStyle.clamp(style) != ThemeStyle.CLASSIC)
            else if (legacy != null) editor.putInt("visual_style", if (legacy) ThemeStyle.GLASS else ThemeStyle.CLASSIC)
            for ((key, value) in pending) {
                put(editor, if (key in VISUAL) destination + key else key, value)
            }
            return editor
        }

        override fun commit(): Boolean = changes().commit()
        override fun apply() = changes().apply()
    }

    companion object {
        const val MIGRATED = "appearance_profiles_v1"
        private const val GLASS = "appearance.glass."
        private const val CLASSIC = "appearance.classic."
        private val VISUAL = setOf(
            "weather_widget", "weather_widget_animation", "hijri_adjustment", "glass_resume_v3", "glass_reference_v2",
            "glass_os_defaults_v1", "harmony_clock_face", "harmony_clock_finish", "harmony_clock_accent", "harmony_clock_sweep",
            "harmony_clock_size", "harmony_daily_quote", "ambient_strength", "demo_defaults_v1", "app_label_size",
            "material_defaults_v1", "harmony_defaults_v1", "theme_name", "custom_accent", "app_font",
            "glass_alpha", "glass_finish", "glass_edge", "panel_transparency", "clock_size",
            "clock_style", "clock_font", "clock_align", "time_24h", "clock_seconds",
            "clock_glow", "clock_shadow", "clock_wallpaper_v2", "hide_clock", "hide_greeting",
            "avatar_shape", "header_bg_mode", "header_bg", "header_blur", "header_glass_v2",
            "tv_columns", "phone_columns", "large_tiles", "tile_corners", "tile_spacing",
            "logo_size", "focus_border", "focus_title_size", "focus_glow", "app_entrance",
            "background_dim", "bottom_shade", "theme_mode", "daynight_smooth", "row_spacing",
            "banner_poster_size", "banner_autoscroll", "continue_watching", "row_netflix", "row_youtube",
            "apps_only", "ss_clock_pos", "wallpaper_mode", "wallpaper_source", "wallpaper_slideshow",
            "wallpaper_darkness", "wallpaper_custom_id", "icon_tiles", "uniform_tiles",
        )

        @Volatile private var instance: ThemePreferences? = null

        @JvmStatic
        fun get(context: Context): SharedPreferences {
            return instance ?: synchronized(ThemePreferences::class.java) {
                instance ?: ThemePreferences(context.applicationContext.getSharedPreferences("personal_home", 0))
                    .also { instance = it }
            }
        }

        private fun scoped(key: String): Boolean {
            return (0 until ThemeStyle.COUNT).any { key.startsWith(ThemeStyle.prefix(it)) }
        }

        @Suppress("UNCHECKED_CAST")
        private fun put(editor: Editor, key: String, value: Any?) {
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, HashSet(value as Set<String>))
            }
        }
    }
}
