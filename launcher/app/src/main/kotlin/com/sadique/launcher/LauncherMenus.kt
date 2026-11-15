package com.sadique.launcher

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.sadique.launcher.MainActivity.Companion.COLUMNS

internal class LauncherMenus(
    private val host: Activity,
    cfg: Config,
    private val root: FrameLayout,
    private val density: Float,
    private val adapter: AppAdapter,
    private val reorder: LauncherReorderController,
    private val networkLabel: () -> CharSequence?,
    private val showInputs: () -> Unit,
    private val openNetworkPill: Runnable,
    private val openLauncherSettings: () -> Unit,
    private val showModuleCards: Runnable,
    private val reloadApps: () -> Unit,
    private val open: (Intent) -> Unit,
    private val glassPopup: (View, Int) -> PopupWindow,
    private val dropIn: (PopupWindow, View, View, Int) -> Unit,
) {
    private val showInputsMenuRow = cfg.showInputsPill
    private val launcherSettingsButton = cfg.launcherSettingsButton
    private val moduleCards = cfg.moduleCards
    private val dockRow = cfg.dockRow
    private val appLibraryEnabled = cfg.appLibrary

    // ---- launcher settings and module cards -------------------------------------------------------

    /** top_menu: the gear's dropdown, standing in for the Inputs, Wi-Fi and Settings pills. */
    fun showTopMenu() {
        val anchor = host.findViewById<View>(R.id.btnSettings)
        val panel = host.layoutInflater.inflate(R.layout.popup_inputs, root, false)
        val rows = panel.findViewById<LinearLayout>(R.id.rows)
        val popup = glassPopup(panel, (220 * density).toInt())
        // Inputs opens its own list once this one has closed.
        if (showInputsMenuRow) addTvInputRow(host, rows, popup, R.drawable.ic_input, host.getString(R.string.inputs), -1) { root.post { showInputs() } }
        addTvInputRow(host, rows, popup, R.drawable.ic_wifi, host.getString(R.string.quick_network), -1, openNetworkPill)
        val network = networkLabel()
        if (!TextUtils.isEmpty(network)) {
            val status = rows.getChildAt(rows.childCount - 1).findViewById<TextView>(R.id.status)
            status.text = network
            status.visibility = View.VISIBLE
        }
        addTvInputRow(host, rows, popup, R.drawable.ic_swap_vert, host.getString(R.string.reorder), -1, reorder::toggleReorder)
        if (launcherSettingsButton) {
            addTvInputRow(host, rows, popup, R.drawable.ic_home_settings_pill, host.getString(R.string.launcher_settings), -1) { openLauncherSettings() }
        } else {
            addTvInputRow(host, rows, popup, R.drawable.ic_settings, host.getString(R.string.settings), -1) {
                open(SystemSettings.intent(host, Settings.ACTION_SETTINGS))
            }
        }
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.width = Math.max((220 * density).toInt(), Math.min(panel.measuredWidth, (360 * density).toInt()))
        dropIn(popup, panel, anchor, if (host.resources.getBoolean(R.bool.rtl_menu_bar)) Gravity.START else Gravity.END)
        rows.getChildAt(0).requestFocus()
    }

    /** The header opens launcher settings directly; hold OK keeps the extra home actions available. */
    fun showSettingsMenu() {
        val anchor = host.findViewById<View>(R.id.btnSettings)
        val panel = host.layoutInflater.inflate(R.layout.popup_inputs, root, false)
        val rows = panel.findViewById<LinearLayout>(R.id.rows)
        val popup = glassPopup(panel, (200 * density).toInt())
        if (!launcherSettingsButton) addSettingsMenuRow(rows, popup, R.string.launcher_settings) {
            openLauncherSettings()
        }
        // Firmware that ships its own settings app next to the AOSP one (Xiaomi China is one): the
        // vendor app holds pages the AOSP settings has no row for, such as Account & Security with
        // the ADB switch. Offered here only while one of them is installed.
        if (SystemSettings.vendorIntent(host) != null) {
            addSettingsMenuRow(rows, popup, R.string.native_settings) {
                val vendor = SystemSettings.vendorIntent(host)
                if (vendor != null) open(vendor)
            }
        }
        if (moduleCards) addSettingsMenuRow(rows, popup, R.string.module_cards, showModuleCards)
        if (dockRow) addSettingsMenuRow(rows, popup, R.string.dock_edit, reorder::startDockEdit)
        addSettingsMenuRow(rows, popup, R.string.reorder, reorder::toggleReorder)
        // A launcher may not restart the TV itself; the Home key helper can open the system power menu instead.
        if (HomeKeyService.canOpenPowerMenu()) addSettingsMenuRow(rows, popup, R.string.power_menu, HomeKeyService::openPowerMenu)
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.width = Math.max((200 * density).toInt(), panel.measuredWidth)
        // Hebrew puts Settings at the left of the header: grow the menu inward from it.
        dropIn(popup, panel, anchor, if (host.resources.getBoolean(R.bool.rtl_menu_bar))
                Gravity.START else Gravity.END)
        rows.getChildAt(0).requestFocus()
    }

    /**
     * Hold OK on a tile: a glass menu next to it. Move starts reorder mode with this tile already picked up;
     * Remove from Home takes the shortcut off (only with the app library on, where one press adds it back);
     * App info opens the TV's page for the app, which is what holding OK used to do.
     */
    fun showTileMenu(position: Int, card: View) {
        if (reorder.reordering) return
        val key = adapter.keyAt(position)
        val pkg = adapter.packageAt(position)
        // Empty dock slots and the Default-home tile carry the launcher's own package: no menu for them.
        if (key == null || pkg == null || pkg == host.packageName) return
        val panel = host.layoutInflater.inflate(R.layout.popup_inputs, root, false)
        val rows = panel.findViewById<LinearLayout>(R.id.rows)
        val popup = glassPopup(panel, (200 * density).toInt())
        if (dockRow && position < adapter.movableCount()) {
            val inDock = position < COLUMNS
            addSettingsMenuRow(rows, popup, if (inDock) R.string.tile_replace_in_dock else R.string.tile_add_to_dock) {
                reorder.startDockEditFor(key, inDock)
            }
        }
        if (position < adapter.movableCount()) addSettingsMenuRow(rows, popup, R.string.tile_move) {
            reorder.startMoving(key)
        }
        if (appLibraryEnabled) addSettingsMenuRow(rows, popup, R.string.library_remove_shortcut) {
            if (LibraryApps.save(host, pkg, false)) reloadApps()
            else Toast.makeText(host, R.string.library_save_failed, Toast.LENGTH_LONG).show()
        }
        // Some firmware has no App info page at all; a row that only toasts "can't open" helps nobody.
        val info = SystemSettings.appInfo(host, pkg)
        if (info != null) addSettingsMenuRow(rows, popup, R.string.library_app_info) { open(info) }
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.width = Math.max((200 * density).toInt(), panel.measuredWidth)
        // Dock tiles sit near the bottom of the screen: open the menu above the tile when it would not fit below.
        val at = IntArray(2)
        card.getLocationInWindow(at)
        val gap = (10 * density).toInt()
        val height = panel.measuredHeight
        if (at[1] + card.height + gap + height <= root.height) {
            dropIn(popup, panel, card, Gravity.START)
        } else {
            panel.alpha = 0f
            panel.pivotY = height.toFloat()
            panel.scaleY = 0.92f
            panel.translationY = 8 * density
            popup.showAtLocation(root, Gravity.NO_GRAVITY, at[0], Math.max(0, at[1] - gap - height))
            panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                    .setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
        rows.getChildAt(0).requestFocus()
    }

    private fun addSettingsMenuRow(rows: LinearLayout, popup: PopupWindow, title: Int, action: Runnable) {
        val row = host.layoutInflater.inflate(R.layout.item_input, rows, false)
        row.findViewById<View>(R.id.icon).visibility = View.GONE
        row.findViewById<View>(R.id.dot).visibility = View.GONE
        row.findViewById<View>(R.id.status).visibility = View.GONE
        row.findViewById<TextView>(R.id.title).setText(title)
        row.setOnClickListener {
            popup.dismiss()
            action.run()
        }
        // The OK-up after opening the menu must not activate its first row.
        var pressed = false
        row.setOnKeyListener { _, code, event ->
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) {
                false
            } else if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) pressed = true
                true
            } else {
                val activate = pressed
                pressed = false
                if (activate) {
                    popup.dismiss()
                    action.run()
                }
                true
            }
        }
        rows.addView(row)
    }
}
