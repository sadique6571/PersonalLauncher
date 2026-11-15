package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

internal class HomeHeader(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val profile: HomeProfile,
    private val tv: Boolean,
    private val tvGlass: Boolean,
    private val glassOs: Boolean,
    private val harmony: Boolean,
    private val text: (String, Int, Int) -> TextView,
    private val applyAppFont: (TextView) -> Unit,
    private val surface: (Boolean) -> GradientDrawable,
    private val focusBelowToolbar: () -> Unit,
    private val updateNetwork: () -> Unit,
    private val openWifi: () -> Unit,
    private val toolbarKey: (View, KeyEvent) -> Boolean,
    private val showBluetooth: () -> Unit,
) {
    class Views(
        val avatar: ImageView,
        val name: TextView,
        val greeting: TextView,
        val networkStatus: TextView,
        val weather: TextView,
        val bluetooth: TextView?,
        val actions: LinearLayout,
    )

    fun build(header: LinearLayout, greetingText: String): Views {
        val photo: ImageView = if (glassOs) AppAccentAvatar(host) else ImageView(host)
        photo.id = R.id.profileAvatar
        photo.contentDescription = host.getString(R.string.h_edit_profile)
        photo.scaleType = ImageView.ScaleType.CENTER_CROP
        photo.background = profile.avatarSurface(false)
        if (!glassOs) profile.avatarOutline(photo)
        if (tvGlass) { photo.setPadding(dp(2), dp(2), dp(2), dp(2)); photo.elevation = dp(2).toFloat() }
        photo.isFocusable = false
        photo.setOnClickListener { LauncherPin.require(host, profile::show) }
        photo.setOnFocusChangeListener { v, focus -> v.background = profile.avatarSurface(focus) }
        val netStatus = text("", 13, MUTED)
        val identityCard = LinearLayout(host)
        identityCard.gravity = Gravity.CENTER_VERTICAL
        // The header already supplies the glass; a second profile-card fill hid the wallpaper detail.
        if (tvGlass) identityCard.setPadding(dp(6), dp(5), dp(14), dp(5))
        identityCard.setOnClickListener { LauncherPin.require(host, profile::show) }
        if (glassOs) {
            identityCard.id = R.id.leftBlock
            identityCard.background = null
            identityCard.isFocusable = true
            identityCard.setOnKeyListener { _, c, e ->
                if (c == KeyEvent.KEYCODE_DPAD_RIGHT && e.action == KeyEvent.ACTION_DOWN) { netStatus.requestFocus(); true }
                else if (c == KeyEvent.KEYCODE_DPAD_DOWN && e.action == KeyEvent.ACTION_DOWN) { focusBelowToolbar(); true }
                else c == KeyEvent.KEYCODE_DPAD_LEFT || c == KeyEvent.KEYCODE_DPAD_UP || c == KeyEvent.KEYCODE_DPAD_RIGHT || c == KeyEvent.KEYCODE_DPAD_DOWN
            }
            identityCard.setOnFocusChangeListener { v, f -> v.alpha = if (f) 1f else 0.88f }
        }
        identityCard.addView(photo, LinearLayout.LayoutParams(dp(if (glassOs) 42 else if (ThemeStyle.custom(prefs) && tv) 52 else 44), dp(if (glassOs) 42 else if (ThemeStyle.custom(prefs) && tv) 52 else 44)))
        if (ThemeStyle.id(prefs) == ThemeStyle.PEARL) {
            val rule = View(host)
            rule.setBackgroundColor(0x7778807c)
            val rp = LinearLayout.LayoutParams(dp(1), dp(40))
            rp.marginStart = dp(10)
            identityCard.addView(rule, rp)
        }
        val identity = LinearLayout(host)
        identity.setPadding(dp(12), 0, dp(4), 0)
        identity.orientation = LinearLayout.VERTICAL
        val nameView = text("", if (tvGlass) 20 else if (tv) 24 else 20, TEXT)
        nameView.maxLines = 1
        nameView.ellipsize = TextUtils.TruncateAt.END
        applyAppFont(nameView)
        if (ThemeStyle.custom(prefs)) nameView.setTypeface(nameView.typeface, if (ThemeStyle.id(prefs) == ThemeStyle.DUNE) Typeface.NORMAL else Typeface.BOLD)
        identity.addView(nameView)
        val greetView = text(greetingText, 13, MUTED)
        applyAppFont(greetView)
        if (prefs.getBoolean("hide_greeting", false) || (harmony && !HarmonyVisionLayout.showSecondGreeting())) greetView.visibility = View.GONE
        identity.addView(greetView)
        if (glassOs) {
            nameView.id = R.id.profileName
            nameView.setTextSize(19f)
            nameView.setTypeface(nameView.typeface, Typeface.BOLD)
            greetView.id = R.id.greeting
            greetView.setTextSize(12f)
            greetView.isAllCaps = false
            greetView.setTypeface(greetView.typeface, Typeface.BOLD)
            identity.removeView(greetView)
            identity.addView(greetView)
        }
        identityCard.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        val identityParams = LinearLayout.LayoutParams(0, -2, 1f)
        identityParams.marginEnd = dp(10)
        header.addView(identityCard, identityParams)
        netStatus.gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER
        netStatus.compoundDrawablePadding = dp(5)
        netStatus.isFocusable = false
        netStatus.setOnFocusChangeListener { v, focus ->
            if (glassOs) updateNetwork()
            else if (focus) v.background = surface(true)
            else v.setBackgroundColor(Color.TRANSPARENT)
        }
        netStatus.setPadding(dp(if (tv) 13 else 11), dp(8), dp(if (tv) 13 else 11), dp(8))
        netStatus.setOnClickListener { openWifi() }
        val bar = LinearLayout(host)
        bar.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        if (glassOs) { bar.id = R.id.topButtons; netStatus.isFocusable = true; netStatus.setOnKeyListener { v, _, e -> toolbarKey(v, e) } }
        bar.addView(netStatus, LinearLayout.LayoutParams(-2, dp(44)))
        val weatherView = text("Unavailable", 13, MUTED)
        weatherView.gravity = Gravity.CENTER
        weatherView.setPadding(dp(10), dp(8), dp(10), dp(8))
        if (harmony) weatherView.visibility = View.GONE
        bar.addView(weatherView, 0, LinearLayout.LayoutParams(-2, dp(44)))
        val btStatus = if (glassOs && !harmony) {
            val btStatus = text("Bluetooth", 11, 0xffdbeafe.toInt())
            btStatus.id = R.id.btnBluetooth
            btStatus.maxWidth = dp(130)
            btStatus.isSingleLine = true
            btStatus.ellipsize = TextUtils.TruncateAt.END
            btStatus.setPadding(dp(10), dp(8), dp(10), dp(8))
            btStatus.gravity = Gravity.CENTER
            val btIcon = TvSymbols.icon(host, android.R.drawable.stat_sys_data_bluetooth)
            btIcon?.setBounds(0, 0, dp(18), dp(18))
            btStatus.compoundDrawablePadding = dp(5)
            btStatus.setCompoundDrawables(btIcon, null, null, null)
            btStatus.background = GlassOsUi.panel(host, 999)
            btStatus.isFocusable = true
            btStatus.setOnFocusChangeListener { v, f -> v.background = surface(f) }
            btStatus.setOnClickListener { showBluetooth() }
            btStatus.setOnKeyListener { v, _, e -> toolbarKey(v, e) }
            bar.addView(btStatus, LinearLayout.LayoutParams(-2, dp(40)))
            btStatus
        } else null
        return Views(photo, nameView, greetView, netStatus, weatherView, btStatus, bar)
    }

    private fun dp(value: Int): Int = Math.round(value * host.resources.displayMetrics.density)

    private companion object {
        const val TEXT = 0xfff0f7ff.toInt()
        const val MUTED = 0xffa4bcd6.toInt()
    }
}
