package com.sadique.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.Locale

internal class TvInputsPopup(
    private val host: Activity,
    private val root: FrameLayout,
    private val density: Float,
    private val coocaaSources: Boolean,
    private val sourcePanelService: String,
    private val topMenu: Boolean,
    private val inputsConnectedOnly: Boolean,
    private val liveTvInputId: String,
    private val liveTvPkg: String,
    private val liveTvIntent: () -> Intent?,
    private val openSourcePanel: () -> Unit,
    private val mappedSource: (String) -> Int?,
    private val openInput: (String) -> Unit,
    private val open: (Intent) -> Unit,
    private val glassPopup: (View, Int) -> PopupWindow,
    private val dropIn: (PopupWindow, View, View, Int) -> Unit,
) {
    fun show() {
        // A configured source panel and no Live TV row to offer next to it: the panel is the whole menu.
        // The BYINTEK U4 needs host, as its TvInputManager lists three HDMI ports and an AV input it doesn't have.
        val coocaa = if (coocaaSources) CoocaaSources.list(host) else Collections.emptyList<CoocaaSources.Source>()
        if (coocaa.isEmpty() && sourcePanelService.isNotEmpty() && liveTvIntent() == null) {
            openSourcePanel()
            return
        }
        // top_menu: the pill is gone, so the list hangs under the gear, growing inward from the right.
        // Read the switch, not the pill: show_inputs_pill=false hides the pill too, and there the list
        // keeps its usual place.
        val underGear = topMenu
        val anchor = host.findViewById<View>(if (underGear) R.id.btnSettings else R.id.btnInputs)
        val panel = host.layoutInflater.inflate(R.layout.popup_inputs, root, false)
        val rows = panel.findViewById<LinearLayout>(R.id.rows)

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        val width = Math.max(anchor.width, (150 * density).toInt())
        val popup = glassPopup(panel, width)

        val tim = host.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        // inputs_connected_only: ports without a connected device and the AV jacks fold behind a "More inputs" row.
        val connectedOnly = inputsConnectedOnly
        val folded: MutableList<View> = ArrayList()
        var sawNonHdmi = false
        // An explicit root URI asks the Live TV app to leave any HDMI source selected by its own UI.
        val rootChannel = TvContract.Channels.CONTENT_URI.toString() == liveTvInputId
        var tunerId: String? = null
        var tunerIsHw = false
        var tunerIsAnalog = false
        if (tim != null) {
            val inputs = ArrayList(tim.tvInputList)
            // HDMI ports first in port order, then AV and the rest.
            inputs.sortWith(Comparator { a, b ->
                val ha = a.type == TvInputInfo.TYPE_HDMI
                val hb = b.type == TvInputInfo.TYPE_HDMI
                if (ha != hb) {
                    if (ha) -1 else 1
                } else {
                    a.loadLabel(host).toString().compareTo(b.loadLabel(host).toString(), ignoreCase = true)
                }
            })
            // An HDMI-CEC device (an Apple TV, a soundbar) gets an input of its own whose parent is the
            // port it sits on, so the port and the device would each get a row (Vestel MT7332: "HDMI 1"
            // and "HDMI Apple TV"). The device takes its port's row instead, and a bare port is listed
            // only when nothing on it speaks CEC.
            val inputIds = HashSet<String>()
            for (info in inputs) inputIds.add(info.id)
            val cecDevices: MutableMap<String, MutableList<TvInputInfo>> = HashMap()
            for (info in inputs) {
                val parent = info.parentId
                if (parent == null || !inputIds.contains(parent)) continue
                if (!info.isPassthroughInput || info.isHidden(host)) continue
                var onPort = cecDevices[parent]
                if (onPort == null) {
                    onPort = ArrayList()
                    cecDevices[parent] = onPort
                }
                onPort.add(info)
            }
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            var hdmiPorts = 0
            var hdmiConnected = 0
            for (info in inputs) {
                if (!info.isPassthroughInput || info.type != TvInputInfo.TYPE_HDMI) continue
                if (info.parentId != null && inputIds.contains(info.parentId)) continue
                hdmiPorts++
                if (tim.getInputState(info.id) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++
            }
            val hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts
            for (info in inputs) {
                if (info.type == TvInputInfo.TYPE_TUNER
                        && (info.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        && (liveTvInputId.isEmpty() || rootChannel || liveTvInputId == info.id)) {
                    // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                    // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                    // Among tuners of the same kind, skip an analog input when another is present.
                    // MediaTek names them AnalogInputService and TunerInputService; alphabetical order
                    // would pick the analog one, and Live TV then opens a dead ATV source.
                    val hw = info.id.matches(".*/HW\\d+$".toRegex())
                    val analog = info.id.lowercase(Locale.ROOT).contains("analog")
                    val currentTuner = tunerId
                    val better = currentTuner == null
                            || (hw && !tunerIsHw)
                            || (hw == tunerIsHw && tunerIsAnalog && !analog)
                            || (hw == tunerIsHw && analog == tunerIsAnalog
                                    && info.id.compareTo(currentTuner) < 0)
                    if (better) {
                        tunerId = info.id
                        tunerIsHw = hw
                        tunerIsAnalog = analog
                    }
                }
                // Inputs hidden in the TV's Settings > Inputs stay out of the menu too.
                if (!info.isPassthroughInput || info.isHidden(host)) continue
                // input_sources can leave out ports the board reports but the set does not have ("HW8=-").
                if (mappedSource(info.id) == -1) continue
                // A CEC device's row is added with its port, below.
                if (info.parentId != null && inputIds.contains(info.parentId)) continue
                val l = info.loadLabel(host)
                var label = l?.toString() ?: info.id
                val hdmi = info.type == TvInputInfo.TYPE_HDMI
                if (hdmi && !label.uppercase(Locale.ROOT).contains("HDMI")) label = "HDMI $label"
                val custom = InputNames.tidy(info.loadCustomLabel(host))
                val named = custom != null && custom.isNotEmpty() && custom != label
                if (!hdmi && rows.childCount > 0 && !sawNonHdmi) {
                    val line = addSeparator(rows)
                    if (connectedOnly) fold(line, folded)
                }
                if (!hdmi) sawNonHdmi = true
                val devices = cecDevices[info.id]
                if (devices != null) {
                    // "Apple TV · HDMI 1": the device's own name (or the one given to it or to its port in
                    // Settings > Inputs), then the port. Opening the device's input also makes it the CEC
                    // active source, which wakes it.
                    for (device in devices) {
                        var name: CharSequence? = InputNames.tidy(device.loadCustomLabel(host))
                        if (name == null || name.isEmpty()) name = if (named) custom else device.loadLabel(host)
                        val deviceLabel = if (name == null || name.isEmpty()) label else "$name \u00B7 $label"
                        val deviceId = device.id
                        val state = if (hdmiStateBogus) -1 else tim.getInputState(deviceId)
                        val row = addTvInputRow(host, rows, popup, R.drawable.ic_input, deviceLabel, state) { openInput(deviceId) }
                        if (connectedOnly && InputFold.folds(true, state)) fold(row, folded)
                    }
                    continue
                }
                // The name given in Settings > Inputs (e.g. "MacBook Pro") goes first, the port after it.
                if (named) label = custom!! + " \u00B7 " + label
                val id = info.id
                // Analog AV jacks have no hotplug detection, and some TVs report them as always connected
                // (TCL Smart TV Pro G03 says "Connected" with nothing plugged in), so AV rows show no state.
                val state = if (!hdmi || hdmiStateBogus) -1 else tim.getInputState(id)
                val row = addTvInputRow(host, rows, popup, if (hdmi) R.drawable.ic_input else R.drawable.ic_av, label, state) { openInput(id) }
                if (connectedOnly && InputFold.folds(hdmi, state)) fold(row, folded)
            }
        }
        if (folded.isNotEmpty()) {
            val more = host.layoutInflater.inflate(R.layout.item_input, rows, false)
            more.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_input)
            more.findViewById<TextView>(R.id.title).setText(R.string.more_inputs)
            more.findViewById<View>(R.id.status).visibility = View.GONE
            more.findViewById<View>(R.id.dot).visibility = View.GONE
            more.setOnClickListener {
                for (f in folded) f.visibility = View.VISIBLE
                rows.removeView(more)
                for (f in folded) {
                    if (f.isFocusable) { f.requestFocus(); break }
                }
            }
            rows.addView(more)
        }
        // Coocaa OS registers nothing with TvInputManager; com.skyworth.tv lists its sources instead.
        if (rows.childCount == 0) {
            for (s in coocaa) {
                val icon = if ("HDMI" == s.name) R.drawable.ic_input
                        else if ("VOD" == s.name) R.drawable.ic_livetv else R.drawable.ic_av
                addTvInputRow(host, rows, popup, icon, s.label, -1) {
                    if (!CoocaaSources.open(host, s)) {
                        Toast.makeText(host, R.string.not_available, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        // Nothing to list where there is no TvInputManager: the TV's own source panel switches inputs.
        if (rows.childCount == 0 && sourcePanelService.isNotEmpty()) {
            addTvInputRow(host, rows, popup, R.drawable.ic_input, host.getString(R.string.inputs), -1, Runnable { openSourcePanel() })
        }
        var liveTv = liveTvIntent()
        if (liveTv != null && tunerId != null) {
            // Tune that input only when the Live TV package itself handles the URI.
            // The unrestricted input player also resolves it (that is how HDMI opens), but on the
            // AIWA AI PONT SA that player stays black, and the Live TV package does not handle the
            // URI at all, which used to toast "Not available on host TV". Keep the leanback activity
            // then: it is the same path as the Live TV tile. Do not drop the package restriction.
            // An explicit tuner uses its channel collection (e.g. DVB-S rather than DVB-T/C).
            // Keep the legacy passthrough route for automatic selection: some OEM players need it.
            // If the configured input is absent, tunerId stays null and the app opens normally.
            val configured = liveTvInputId.isNotEmpty() && !rootChannel
            val tuned = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(if (configured) TvContract.buildChannelsUriForInput(tunerId)
                                    else TvContract.buildChannelUriForPassthroughInput(tunerId),
                            if (configured) TvContract.Channels.CONTENT_TYPE else TvContract.Channels.CONTENT_ITEM_TYPE)
                    .setPackage(liveTvPkg)
            val handler = tuned.resolveActivity(host.packageManager)
            if (handler != null) {
                // On some MediaTek sets the tuner passthrough URI leaves the app's HDMI source selected.
                // The stock launcher addresses the same activity with the root channel URI instead (#1941).
                liveTv = if (rootChannel)
                        Intent(Intent.ACTION_VIEW).setData(TvContract.Channels.CONTENT_URI).setComponent(handler)
                        else tuned
            }
        }
        if (liveTv != null) {
            if (rows.childCount > 0) addSeparator(rows)
            val liveTvIntent = liveTv
            addTvInputRow(host, rows, popup, R.drawable.ic_livetv, host.getString(R.string.live_tv), -1) { open(liveTvIntent) }
        }
        if (rows.childCount == 0) {
            Toast.makeText(host, R.string.no_inputs, Toast.LENGTH_SHORT).show()
            return
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.width = Math.max(width, Math.min(panel.measuredWidth, (360 * density).toInt()))

        val alignment = if (host.resources.getBoolean(R.bool.rtl_menu_bar) != underGear) Gravity.END else Gravity.START
        dropIn(popup, panel, anchor, alignment)
        for (i in 0 until rows.childCount) {
            val child = rows.getChildAt(i)
            if (child.visibility == View.VISIBLE && child.isFocusable) { child.requestFocus(); break }
        }
    }

    private fun addSeparator(rows: LinearLayout): View {
        val line = View(host)
        line.setBackgroundColor(0x1FFFFFFF)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, density.toInt())
        val m = (6 * density).toInt()
        val h = (12 * density).toInt()
        lp.setMargins(h, m, h, m)
        rows.addView(line, lp)
        return line
    }

    private fun fold(view: View, folded: MutableList<View>) {
        view.visibility = View.GONE
        folded.add(view)
    }
}

internal fun addTvInputRow(host: Activity, rows: LinearLayout, popup: PopupWindow, iconRes: Int, title: String, state: Int, action: Runnable): View {
    val row = host.layoutInflater.inflate(R.layout.item_input, rows, false)
    row.findViewById<ImageView>(R.id.icon).setImageResource(iconRes)
    row.findViewById<TextView>(R.id.title).text = title
    val status = row.findViewById<TextView>(R.id.status)
    val dot = row.findViewById<View>(R.id.dot)
    // This TV reports "standby" for every port whether or not something is plugged in, so only
    // definite states are shown.
    if (state == TvInputManager.INPUT_STATE_CONNECTED) {
        status.setText(R.string.connected)
        dot.visibility = View.VISIBLE
    } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
        status.setText(R.string.not_connected)
        dot.visibility = View.GONE
    } else {
        status.visibility = View.GONE
        dot.visibility = View.GONE
    }
    row.setOnClickListener {
        popup.dismiss()
        action.run()
    }
    rows.addView(row)
    return row
}
