package com.sadique.launcher

import android.content.Context
import android.content.Intent
import android.media.tv.TvInputManager
import android.os.Handler
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView

/**
 * The top bar's pills: what the network and VPN pills open, whether the Inputs pill has anything to list,
 * the icon-only pill shape, and the pill refresh that runs after the first frame back on Home.
 */
class MainTopPills(
    private val context: Context,
    private val handler: Handler,
    private val root: FrameLayout,
    private val density: Float,
    private val btnInputs: View,
    private val networkPills: () -> LauncherNetworkPills,
    private val inputActions: TvInputActions,
    private val sourcePanelService: String,
    private val coocaaSources: Boolean,
    /** config show_inputs_pill: false keeps the Inputs pill hidden even when the TV has inputs. */
    var showInputsPill: Boolean,
    private val appLibraryEnabled: Boolean,
    private val open: (Intent) -> Unit,
    /** The App library, opened from the VPN pill: closing it returns there while the pill still shows. */
    private val openLibraryFromVpn: () -> Unit,
    private val isDestroyed: () -> Boolean,
) {
    /** Turns a labelled top pill into the round TopButtonIcon shape: no text, the icon centred by even padding. */
    fun makeIconOnly(pill: TextView) {
        pill.contentDescription = pill.text
        pill.text = null
        pill.maxWidth = Int.MAX_VALUE
        val pad = Math.round(10 * density)
        pill.setPaddingRelative(pad, 0, pad, 0)
        pill.compoundDrawablePadding = 0
        pill.layoutParams.width = Math.round(42 * density)
    }

    /**
     * The network pill opens the VPN app when one is configured and installed, otherwise Wi-Fi settings. With
     * vpn_pill the VPN app has its own pill, so this one always opens Wi-Fi settings.
     */
    fun openNetworkPill() {
        val i = if (networkPills().vpnPill) null else networkPills().vpnAppIntent()
        open(i ?: SystemSettings.intent(context, Settings.ACTION_WIFI_SETTINGS))
    }

    /**
     * vpn_pill: the VPN app, where it is switched on and off. Without one installed, the App library to pick from, or
     * Android's VPN settings when the theme turned the library off.
     */
    fun openVpnPill() {
        val i = networkPills().vpnAppIntent()
        if (i != null || !appLibraryEnabled) {
            open(i ?: SystemSettings.intent(context, Settings.ACTION_VPN_SETTINGS))
            return
        }
        openLibraryFromVpn()
    }

    /** Hides the Inputs pill when it would have nothing to list: streaming boxes have no HDMI-in, AV or tuner. */
    fun updateInputsButton() {
        if (!showInputsPill) {
            btnInputs.visibility = View.GONE
            return
        }
        var any = inputActions.liveTvIntent() != null || sourcePanelService.isNotEmpty()
                || (coocaaSources && CoocaaSources.list(context).isNotEmpty())
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        if (!any && tim != null) {
            for (info in tim.tvInputList) {
                // Inputs hidden in Settings > Inputs are left out of the menu, so they don't count here either.
                if (info.isPassthroughInput && !info.isHidden(context)) {
                    any = true
                    break
                }
            }
        }
        btnInputs.visibility = if (any) View.VISIBLE else View.GONE
    }

    fun refreshAfterNextDraw() {
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                // Posted from inside the frame's traversal, so it runs after that frame is drawn. A post straight
                // from onResume would run first: the frame is only scheduled after onResume returns.
                handler.post {
                    if (!isDestroyed()) {
                        networkPills().refresh()
                        updateInputsButton()
                    }
                }
                return true
            }
        })
        root.invalidate()   // a resume that changes nothing on screen still draws once, so the refresh runs
    }
}
