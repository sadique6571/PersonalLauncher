package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.ImageView
import android.widget.TextView

/** Header network pill: text, icon and online state. Pure move from PersonalHomeActivity. */
internal class HomeNetworkStatus(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val dp: (Int) -> Int,
    private val glassOs: () -> Boolean,
    private val connectivity: () -> ConnectivityManager?,
    private val status: () -> TextView?,
    private val avatar: () -> ImageView?,
    private val onRefresh: () -> Unit,
) {
    fun update() {
        val status = status() ?: return
        val conn = connectivity()
        val active = conn?.activeNetwork
        val caps = if (active == null) null else conn?.getNetworkCapabilities(active)
        val online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val wifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        status.text = if (ThemeStyle.custom(prefs)) "" else if (online) "Connected" else if (caps == null) "Disconnected" else "No internet access"
        status.contentDescription = if (online) "Network connected" else if (caps == null) "Network disconnected" else "Network without internet"
        status.tooltipText = if (online) (if (wifi) "Wi-Fi · Internet available" else "Internet available") else "No internet"
        val color = if (online) 0xff59e493.toInt() else 0xffff6c78.toInt()
        status.setTextColor(if (ThemeStyle.custom(prefs)) HeaderAppearance.foreground(prefs) else color)
        val iconId = if (wifi || caps == null) R.drawable.ic_wifi
                else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) R.drawable.ic_ethernet else R.drawable.ic_internet
        val icon = TvSymbols.icon(activity, iconId)
        icon?.setTint(if (ThemeStyle.custom(prefs)) HeaderAppearance.foreground(prefs) else color)
        icon?.setBounds(0, 0, dp(28), dp(24))
        status.setCompoundDrawables(icon, null, null, null)
        status.contentDescription = if (online) "Internet available. Open Wi-Fi settings" else "No validated internet access. Open Wi-Fi settings"
        if (glassOs()) {
            val transport = if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) "Ethernet" else if (wifi || caps == null) "Wi-Fi" else "Internet"
            status.text = "● " + transport + " • " + if (online) "Online" else "No Internet"
            status.setTextSize(11f)
            icon?.setBounds(0, 0, dp(18), dp(18))
            status.setCompoundDrawables(icon, null, null, null)
            status.setTextColor(if (online) 0xffdcfce7.toInt() else 0xfffee2e2.toInt())
            val state = GradientDrawable()
            state.setColor(if (online) 0x8014532d.toInt() else 0x947f1d1d.toInt())
            state.cornerRadius = dp(999).toFloat()
            state.setStroke(dp(if (status.isFocused) 2 else 1), if (online) 0xb322c55e.toInt() else 0xccef4444.toInt())
            status.background = state
            val photo = avatar()
            if (photo is AppAccentAvatar) photo.online(online)
        }
        onRefresh()
    }
}
