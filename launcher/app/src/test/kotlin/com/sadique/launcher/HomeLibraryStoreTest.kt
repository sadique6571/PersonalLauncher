package com.sadique.launcher

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLibraryStoreTest {
    private val home = MemoryPrefs()
    private val registry = MemoryPrefs()
    private val store = HomeLibraryStore(home, registry)

    @Test fun updatePreservesExistingPicturesWithoutSwitchingProfiles() {
        assertEquals("personal-avatar.png", store.assetName("personal-avatar.png"))
        registry.edit().putString("active", "existing-id").apply()
        assertEquals("profile_existing-id_personal-avatar.png", store.assetName("personal-avatar.png"))
        assertEquals("profile_existing-id_personal-wallpaper.jpg", store.assetName("personal-wallpaper.jpg"))
        assertEquals("existing-id", registry.getString("active", ""))
    }

    @Test fun categoriesCanBeRenamedAndClearedWithoutChangingOtherApps() {
        store.category("video", "Videos")
        store.category("tool", "Tools")
        store.category("video", "Family")
        assertEquals("Family", store.category("video"))
        assertEquals("Tools", store.category("tool"))
        store.category("video", "")
        assertEquals("", store.category("video"))
        assertEquals(1, store.categories().size)
    }

    /** In-memory preferences: tests exercise production persistence without Android framework calls. */
    private class MemoryPrefs : SharedPreferences {
        private val data = HashMap<String, Any?>()

        override fun getAll(): Map<String, *> {
            return HashMap(data)
        }

        override fun getString(key: String, defValue: String?): String? {
            return if (data.containsKey(key)) data[key] as String else defValue
        }

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
            return if (data.containsKey(key)) HashSet(data[key] as Set<String>) else defValues
        }

        override fun getInt(key: String, defValue: Int): Int {
            return if (data.containsKey(key)) data[key] as Int else defValue
        }

        override fun getLong(key: String, defValue: Long): Long {
            return if (data.containsKey(key)) data[key] as Long else defValue
        }

        override fun getFloat(key: String, defValue: Float): Float {
            return if (data.containsKey(key)) data[key] as Float else defValue
        }

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return if (data.containsKey(key)) data[key] as Boolean else defValue
        }

        override fun contains(key: String): Boolean {
            return data.containsKey(key)
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        }

        override fun edit(): SharedPreferences.Editor {
            return object : SharedPreferences.Editor {
                private val updates = HashMap<String, Any?>()
                private var clear = false

                override fun putString(key: String, value: String?): SharedPreferences.Editor {
                    updates[key] = value
                    return this
                }

                override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
                    updates[key] = HashSet(values!!)
                    return this
                }

                override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                    updates[key] = value
                    return this
                }

                override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                    updates[key] = value
                    return this
                }

                override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                    updates[key] = value
                    return this
                }

                override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                    updates[key] = value
                    return this
                }

                override fun remove(key: String): SharedPreferences.Editor {
                    updates[key] = null
                    return this
                }

                override fun clear(): SharedPreferences.Editor {
                    clear = true
                    return this
                }

                override fun commit(): Boolean {
                    apply()
                    return true
                }

                override fun apply() {
                    if (clear) data.clear()
                    for ((k, v) in updates) {
                        if (v == null) data.remove(k) else data[k] = v
                    }
                }
            }
        }
    }
}
