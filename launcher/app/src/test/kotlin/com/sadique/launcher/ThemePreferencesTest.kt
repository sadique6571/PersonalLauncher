package com.sadique.launcher

import android.content.SharedPreferences
import java.util.*
import org.junit.Test
import org.junit.Assert.*

class ThemePreferencesTest {
    @Test fun inactiveThemeListenersStaySilentForEveryRegisteredStyle() {
        val base = Memory(); val prefs = ThemePreferences(base)
        val changes: MutableList<String> = ArrayList()
        prefs.registerOnSharedPreferenceChangeListener { _, k -> changes.add(k!!) }
        for (active in 0 until ThemeStyle.COUNT) {
            prefs.edit().putInt("visual_style", active).apply()
            for (target in 0 until ThemeStyle.COUNT) {
                changes.clear()
                base.edit().putInt(ThemeStyle.prefix(target) + "glass_alpha", active * 10 + target).apply()
                if (target == active) assertEquals(Collections.singletonList("glass_alpha"), changes)
                else assertTrue("inactive style " + target + " leaked into " + active, changes.isEmpty())
            }
        }
    }
    @Test fun pineAndAmberBackupRestoresBothProfilesAndPendingEditorsStayLocal() {
        val prefs = ThemePreferences(Memory())
        prefs.edit().putInt("visual_style", ThemeStyle.PINE).putInt("glass_alpha", 31).apply()
        val pine: SharedPreferences.Editor = prefs.edit().putInt("app_font", 2)
        prefs.edit().putInt("visual_style", ThemeStyle.AMBER).putInt("glass_alpha", 76).apply()
        pine.apply()
        assertEquals(0, prefs.getInt("app_font", 0))
        val backup: Map<String, *> = prefs.getAll()
        val restored = ThemePreferences(Memory()); val editor: SharedPreferences.Editor = restored.edit()
        for (entry in backup.entries) {
            if (entry.value is Boolean) editor.putBoolean(entry.key, entry.value as Boolean)
            if (entry.value is Int) editor.putInt(entry.key, entry.value as Int)
        }
        editor.apply()
        assertEquals(ThemeStyle.AMBER, ThemeStyle.id(restored)); assertEquals(76, restored.getInt("glass_alpha", 0))
        restored.edit().putInt("visual_style", ThemeStyle.PINE).apply()
        assertEquals(31, restored.getInt("glass_alpha", 0)); assertEquals(2, restored.getInt("app_font", 0))
    }
    @Test fun pineAndAmberHeaderTitlesContrastWithBothPaletteStops() {
        val prefs = ThemePreferences(Memory())
        for (style in intArrayOf(ThemeStyle.PINE, ThemeStyle.AMBER)) {
            prefs.edit().putInt("visual_style", style).apply()
            for (wallpaper in intArrayOf(0xff000000.toInt(), 0xffffffff.toInt())) for (base in intArrayOf(ThemeStyle.baseTop(prefs), ThemeStyle.baseBottom(prefs))) {
                val composite = MaterialAppearance.mix(wallpaper, base, HeaderAppearance.alpha(0) / 255f)
                val a = ThemeStyle.luminance(composite)
                val b = ThemeStyle.luminance(ThemeStyle.headerTitleColor(prefs))
                assertTrue("title contrast for style " + style, (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05) >= 4.5)
            }
        }
    }
    @Test fun wallpaperClockShadowMigrationIsOncePerTheme() {
        val prefs = ThemePreferences(Memory())
        for (style in 0 until ThemeStyle.COUNT) {
            prefs.edit().putInt("visual_style", style).putBoolean("clock_shadow", false).apply()
            HeaderAppearance.clockDefaults(prefs)
            assertTrue(prefs.getBoolean("clock_shadow", false))
            prefs.edit().putBoolean("clock_shadow", false).apply()
            HeaderAppearance.clockDefaults(prefs)
            assertFalse(prefs.getBoolean("clock_shadow", true))
        }
    }
    @Test fun themeHeaderInkSurvivesDarkAndWhiteWallpaper() {
        val prefs = ThemePreferences(Memory())
        for (style in 0 until ThemeStyle.COUNT) {
            prefs.edit().putInt("visual_style", style).putInt("header_bg_mode", 1).putBoolean("header_glass_v2", true).apply()
            for (wallpaper in intArrayOf(0xff000000.toInt(), 0xffffffff.toInt())) {
                val bases = if (style >= 2) intArrayOf(ThemeStyle.baseTop(prefs), ThemeStyle.baseBottom(prefs)) else intArrayOf(0xff132539.toInt(), 0xff09182a.toInt())
                for (base in bases) {
                    val composite = MaterialAppearance.mix(wallpaper, base, HeaderAppearance.alpha(0) / 255f)
                    val a = ThemeStyle.luminance(composite)
                    val b = ThemeStyle.luminance(HeaderAppearance.foreground(prefs))
                    assertTrue("style " + style, (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05) >= 4.5)
                }
            }
            prefs.edit().putInt("header_bg_mode", 0).apply()
            assertEquals(0xffffffff.toInt(), HeaderAppearance.foreground(prefs))
        }
    }
    @Test fun migrationPreservesActiveAppearanceAndDoesNotCopyItToOtherTheme() {
        for (active in booleanArrayOf(true, false)) {
            val base = Memory(); base.edit().putBoolean("tv_glass_style", active).putInt("glass_alpha", 33).putString("weather_city", "Okara").apply()
            val prefs = ThemePreferences(base)
            assertEquals(33, prefs.getInt("glass_alpha", 70))
            prefs.edit().putBoolean("tv_glass_style", !active).apply()
            assertEquals(70, prefs.getInt("glass_alpha", 70))
            prefs.edit().putInt("glass_alpha", 90).putInt("clock_size", 42).apply()
            prefs.edit().putBoolean("tv_glass_style", active).apply()
            assertEquals(33, prefs.getInt("glass_alpha", 70)); assertEquals(52, prefs.getInt("clock_size", 52))
            assertEquals("Okara", prefs.getString("weather_city", ""))
        }
    }
    @Test fun returningToAThemeRestoresColourFontAndLayoutAndResetIsLocal() {
        val prefs = ThemePreferences(Memory())
        prefs.edit().putInt("custom_accent", 12).putInt("app_font", 1).putInt("tv_columns", 4).apply()
        prefs.edit().putBoolean("tv_glass_style", false).apply()
        prefs.edit().putInt("custom_accent", 34).putInt("app_font", 2).putInt("tv_columns", 6).apply()
        prefs.edit().remove("tv_columns").apply(); assertEquals(0, prefs.getInt("tv_columns", 0))
        prefs.edit().putBoolean("tv_glass_style", true).apply()
        assertEquals(12, prefs.getInt("custom_accent", 0)); assertEquals(1, prefs.getInt("app_font", 0)); assertEquals(4, prefs.getInt("tv_columns", 0))
        prefs.edit().putBoolean("tv_glass_style", false).apply(); assertEquals(34, prefs.getInt("custom_accent", 0))
    }
    @Test fun editorKeepsItsThemeEvenIfAnotherOperationSwitchesBeforeApply() {
        val prefs = ThemePreferences(Memory())
        val glass: SharedPreferences.Editor = prefs.edit().putInt("glass_alpha", 20)
        prefs.edit().putBoolean("tv_glass_style", false).apply(); glass.apply()
        assertEquals(70, prefs.getInt("glass_alpha", 70))
        prefs.edit().putBoolean("tv_glass_style", true).apply(); assertEquals(20, prefs.getInt("glass_alpha", 70))
    }
    @Test fun backupContainsBothThemesAndRestoreTargetsExplicitSelectedTheme() {
        val prefs = ThemePreferences(Memory())
        prefs.edit().putInt("glass_alpha", 25).apply(); prefs.edit().putBoolean("tv_glass_style", false).putInt("glass_alpha", 85).apply()
        val backup: Map<String, *> = prefs.getAll()
        assertEquals(25, backup["appearance.glass.glass_alpha"]); assertEquals(85, backup["appearance.classic.glass_alpha"])
        val restored = ThemePreferences(Memory()); val editor: SharedPreferences.Editor = restored.edit()
        for (e in backup.entries) {
            if (e.value is Boolean) editor.putBoolean(e.key, e.value as Boolean)
            else if (e.value is Int) editor.putInt(e.key, e.value as Int)
        }
        assertTrue(editor.commit()); assertEquals(85, restored.getInt("glass_alpha", 0))
        restored.edit().putBoolean("tv_glass_style", true).apply(); assertEquals(25, restored.getInt("glass_alpha", 0))
    }
    @Test fun listenersMapActiveKeysAndSharedIdentityDoesNotSplit() {
        val base = Memory(); val prefs = ThemePreferences(base); val changed: MutableList<String> = ArrayList()
        val listener: SharedPreferences.OnSharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k -> changed.add(k!!) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        base.edit().putInt("appearance.classic.glass_alpha", 90).apply(); assertTrue(changed.isEmpty())
        prefs.edit().putInt("glass_alpha", 30).putString("name", "Person").apply(); assertTrue(changed.contains("glass_alpha"))
        prefs.edit().putBoolean("tv_glass_style", false).apply(); assertEquals("Person", prefs.getString("name", ""))
        val count = changed.size; prefs.unregisterOnSharedPreferenceChangeListener(listener); prefs.edit().putInt("glass_alpha", 40).apply(); assertEquals(count, changed.size)
    }
    @Test fun transparencyIsIndependentAndDrawerNeverBecomesSolid() {
        val prefs = ThemePreferences(Memory())
        assertTrue(PanelTransparency.enabled(prefs))
        prefs.edit().putInt("glass_alpha", 30).putInt("glass_finish", 1).putBoolean("panel_transparency", false).apply()
        for (alpha in intArrayOf(0, 24, 70, 150, 245)) {
            assertEquals(255, PanelTransparency.alpha(alpha, prefs, false))
            assertEquals(alpha, PanelTransparency.alpha(alpha, prefs, true))
        }
        prefs.edit().putBoolean("tv_glass_style", false).apply()
        assertTrue(PanelTransparency.enabled(prefs))
        prefs.edit().putInt("glass_alpha", 70).putBoolean("panel_transparency", false).apply()
        assertEquals(255, PanelTransparency.alpha(ClassicGlassAlpha.card(70, false), prefs, false))
        assertEquals(49, PanelTransparency.alpha(ClassicGlassAlpha.drawer(70), prefs, true))
        prefs.edit().putBoolean("panel_transparency", true).apply()
        assertEquals(105, PanelTransparency.alpha(ClassicGlassAlpha.card(70, false), prefs, false))
        prefs.edit().putBoolean("tv_glass_style", true).apply()
        assertFalse(PanelTransparency.enabled(prefs))
        assertEquals(30, prefs.getInt("glass_alpha", 0)); assertEquals(1, prefs.getInt("glass_finish", 0))
        prefs.edit().putBoolean("panel_transparency", true).apply()
        assertEquals(24, PanelTransparency.alpha(24, prefs, false))
    }
    @Test fun missingHeaderGlassReturnsOnceAndExplicitOffSurvivesThemeChanges() {
        val prefs = ThemePreferences(Memory())
        prefs.edit().putInt("header_bg_mode", 0).apply()
        assertEquals(1, HeaderAppearance.mode(prefs))
        prefs.edit().putInt("header_bg_mode", 0).putInt("avatar_shape", 1).apply()
        assertEquals(0, HeaderAppearance.mode(prefs))
        prefs.edit().putBoolean("tv_glass_style", false).apply()
        assertEquals(1, HeaderAppearance.mode(prefs))
        assertEquals(0, prefs.getInt("avatar_shape", 0))
        prefs.edit().putInt("header_bg_mode", 2).apply()
        assertEquals(2, HeaderAppearance.mode(prefs))
        prefs.edit().putBoolean("tv_glass_style", true).apply()
        assertEquals(0, HeaderAppearance.mode(prefs))
        assertEquals(1, prefs.getInt("avatar_shape", 0))
    }
    @Test fun edgeSettingsRetainDistinctStrengthsInBothThemes() {
        val prefs = ThemePreferences(Memory())
        for (glass in booleanArrayOf(true, false)) {
            prefs.edit().putBoolean("tv_glass_style", glass).apply()
            var last = -1
            for (edge in 0 until 3) {
                prefs.edit().putInt("glass_edge", edge).apply()
                val alpha = TvGlassStyle.edge(prefs) ushr 24
                assertTrue(alpha > last); last = alpha
            }
        }
    }
    @Test fun allThemesRestoreTheirOwnAppearanceAndWallpaperWithoutSplittingIdentity() {
        val prefs = ThemePreferences(Memory())
        prefs.edit().putString("name", "Profile").putInt("wallpaper_source", 1).apply()
        for (style in 0 until ThemeStyle.COUNT) {
            prefs.edit().putInt("visual_style", style).putInt("glass_alpha", 20 + style * 10)
                    .putInt("avatar_shape", style % 2).putBoolean("panel_transparency", style % 2 == 0)
                    .putInt("wallpaper_source", style % 3).putString("wallpaper_mode", if (style == 0) "solid" else "mountains")
                    .putInt("ambient_strength", style * 10).putBoolean("glass_os_defaults_v1", style == 0).apply()
        }
        for (style in ThemeStyle.COUNT - 1 downTo 0) {
            prefs.edit().putInt("visual_style", style).apply()
            assertEquals(style, ThemeStyle.id(prefs))
            assertEquals(20 + style * 10, prefs.getInt("glass_alpha", 0))
            assertEquals(style % 2, prefs.getInt("avatar_shape", 0))
            assertEquals(style % 2 == 0, PanelTransparency.enabled(prefs))
            assertEquals("Profile", prefs.getString("name", ""))
            assertEquals(style % 3, prefs.getInt("wallpaper_source", -1))
            assertEquals(if (style == 0) "solid" else "mountains", prefs.getString("wallpaper_mode", ""))
            assertEquals(style * 10, prefs.getInt("ambient_strength", -1))
            assertEquals(style == 0, prefs.getBoolean("glass_os_defaults_v1", false))
        }
    }
    @Test fun harmonyDailyWordsPreferenceNeverChangesOtherThemes() {
        val prefs = ThemePreferences(Memory())
        prefs.edit().putInt("visual_style", ThemeStyle.HARMONY)
                .putBoolean("harmony_daily_quote", false).apply()
        assertFalse(prefs.getBoolean("harmony_daily_quote", true))
        prefs.edit().putInt("visual_style", ThemeStyle.GLASS).apply()
        assertTrue(prefs.getBoolean("harmony_daily_quote", true))
        prefs.edit().putInt("visual_style", ThemeStyle.HARMONY).apply()
        assertFalse(prefs.getBoolean("harmony_daily_quote", true))
    }
    @Test fun newStyleBackupRestoresSelectedNamespaceRegardlessOfLegacyBoolean() {
        val prefs = ThemePreferences(Memory())
        prefs.edit().putInt("visual_style", 2).putInt("glass_alpha", 31).apply()
        prefs.edit().putInt("visual_style", 3).putInt("glass_alpha", 67).apply()
        val backup: Map<String, *> = prefs.getAll()
        assertEquals(31, backup["appearance.dune.glass_alpha"])
        assertEquals(67, backup["appearance.pearl.glass_alpha"])
        val restored = ThemePreferences(Memory()); val editor: SharedPreferences.Editor = restored.edit()
        for (entry in backup.entries) {
            if (entry.value is Boolean) editor.putBoolean(entry.key, entry.value as Boolean)
            if (entry.value is Int) editor.putInt(entry.key, entry.value as Int)
        }
        editor.apply(); assertEquals(3, ThemeStyle.id(restored)); assertEquals(67, restored.getInt("glass_alpha", 0))
        restored.edit().putInt("visual_style", 2).apply(); assertEquals(31, restored.getInt("glass_alpha", 0))
    }
    @Test fun staleEditorsAndInactiveNewThemeChangesDoNotLeakIntoCurrentTheme() {
        val base = Memory(); val prefs = ThemePreferences(base)
        prefs.edit().putInt("visual_style", 2).apply()
        val dune: SharedPreferences.Editor = prefs.edit().putInt("glass_alpha", 42)
        prefs.edit().putInt("visual_style", 3).apply(); dune.apply()
        assertEquals(70, prefs.getInt("glass_alpha", 70))
        val changes: MutableList<String> = ArrayList(); prefs.registerOnSharedPreferenceChangeListener { _, k -> changes.add(k!!) }
        base.edit().putInt("appearance.dune.glass_alpha", 55).apply(); assertTrue(changes.isEmpty())
        prefs.edit().putInt("glass_alpha", 80).apply(); assertTrue(changes.contains("glass_alpha"))
        prefs.edit().putInt("visual_style", 2).apply(); assertEquals(55, prefs.getInt("glass_alpha", 0))
    }
    @Test fun newThemePaletteHasReadableTextAndLegacySelectorRemainsCompatible() {
        val prefs = ThemePreferences(Memory())
        for (style in intArrayOf(ThemeStyle.DUNE, ThemeStyle.PEARL)) {
            prefs.edit().putInt("visual_style", style).apply()
            val background = if (style == ThemeStyle.PEARL) 0xffe9e4dc.toInt() else 0xff111e24.toInt()
            val a = ThemeStyle.luminance(background)
            val b = ThemeStyle.luminance(ThemeStyle.foreground(prefs))
            assertTrue((Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05) > 7)
            assertTrue(ThemeStyle.custom(prefs))
            assertEquals(style == ThemeStyle.PEARL, ThemeStyle.pearl(prefs))
        }
        prefs.edit().putBoolean("tv_glass_style", false).apply(); assertEquals(ThemeStyle.CLASSIC, ThemeStyle.id(prefs))
        prefs.edit().putBoolean("tv_glass_style", true).apply(); assertEquals(ThemeStyle.GLASS, ThemeStyle.id(prefs))
    }
    @Test fun customBlackOrWhiteAccentCannotHideMaterialFocusIndicator() {
        val prefs = ThemePreferences(Memory())
        for (style in intArrayOf(ThemeStyle.DUNE, ThemeStyle.PEARL)) for (color in intArrayOf(0xff000000.toInt(), 0xffffffff.toInt(), 0xffedab75.toInt(), 0xff006d72.toInt())) {
            prefs.edit().putInt("visual_style", style).putInt("theme_name", 3).putInt("custom_accent", color).apply()
            val background = if (style == ThemeStyle.PEARL) 0xffe9e4dc.toInt() else 0xff111e24.toInt()
            val a = ThemeStyle.luminance(background)
            val b = ThemeStyle.luminance(ThemeStyle.focusAccent(prefs))
            assertTrue((Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05) >= 3)
        }
    }
    @Test fun bothMaterialsHonorFontClockAlignmentAndPreserveAccentIdentity() {
        val prefs = ThemePreferences(Memory())
        for (style in intArrayOf(ThemeStyle.DUNE, ThemeStyle.PEARL)) {
            prefs.edit().putInt("visual_style", style).putInt("app_font", 1).putInt("clock_font", 2).putInt("clock_align", 0).apply()
            assertFalse(ThemeStyle.fixedTypography(prefs))
            assertEquals("serif", ThemeStyle.fontFamily(prefs))
            assertEquals("monospace", ThemeStyle.clockFontFamily(prefs))
            assertEquals(0, ThemeStyle.clockAlignment(prefs))
            prefs.edit().putInt("clock_align", 2).apply(); assertEquals(2, ThemeStyle.clockAlignment(prefs))
            val accents: MutableSet<Int> = HashSet()
            for (color in intArrayOf(0xffff5252.toInt(), 0xffffea00.toInt(), 0xff69f0ae.toInt(), 0xff508dff.toInt())) {
                prefs.edit().putInt("theme_name", 3).putInt("custom_accent", color).apply()
                assertEquals(color, ThemeStyle.accent(prefs)); accents.add(ThemeStyle.focusAccent(prefs))
            }
            assertEquals(4, accents.size)
            prefs.edit().putBoolean("panel_transparency", false).apply()
            assertEquals(255, PanelTransparency.alpha(MaterialAppearance.alpha(0, 30, false, false), prefs, false))
            assertEquals(MaterialAppearance.alpha(0, 30, true, false), PanelTransparency.alpha(MaterialAppearance.alpha(0, 30, true, false), prefs, true))
        }
        prefs.edit().putInt("visual_style", ThemeStyle.GLASS).putInt("clock_align", 0).apply()
        assertTrue(ThemeStyle.fixedTypography(prefs)); assertEquals(1, ThemeStyle.clockAlignment(prefs))
    }
    @Test fun solidPanelToggleCannotChangeDrawerPanelOrInnerCardOpacityInAnyStyle() {
        val prefs = ThemePreferences(Memory())
        for (style in 0 until ThemeStyle.COUNT) {
            prefs.edit().putInt("visual_style", style).apply()
            for (finish in 0 until 3) for (focused in booleanArrayOf(false, true)) {
                val glass = MaterialAppearance.alpha(finish, 70, true, focused)
                prefs.edit().putBoolean("panel_transparency", true).apply()
                val enabled = PanelTransparency.alpha(glass, prefs, true)
                prefs.edit().putBoolean("panel_transparency", false).apply()
                assertEquals(enabled, PanelTransparency.alpha(glass, prefs, true))
                assertEquals(255, PanelTransparency.alpha(glass, prefs, false))
            }
        }
    }
    @Test fun cobaltAndSageUseIndependentProfilesAndReadablePalettes() {
        val prefs = ThemePreferences(Memory())
        prefs.edit().putInt("visual_style", ThemeStyle.COBALT).apply(); ThemeStyle.demoDefaults(prefs)
        assertEquals(4, prefs.getInt("tv_columns", 0)); assertEquals(2, prefs.getInt("phone_columns", 0))
        assertEquals(0xffffd92f.toInt(), ThemeStyle.accent(prefs)); assertFalse(ThemeStyle.light(prefs))
        prefs.edit().putInt("glass_alpha", 30).apply()
        prefs.edit().putInt("visual_style", ThemeStyle.SAGE).apply(); ThemeStyle.demoDefaults(prefs)
        assertTrue(ThemeStyle.light(prefs)); assertEquals(70, prefs.getInt("glass_alpha", 70))
        assertEquals(0xff195c36.toInt(), ThemeStyle.accent(prefs))
        assertEquals(0xffffffff.toInt(), ThemeStyle.wallpaperClockInk(0xffeef3e7.toInt(), ThemeStyle.foreground(prefs)))
        assertEquals(ThemeStyle.foreground(prefs), ThemeStyle.wallpaperClockInk(0xffffffff.toInt(), ThemeStyle.foreground(prefs)))
        assertEquals(0xffffffff.toInt(), ThemeStyle.wallpaperClockInk(0xff10251a.toInt(), ThemeStyle.foreground(prefs)))
        val a = ThemeStyle.luminance(ThemeStyle.baseTop(prefs))
        val b = ThemeStyle.luminance(ThemeStyle.foreground(prefs))
        assertTrue((a + 0.05) / (b + 0.05) > 7)
        prefs.edit().putInt("phone_columns", 3).putInt("header_bg_mode", 0).apply(); ThemeStyle.demoDefaults(prefs)
        assertEquals(3, prefs.getInt("phone_columns", 0)); assertEquals(0, prefs.getInt("header_bg_mode", 1))
        prefs.edit().putInt("visual_style", ThemeStyle.COBALT).apply(); assertEquals(30, prefs.getInt("glass_alpha", 0))
        assertEquals("appearance.cobalt.", ThemeStyle.prefix(4)); assertEquals("appearance.sage.", ThemeStyle.prefix(5))
    }
    @Test fun newThemeNamespacesSurviveBackupAndInactiveListenerChanges() {
        val base = Memory(); val prefs = ThemePreferences(base)
        prefs.edit().putInt("visual_style", 4).putInt("app_label_size", 20).apply()
        prefs.edit().putInt("visual_style", 5).putInt("app_label_size", 24).apply()
        val backup: Map<String, *> = prefs.getAll()
        assertEquals(20, backup["appearance.cobalt.app_label_size"])
        assertEquals(24, backup["appearance.sage.app_label_size"])
        val changes: MutableList<String> = ArrayList(); prefs.registerOnSharedPreferenceChangeListener { _, k -> changes.add(k!!) }
        base.edit().putInt("appearance.cobalt.app_label_size", 14).apply(); assertTrue(changes.isEmpty())
        prefs.edit().putInt("app_label_size", 17).apply(); assertTrue(changes.contains("app_label_size"))
        val restored = ThemePreferences(Memory()); val e: SharedPreferences.Editor = restored.edit()
        for (entry in backup.entries) {
            if (entry.value is Int) e.putInt(entry.key, entry.value as Int)
            if (entry.value is Boolean) e.putBoolean(entry.key, entry.value as Boolean)
        }
        e.apply(); assertEquals(5, ThemeStyle.id(restored)); assertEquals(24, restored.getInt("app_label_size", 0))
        restored.edit().putInt("visual_style", 4).apply(); assertEquals(20, restored.getInt("app_label_size", 0))
    }
    @Test fun defaultReferenceColoursDoNotMixTheFocusAccentIntoTheCardBase() {
        val prefs = ThemePreferences(Memory())
        for (style in 2 until ThemeStyle.COUNT) {
            prefs.edit().putInt("visual_style", style).putInt("theme_name", 0).apply()
            val base = ThemeStyle.baseTop(prefs); assertEquals(base, ThemeStyle.materialColor(prefs, base, false))
            prefs.edit().putInt("theme_name", 3).putInt("custom_accent", 0xffff0000.toInt()).apply()
            val red = ThemeStyle.materialColor(prefs, base, false)
            prefs.edit().putInt("custom_accent", 0xff0000ff.toInt()).apply(); assertNotEquals(red, ThemeStyle.materialColor(prefs, base, false))
        }
    }
    @Test fun weatherWidgetAndAnimationRemainIndependentInAllThemes() {
        val prefs = ThemePreferences(Memory())
        prefs.edit().putString("weather_city", "Test city").apply()
        for (style in 0 until ThemeStyle.COUNT)
            prefs.edit().putInt("visual_style", style).putBoolean("weather_widget", style % 2 == 0).putBoolean("weather_widget_animation", style % 3 == 0).putInt("clock_align", 2).apply()
        for (style in 0 until ThemeStyle.COUNT) {
            prefs.edit().putInt("visual_style", style).apply()
            assertEquals(style % 2 == 0, prefs.getBoolean("weather_widget", false))
            assertEquals(style % 3 == 0, prefs.getBoolean("weather_widget_animation", false))
            assertEquals(2, prefs.getInt("clock_align", 0))
            assertEquals("Test city", prefs.getString("weather_city", ""))
        }
    }
    private class Memory : SharedPreferences {
        val data: MutableMap<String, Any?> = HashMap()
        val listeners: MutableSet<SharedPreferences.OnSharedPreferenceChangeListener> = HashSet()
        override fun getAll(): Map<String, *> { return HashMap(data) }
        override fun getString(key: String, defValue: String?): String? { return if (data.containsKey(key)) data[key] as String else defValue }
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
            return if (data.containsKey(key)) HashSet(data[key] as Set<String>) else defValues
        }
        override fun getInt(key: String, defValue: Int): Int { return if (data.containsKey(key)) data[key] as Int else defValue }
        override fun getLong(key: String, defValue: Long): Long { return if (data.containsKey(key)) data[key] as Long else defValue }
        override fun getFloat(key: String, defValue: Float): Float { return if (data.containsKey(key)) data[key] as Float else defValue }
        override fun getBoolean(key: String, defValue: Boolean): Boolean { return if (data.containsKey(key)) data[key] as Boolean else defValue }
        override fun contains(key: String): Boolean { return data.containsKey(key) }
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.add(listener) }
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.remove(listener) }
        override fun edit(): SharedPreferences.Editor {
            return object : SharedPreferences.Editor {
                val pending: MutableMap<String, Any?> = LinkedHashMap()
                var clear = false
                fun add(k: String, v: Any?): SharedPreferences.Editor { pending[k] = v; return this }
                override fun putString(key: String, value: String?): SharedPreferences.Editor { return add(key, value) }
                override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                    return add(key, if (values == null) null else HashSet(values))
                }
                override fun putInt(key: String, value: Int): SharedPreferences.Editor { return add(key, value) }
                override fun putLong(key: String, value: Long): SharedPreferences.Editor { return add(key, value) }
                override fun putFloat(key: String, value: Float): SharedPreferences.Editor { return add(key, value) }
                override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { return add(key, value) }
                override fun remove(key: String): SharedPreferences.Editor { return add(key, null) }
                override fun clear(): SharedPreferences.Editor { clear = true; return this }
                override fun commit(): Boolean { apply(); return true }
                override fun apply() {
                    if (clear) data.clear()
                    for (e in pending.entries) {
                        if (e.value == null) data.remove(e.key) else data[e.key] = e.value
                    }
                    for (k in pending.keys) for (l in HashSet(listeners)) l.onSharedPreferenceChanged(this@Memory, k)
                }
            }
        }
    }
}
