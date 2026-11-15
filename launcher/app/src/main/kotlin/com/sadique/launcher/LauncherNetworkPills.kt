package com.sadique.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.view.View
import android.widget.TextView

internal class LauncherNetworkPills(
    private val host: Activity,
    cfg: Config,
    private val btnWifi: TextView,
    private val btnVpn: TextView,
    private val density: Float,
    private val festiveDiya: () -> DiyaDrawable?,
    private val firstTopButton: () -> View,
) {
    /** The network pill's icon as last set, so a refresh that changes nothing does not lay the top bar out again. */
    private var wifiIcon = 0
    /** icon_only_buttons without wifi_label_width: the Wi-Fi pill shows no network name. */
    private val wifiIconOnly = cfg.iconOnlyButtons && cfg.wifiLabelWidth <= 0
    /** config.xml wifi_status_color: the Wi-Fi icon's green while online and its usual pill colours; null while off. */
    private var wifiOnlineTint: ColorStateList? = null
    private var wifiPlainTint: ColorStateList? = null
    /** wifi_status_warnings (with wifi_status_color): orange without internet, red offline; null while off. */
    private var wifiNoInternetTint: ColorStateList? = null
    private var wifiOfflineTint: ColorStateList? = null
    private val vpnApp = cfg.vpnApp
    /** vpn_pill: the VPN shown as its own pill (btnVpn) instead of a badge in the network pill. Off with top_menu. */
    val vpnPill = cfg.vpnPill && !cfg.topMenu
    /** The VpnBadge pill state btnVpn shows, so a refresh that changes nothing does not restyle it. */
    private var vpnPillState = VpnBadge.PILL_HIDDEN
    /** icon_only_vpn: the VPN pill is a round lock button; off, it keeps the plain glass look saved here. */
    private val iconOnlyVpn = vpnPill && cfg.iconOnlyVpn
    private var vpnPlainBackground: Drawable.ConstantState? = null
    private var vpnPlainInk: ColorStateList? = null

    val warnsAboutInternet: Boolean get() = wifiNoInternetTint != null
    val label: CharSequence? get() = if (wifiIconOnly) btnWifi.contentDescription else btnWifi.text

    init {
        if (cfg.wifiStatusColor) {
            wifiPlainTint = btnWifi.compoundDrawableTintList
            wifiOnlineTint = host.getColorStateList(R.color.pill_icon_online)
            if (cfg.wifiStatusWarnings) {
                wifiNoInternetTint = host.getColorStateList(R.color.pill_icon_no_internet)
                wifiOfflineTint = host.getColorStateList(R.color.pill_icon_offline)
            }
        }
    }

    fun captureVpnStyle(makeIconOnly: (TextView) -> Unit) {
        if (iconOnlyVpn) {
            vpnPlainBackground = btnVpn.background.constantState
            vpnPlainInk = btnVpn.compoundDrawableTintList
            makeIconOnly(btnVpn)
        }
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    fun refresh() {
        var text = host.getString(R.string.wifi)
        var vpn = false
        var online = false
        var validated = false
        var icon = R.drawable.ic_wifi
        // The diya's flame: 0 is out (offline), 1 to 4 follow the Wi-Fi signal, Ethernet burns steady at 4.
        var flame = 0
        var steady = false
        try {
            val cm = host.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            var wifi = false
            var ethernet = false
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (net in cm?.allNetworks ?: emptyArray<Network>()) {
                val caps = cm?.getNetworkCapabilities(net)
                if (caps == null) continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true
                    continue
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                online = true
                // Android reached the internet through it; a captive portal stays unvalidated until login.
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) validated = true
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true
            }
            if (!online) {
                text = host.getString(R.string.offline)
                vpn = false
            } else if (ethernet) {
                text = host.getString(R.string.ethernet)
                icon = R.drawable.ic_ethernet
                flame = 4
                steady = true
            } else if (wifi) {
                val wm = host.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val info = wm?.connectionInfo
                var ssid: String? = info?.ssid
                flame = if (info != null) Math.max(1, Math.min(4, WifiManager.calculateSignalLevel(info.rssi, 5))) else 3
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim()
                    if (ssid.isNotEmpty() && !ssid.contains("unknown") && ssid != "0x") text = ssid
                }
            }
        } catch (_: Exception) {
        }
        // With a VPN app configured, a VPN that is down matters too: show a red "VPN off" capsule then.
        val vpnDown = !vpn && online && vpnApp.isNotEmpty()
        if (vpnPill) updateVpnPill(vpn)
        if ((vpn || vpnDown) && !vpnPill) {
            // Network name, then the green lock-and-VPN (or red "VPN off") capsule drawn in place of the word.
            val badge = host.getString(if (vpn) R.string.vpn else R.string.vpn_off)
            val span = VpnBadge(if (vpn) host.getDrawable(R.drawable.ic_lock) else null, density)
            // The pill has a maximum width and ellipsizes at the end, which would cut the badge off after
            // a long network name, so shorten the name to whatever room the badge leaves. The Wi-Fi icon and its
            // gap are counted by hand: until the pill has a resolved layout direction (the first call, from
            // onResume, runs before that) getTotalPaddingStart/End leave the compound drawables out, which
            // left the name about 31dp too much room and the badge cut off.
            val paint = btnWifi.paint
            val compound = btnWifi.compoundDrawablesRelative
            val firstDrawable = compound[0]
            val iconRoom = if (firstDrawable != null) firstDrawable.intrinsicWidth + btnWifi.compoundDrawablePadding else 0
            val room = (btnWifi.maxWidth - btnWifi.paddingStart - btnWifi.paddingEnd - iconRoom
                    - span.getSize(paint, badge, 0, badge.length, null)).toFloat() - paint.measureText(" ") - 2 * density
            val name = TextUtils.ellipsize(text, paint, Math.max(0f, room), TextUtils.TruncateAt.END).toString()
            // A long badge ("VPN отключён") can leave room for a stub like "MT…", which says nothing and, after
            // rounding, can push the badge itself out of the pill. Show the badge alone then. On Ethernet the
            // icon already names the link, and "Ethernet" cut to "Ethe…" or not depended on the font size (#961),
            // so Ethernet always shows the icon and the badge alone.
            // The space stays even then: a line holding only the span gets no font metrics and collapses.
            val stub = name.length < text.length && name.length < 5
            text = if (stub || icon == R.drawable.ic_ethernet) "" else name
            val label = SpannableString("$text $badge")
            label.setSpan(span, text.length + 1, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (!TextUtils.equals(btnWifi.text, label)) btnWifi.text = label
        } else if (!TextUtils.equals(btnWifi.text, text)) {
            btnWifi.text = text
        }
        if (wifiIconOnly) {
            btnWifi.contentDescription = btnWifi.text
            btnWifi.text = null
        }
        val d = festiveDiya()
        if (d != null) {
            if (online && flame == 0) flame = 3   // online some other way (cellular, USB tethering)
            d.setSteady(steady)
            d.setFlame(flame)
        } else {
            if (icon != wifiIcon) {
                wifiIcon = icon
                btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            }
            var tint: ColorStateList? = if (online) wifiOnlineTint else wifiPlainTint
            // wifi_status_warnings: orange while connected but not validated (a captive portal), red while offline.
            if (wifiNoInternetTint != null) tint = if (!online) wifiOfflineTint else if (validated) wifiOnlineTint else wifiNoInternetTint
            if (wifiOnlineTint != null && btnWifi.compoundDrawableTintList != tint) {
                btnWifi.compoundDrawableTintList = tint
            }
        }
    }

    /** vpn_app's launch intent, or null when none is set or it is not installed. */
    fun vpnAppIntent(): Intent? {
        if (vpnApp.isEmpty()) return null
        val i = host.packageManager.getLeanbackLaunchIntentForPackage(vpnApp)
        return i ?: host.packageManager.getLaunchIntentForPackage(vpnApp)
    }

    /** vpn_pill: the pill outlined in green with a lock while a VPN is up, outlined in red "VPN off" while it is not;
     *  solid with a white ring only while focused. */
    private fun updateVpnPill(vpn: Boolean) {
        val state = VpnBadge.pillState(vpn, vpnAppIntent() != null)
        if (state == VpnBadge.PILL_HIDDEN) {
            if (btnVpn.visibility == View.GONE) return
            val focused = btnVpn.hasFocus()
            btnVpn.visibility = View.GONE
            if (focused) (if (btnWifi.visibility == View.VISIBLE) btnWifi else firstTopButton()).requestFocus()
            return
        }
        if (state != vpnPillState) {
            vpnPillState = state
            val up = state == VpnBadge.PILL_UP
            // The state colour at rest (the pill is an outline then), the contrasting ink on the solid focused fill.
            val ink = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                    intArrayOf(VpnBadge.pillInkColor(up, true), VpnBadge.pillInkColor(up, false)))
            if (iconOnlyVpn) {
                // An open lock on the plain glass button, or the closed lock outlined in green like the pill (solid
                // while focused); the words go to talkback.
                btnVpn.contentDescription = host.getString(if (up) R.string.vpn else R.string.vpn_off)
                btnVpn.background = if (up) VpnBadge.pillBackground(true, density) else vpnPlainBackground!!.newDrawable()
                btnVpn.setCompoundDrawablesRelativeWithIntrinsicBounds(VpnBadge.iconOnlyLock(up), 0, 0, 0)
                btnVpn.compoundDrawableTintList = if (up) ink else vpnPlainInk
                btnVpn.visibility = View.VISIBLE
                return
            }
            btnVpn.setText(if (up) R.string.vpn else R.string.vpn_off)
            btnVpn.setTextColor(ink)
            btnVpn.background = VpnBadge.pillBackground(up, density)
            btnVpn.setCompoundDrawablesRelativeWithIntrinsicBounds(if (up) R.drawable.ic_lock else 0, 0, 0, 0)
            btnVpn.compoundDrawableTintList = ink
        }
        btnVpn.visibility = View.VISIBLE
    }
}
