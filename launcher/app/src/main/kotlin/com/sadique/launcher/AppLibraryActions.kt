package com.sadique.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import java.util.function.Predicate

internal class AppLibraryActions(
    private val activity: Activity,
    private val panel: LinearLayout,
    private val reducedMotion: Boolean,
    private val density: Float,
    private val onHome: Predicate<String>,
    private val restoreCardFocus: () -> Unit,
    private val open: (ResolveInfo) -> Unit,
    private val changeHome: (ResolveInfo, Boolean) -> Unit,
    private val close: () -> Unit,
    private val label: (ResolveInfo) -> String,
) {
    private var actionPopup: PopupWindow? = null
    val isOpen: Boolean get() = actionPopup != null

    fun dismiss() {
        val menu = actionPopup
        actionPopup = null
        menu?.dismiss()
    }

    fun show(app: ResolveInfo, anchor: View) {
        val pkg = app.activityInfo.packageName
        val menuPanel = activity.layoutInflater.inflate(R.layout.popup_inputs, panel, false)
        if (TvGlassStyle.enabled(ThemePreferences.get(activity))) {
            menuPanel.background = TvGlassStyle.surface(dp(24), dp(1), false, ThemePreferences.get(activity))
            menuPanel.clipToOutline = true
        }
        val rows: LinearLayout = menuPanel.findViewById(R.id.rows)
        if (activity is PersonalHomeActivity) activity.liquidPanel(menuPanel, 24)
        val menu = PopupWindow(menuPanel, dp(220), ViewGroup.LayoutParams.WRAP_CONTENT, true)
        actionPopup = menu
        menu.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        menu.isOutsideTouchable = true
        menu.animationStyle = 0
        menu.elevation = 0f
        menu.setOnDismissListener {
            if (actionPopup == menu) actionPopup = null
            restoreCardFocus()
        }
        addAction(rows, R.string.library_open) { open(app) }
        val home = onHome.test(pkg)
        addAction(rows, if (home) R.string.library_remove_shortcut else R.string.library_add_home) { changeHome(app, !home) }
        val info = SystemSettings.appInfo(activity, pkg)
        if (info != null) addAction(rows, R.string.library_app_info) {
            close()
            try {
                activity.startActivity(info)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show()
            }
        }
        var menuWidth = dp(220)
        for (i in 0 until rows.childCount) {
            val title: TextView = rows.getChildAt(i).findViewById(R.id.title)
            menuWidth = Math.max(menuWidth, Math.ceil(title.paint.measureText(title.text.toString()).toDouble()).toInt() + dp(58))
            title.ellipsize = TextUtils.TruncateAt.END
        }
        menuWidth = Math.min(menuWidth, panel.width - dp(24))
        menu.width = menuWidth
        menuPanel.measure(View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val panelLocation = IntArray(2)
        panel.getLocationOnScreen(panelLocation)
        val screen = Rect(panelLocation[0], panelLocation[1],
            panelLocation[0] + panel.width, panelLocation[1] + panel.height)
        val x = Math.max(screen.left + dp(12), Math.min(location[0] + anchor.width - dp(12), screen.right - menuWidth - dp(12)))
        val y = Math.max(screen.top + dp(12), Math.min(location[1] + dp(8), screen.bottom - menuPanel.measuredHeight - dp(12)))
        menuPanel.alpha = 0f
        menuPanel.pivotY = 0f
        menuPanel.scaleY = 0.92f
        menuPanel.translationY = -dp(8).toFloat()
        menu.showAtLocation(activity.window.decorView, Gravity.TOP or Gravity.START, x, y)
        menuPanel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration((if (reducedMotion) Motion.REDUCED_MS else 220).toLong())
            .setInterpolator(DecelerateInterpolator(1.6f)).start()
        rows.getChildAt(0).requestFocus()
    }

    private fun addAction(rows: LinearLayout, label: Int, action: Runnable) {
        val row = activity.layoutInflater.inflate(R.layout.item_input, rows, false)
        val title: TextView = row.findViewById(R.id.title)
        title.setText(label)
        if (ThemeStyle.custom(ThemePreferences.get(activity))) {
            title.setTextColor(ThemeStyle.foreground(ThemePreferences.get(activity)))
            row.background = GlassSettings.background(activity, false)
            row.setOnFocusChangeListener { v, focused -> v.setBackground(GlassSettings.background(activity, focused)) }
        }
        row.isFocusableInTouchMode = true
        row.findViewById<View>(R.id.icon).visibility = View.GONE
        row.findViewById<View>(R.id.dot).visibility = View.GONE
        row.findViewById<View>(R.id.status).visibility = View.GONE
        row.setOnClickListener { action.run() }
        rows.addView(row)
        row.setOnKeyListener { v, key, event ->
            val index = rows.indexOfChild(v)
            event.action == KeyEvent.ACTION_DOWN && (key == KeyEvent.KEYCODE_DPAD_LEFT
                || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_UP && index == 0
                || key == KeyEvent.KEYCODE_DPAD_DOWN && index == rows.childCount - 1)
        }
    }

    private fun dp(value: Int): Int = Math.round(value * density)
}
