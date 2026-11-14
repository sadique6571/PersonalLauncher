package com.sadique.launcher

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.function.IntConsumer

/** Quick controls with theme-specific layout and deterministic D-pad navigation. */
class ControlCenterGrid(private val host: Activity, private val open: IntConsumer) : LinearLayout(host) {
    private val ids = IntArray(ACTIONS.size) { View.generateViewId() }
    private var style = -1
    private val main = Handler(Looper.getMainLooper())
    private var watching = false
    private var bluetooth: GlassBluetooth? = null
    private var wifiTile: View? = null
    private var wifiLabel: TextView? = null
    private var bluetoothLabel: TextView? = null
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { main.post(::status) }
        override fun onLost(network: Network) { main.post(::status) }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { main.post(::status) }
    }

    init {
        orientation = VERTICAL
        refresh()
    }

    private fun manager(): ConnectivityManager? = host.getSystemService(ConnectivityManager::class.java)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        manager()?.let {
            try {
                it.registerDefaultNetworkCallback(network)
                watching = true
            } catch (_: RuntimeException) {
                // Some firmware denies network observation.
            }
        }
        bluetooth = GlassBluetooth(host, ::status).also { it.start() }
        status()
    }

    override fun onDetachedFromWindow() {
        if (watching) {
            manager()?.unregisterNetworkCallback(network)
            watching = false
        }
        bluetooth?.stop()
        bluetooth = null
        main.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    fun status() {
        if (style != ThemeStyle.GLASS && style != ThemeStyle.HARMONY) return
        val tile = wifiTile ?: return
        val manager = manager()
        val caps = manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        wifiLabel?.text = (if (ethernet) "Ethernet" else "Wi-Fi") + " • " + if (online) "Online" else "No Internet"
        tile.background = if (style == ThemeStyle.HARMONY) HarmonyVisionSurface.control(host, tile.isFocused)
        else GradientDrawable().apply {
            setColor(if (online) 0x8014532d.toInt() else 0x947f1d1d.toInt())
            cornerRadius = dp(22).toFloat()
            setStroke(dp(if (tile.isFocused) 2 else 1), if (online) 0xb322c55e.toInt() else 0xccef4444.toInt())
        }
        bluetooth?.let { bluetoothLabel?.text = it.connectedName() }
    }

    fun refresh() {
        val next = ThemeStyle.id(ThemePreferences.get(host))
        if (style == next) return
        val focusedId = findFocus()?.id ?: View.NO_ID
        style = next
        wifiTile = null
        wifiLabel = null
        bluetoothLabel = null
        removeAllViews()
        val layout = ControlCenterLayout.rows(style)
        for ((rowIndex, items) in layout.withIndex()) {
            val row = LinearLayout(host).apply {
                orientation = HORIZONTAL
                isBaselineAligned = false
            }
            val gap = dp(when (style) {
                ThemeStyle.COBALT -> 2
                ThemeStyle.HARMONY -> 5
                else -> 4
             })
            for ((column, item) in items.withIndex()) {
                val tile = tile(item, items.size)
                tile.id = ids[item]
                tile.nextFocusLeftId = ids[items[maxOf(0, column - 1)]]
                tile.nextFocusRightId = ids[items[minOf(items.lastIndex, column + 1)]]
                layout.getOrNull(rowIndex - 1)?.let { above ->
                    tile.nextFocusUpId = ids[above[ControlCenterLayout.neighbour(column, items.size, above.size)]]
                }
                layout.getOrNull(rowIndex + 1)?.let { below ->
                    tile.nextFocusDownId = ids[below[ControlCenterLayout.neighbour(column, items.size, below.size)]]
                }
                row.addView(tile, LayoutParams(0, -1, 1f).apply { setMargins(gap, gap, gap, gap) })
            }
            val height = when {
                style == ThemeStyle.HARMONY -> 88
                style == ThemeStyle.CLASSIC -> 64
                ControlCenterLayout.stacked(style) -> 100
                items.size == 1 -> 76
                else -> 88
            }
            addView(row, LayoutParams(-1, dp(height)))
        }
        status()
        if (focusedId != View.NO_ID) findViewById<View>(focusedId)?.requestFocus()
    }

    fun surface(focused: Boolean): GradientDrawable {
        val prefs = ThemePreferences.get(host)
        val radius = dp(ControlCenterLayout.radius(ThemeStyle.id(prefs)))
        if (ThemeStyle.harmony(prefs)) return HarmonyVisionSurface.control(host, focused)
        if (ThemeStyle.id(prefs) == ThemeStyle.GLASS) {
            return GlassOsUi.panel(host, 22).apply {
                val edge = (Math.round((TvGlassStyle.edge(prefs) ushr 24) * .47f) shl 24) or 0xffffff
                setStroke(dp(if (focused) 2 else 1), if (focused) 0xccffffff.toInt() else edge)
            }
        }
        if (ThemeStyle.custom(prefs)) return DemoCardSurface(radius, dp(1).toFloat(), focused, prefs, false)
        return GlassSettings.background(host, focused).apply { cornerRadius = radius.toFloat() }
    }

    private fun tile(item: Int, columns: Int): View {
        val vertical = ControlCenterLayout.stacked(style) || columns == 3
        val titleText = host.getString(TITLES[item])
        val tile = LinearLayout(host).apply {
            orientation = if (vertical) VERTICAL else HORIZONTAL
            gravity = if (vertical) Gravity.CENTER else Gravity.CENTER_VERTICAL
            val pad = dp(if (columns == 3) 7 else 12)
            setPadding(pad, pad, pad, pad)
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            contentDescription = titleText
            tag = "control_center_tile"
            background = surface(false)
            setOnFocusChangeListener { _, focused ->
                background = surface(focused)
                status()
            }
        }
        val icon = ImageView(host).apply {
            setImageDrawable(TvSymbols.icon(host, ICONS[item]))
            setTag(R.id.theme_symbol_resource, ICONS[item])
            tag = "theme_accent"
            setColorFilter(GlassSettings.themeAccent(host))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val size = dp(if (columns == 3) 22 else 26)
        tile.addView(icon, LayoutParams(size, size).apply {
            if (vertical) bottomMargin = dp(if (ControlCenterLayout.stacked(style)) 5 else 4)
            else marginEnd = dp(8)
        })
        val title = GlassSettings.label(host, titleText, if (columns == 3) 11 else 13, -1).apply {
            gravity = if (vertical) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            if (style == ThemeStyle.COBALT || style == ThemeStyle.AMBER || style == ThemeStyle.HARMONY) {
                setTypeface(typeface, Typeface.BOLD)
            }
        }
        if (item == 0) {
            wifiTile = tile
            wifiLabel = title
        }
        if (item == 1) bluetoothLabel = title
        tile.addView(title, LayoutParams(if (vertical) -1 else 0, -2, if (vertical) 0f else 1f))
        tile.setOnClickListener {
            it.requestFocus()
            open.accept(ACTIONS[item])
        }
        return tile
    }

    private fun dp(value: Int): Int = GlassSettings.dp(host, value.toFloat())

    private companion object {
        val TITLES = intArrayOf(R.string.h_wifi, R.string.h_bluetooth, R.string.h_inputs,
            R.string.h_display, R.string.h_sound, R.string.h_screensaver)
        val ICONS = intArrayOf(R.drawable.ic_wifi, android.R.drawable.stat_sys_data_bluetooth, R.drawable.ic_input,
            R.drawable.ic_picture, R.drawable.ic_volume, R.drawable.ic_picture)
        val ACTIONS = intArrayOf(HomeDashboard.WIFI, HomeDashboard.BLUETOOTH, HomeDashboard.INPUTS,
            HomeDashboard.DISPLAY, HomeDashboard.SOUND, HomeDashboard.SCREENSAVER)
    }
}
