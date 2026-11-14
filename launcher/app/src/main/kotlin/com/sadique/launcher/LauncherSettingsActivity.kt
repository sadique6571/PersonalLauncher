package com.sadique.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.ArrayList
import java.util.Collections

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
class LauncherSettingsActivity : Activity() {
    companion object {
        const val EXTRA_REORDER = "com.sadique.launcher.extra.REORDER_FROM_SETTINGS"
        private val DOCK_OPACITIES = intArrayOf(100, 70, 50, 30)

        private fun indexOf(images: List<File>, name: String?): Int {
            if (name == null) return -1
            for (i in images.indices) {
                if (images[i].name == name) return i
            }
            return -1
        }
    }

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(LauncherLocale.wrap(base)) // locale
    }

    private lateinit var choices: SharedPreferences
    private val columnChoices = arrayOfNulls<TextView>(4)
    private val rowChoices = arrayOfNulls<TextView>(4)
    private val dockChoices = arrayOfNulls<TextView>(DOCK_OPACITIES.size)
    private lateinit var wallpaperChoice: TextView
    private lateinit var animationsChoice: TextView
    private lateinit var advancedChoice: TextView
    private lateinit var followThemeChoice: TextView
    private lateinit var openOnStartChoice: TextView
    private var columns = 0
    private var rowsBelowDock = 0
    private var dockOpacity = 0
    private var animations = false
    private var advancedEffects = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_launcher_settings)
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
        val config = Config.load(this)
        val reorderChoice = findViewById<View>(R.id.reorderChoice)
        reorderChoice.visibility = if (config.launcherSettingsButton) View.VISIBLE else View.GONE
        reorderChoice.setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_REORDER, true))
            finish()
        }
        columns = config.columns
        rowsBelowDock = config.rowsBelowDock
        dockOpacity = config.dockOpacity
        animations = config.animations
        advancedEffects = config.animations && config.advancedEffects

        val ids = intArrayOf(R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7)
        for (i in ids.indices) {
            val value = i + 4
            val choice = findViewById<TextView>(ids[i])
            columnChoices[i] = choice
            choice.setOnClickListener {
                if (columns == value) return@setOnClickListener
                columns = value
                choices.edit().putInt("columns", value).apply()
                changed()
                refresh()
            }
        }
        val rowIds = intArrayOf(R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3)
        for (i in rowIds.indices) {
            val value = i
            val choice = findViewById<TextView>(rowIds[i])
            rowChoices[i] = choice
            choice.setOnClickListener {
                if (rowsBelowDock == value) return@setOnClickListener
                rowsBelowDock = value
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply()
                changed()
                refresh()
            }
        }
        val dockIds = intArrayOf(R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30)
        for (i in dockIds.indices) {
            val value = DOCK_OPACITIES[i]
            val choice = findViewById<TextView>(dockIds[i])
            dockChoices[i] = choice
            choice.setOnClickListener {
                if (dockOpacity == value) return@setOnClickListener
                dockOpacity = value
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply()
                changed()
                refresh()
            }
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (resources.getBoolean(R.bool.rtl_menu_bar))
            findViewById<View>(android.R.id.content).layoutDirection = View.LAYOUT_DIRECTION_RTL
        if (resources.getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices)
            reverseRow(rowChoices)
            reverseRow(dockChoices)
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice)
        wallpaperChoice.setOnClickListener { showWallpaperPicker() }
        animationsChoice = findViewById(R.id.animationsChoice)
        animationsChoice.setOnClickListener {
            animations = !animations
            if (!animations) advancedEffects = false
            choices.edit().putBoolean("animations", animations)
                .putBoolean("advanced_effects", advancedEffects).apply()
            changed()
            refresh()
        }
        advancedChoice = findViewById(R.id.advancedChoice)
        advancedChoice.setOnClickListener {
            if (advancedEffects) {
                setAdvanced(false)
            } else {
                ThemeDialogBuilder(this)
                    .setTitle(R.string.advanced_effects)
                    .setMessage(R.string.advanced_effects_warning)
                    .setPositiveButton(R.string.enable) { _, _ -> setAdvanced(true) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        openOnStartChoice = findViewById(R.id.openOnStartChoice)
        if (config.openOnStartChoice) {
            openOnStartChoice.visibility = View.VISIBLE
            openOnStartChoice.setOnClickListener { showOpenOnStartPicker() }
        }
        followThemeChoice = findViewById(R.id.followThemeChoice)
        followThemeChoice.setOnClickListener {
            if (!hasOnTvChoices()) return@setOnClickListener
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                .remove(Config.DOCK_OPACITY_KEY).remove(OpenOnStart.KEY).commit()
            val current = Config.load(this)
            columns = current.columns
            rowsBelowDock = current.rowsBelowDock
            dockOpacity = current.dockOpacity
            animations = current.animations
            advancedEffects = current.animations && current.advancedEffects
            changed()
            refresh()
        }
        findViewById<View>(R.id.defaultHomeChoice).setOnClickListener {
            open(Intent(this, HomeSettingsActivity::class.java))
        }
        findViewById<View>(R.id.tvSettingsChoice).setOnClickListener {
            open(SystemSettings.intent(this, Settings.ACTION_SETTINGS))
        }
        refresh()
        if (config.launcherSettingsButton) reorderChoice.requestFocus()
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))]?.requestFocus()
    }

    private fun reverseRow(views: Array<TextView?>) {
        val row = views[0]?.parent as LinearLayout
        row.removeAllViews()
        for (i in views.indices.reversed()) row.addView(views[i])
    }

    private fun showWallpaperPicker() {
        val dir = Config.dir(this)
        val images = ArrayList(WallpaperRotation.images(dir?.let { File(it, "wallpapers") }))
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        val single = dir?.let { File(it, Config.WALLPAPER_NAME) }
        if (single != null && single.isFile) images.add(0, single)
        val labels = Array<CharSequence>(images.size + 1) { i ->
            if (i < images.size) images[i].name else getString(R.string.wallpaper_none)
        }
        val current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null)
        val checked = if ("none" == current) images.size else indexOf(images, current)
        ThemeDialogBuilder(this)
            .setTitle(R.string.wallpaper_label)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                if (which == images.size) {
                    choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply()
                } else {
                    choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, images[which].name).apply()
                }
                changed()
                Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNeutralButton(R.string.wallpaper_theme_default) { _, _ ->
                choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply()
                changed()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private fun showOpenOnStartPicker() {
        val pm = packageManager
        val apps = ArrayList<Array<String>>()
        val tv = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        for (r in pm.queryIntentActivities(tv, 0)) {
            val pkg = r.activityInfo.packageName
            if (pkg == packageName) continue
            var seen = false
            for (a in apps) seen = seen or (a[1] == pkg)
            if (!seen) apps.add(arrayOf(r.loadLabel(pm).toString(), pkg))
        }
        Collections.sort(apps) { a, b -> a[0].compareToIgnoreCase(b[0]) }
        val labels = Array<CharSequence>(apps.size + 1) { i ->
            if (i == 0) getString(R.string.off) else apps[i - 1][0]
        }
        val current = choices.getString(OpenOnStart.KEY, "")
        var checked = 0
        for (i in apps.indices) {
            if (apps[i][1] == current) checked = i + 1
        }
        ThemeDialogBuilder(this)
            .setTitle(R.string.open_on_start_label)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply()
                else {
                    OpenOnStart.skipThisBoot(this)
                    choices.edit().putString(OpenOnStart.KEY, apps[which - 1][1]).apply()
                }
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private fun openOnStartLabel(): String {
        val pkg = choices.getString(OpenOnStart.KEY, "")
        if (pkg.isEmpty()) return getString(R.string.off)
        return try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private fun changed() {
        setResult(RESULT_OK)
        choices.edit().putLong(
            Config.SETTINGS_REVISION_KEY,
            choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1
        ).apply()
    }

    private fun setAdvanced(enabled: Boolean) {
        advancedEffects = enabled
        choices.edit().putBoolean("advanced_effects", enabled).apply()
        changed()
        refresh()
    }

    private fun refresh() {
        for (i in columnChoices.indices) {
            columnChoices[i]?.isSelected = columns == i + 4
        }
        for (i in rowChoices.indices) {
            rowChoices[i]?.isSelected = rowsBelowDock == i
        }
        for (i in dockChoices.indices) {
            dockChoices[i]?.isSelected = dockOpacity == DOCK_OPACITIES[i]
        }
        animationsChoice.text = getString(
            R.string.animations_state,
            getString(if (animations) R.string.on else R.string.off)
        )
        advancedChoice.text = getString(
            R.string.advanced_effects_state,
            getString(if (advancedEffects) R.string.on else R.string.off)
        )
        advancedChoice.isEnabled = animations
        advancedChoice.alpha = if (animations) 1f else 0.5f
        val wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null)
        wallpaperChoice.text = getString(
            R.string.wallpaper_state,
            if ("none" == wallpaperCurrent) getString(R.string.wallpaper_none)
            else wallpaperCurrent ?: getString(R.string.wallpaper_theme_default)
        )
        openOnStartChoice.text = getString(R.string.open_on_start_state, openOnStartLabel())
        followThemeChoice.isSelected = !hasOnTvChoices()
    }

    private fun hasOnTvChoices(): Boolean {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
            || choices.contains("animations") || choices.contains("advanced_effects")
            || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
            || choices.contains(OpenOnStart.KEY)
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show()
        }
    }
}
