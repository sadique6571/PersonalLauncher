package com.sadique.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.widget.Toast

internal class TvInputActions(
    private val host: Activity,
    private val config: Config,
    private val open: (Intent) -> Unit,
) {
    /** The Live TV app's launch intent: its leanback entry point, or the configured action if it has none. */
    fun liveTvIntent(): Intent? {
        if (config.liveTvPackage.isEmpty()) return null
        val i = host.packageManager.getLeanbackLaunchIntentForPackage(config.liveTvPackage)
        if (i != null || config.liveTvAction.isEmpty()) return i
        val byAction = Intent(config.liveTvAction).setPackage(config.liveTvPackage)
        return if (host.packageManager.resolveActivity(byAction, 0) != null) byAction else null
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9): a
     *  service, or an activity on sets whose panel is a dialog (BYINTEK U4's QuickSourceActivity). */
    fun openSourcePanel() {
        val i = Intent(config.sourcePanelAction)
        val c = ComponentName.unflattenFromString(config.sourcePanelService)
        if (c != null) i.component = c
        val eq = config.sourcePanelExtra.indexOf('=')
        if (eq > 0) {
            try {
                i.putExtra(config.sourcePanelExtra.substring(0, eq).trim(),
                        config.sourcePanelExtra.substring(eq + 1).trim().toInt())
            } catch (_: NumberFormatException) {
            }
        }
        try {
            if (host.packageManager.resolveActivity(i, 0) != null) {
                host.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            host.startService(i)
        } catch (_: Exception) {
            Toast.makeText(host, R.string.not_available, Toast.LENGTH_SHORT).show()
        }
    }

    /** `input_sources` in config.xml or a theme: "HW5=23" maps the framework input whose id ends in /HW5 to a vendor
     *  source number; "HW8=-" leaves that input out of the Inputs menu (a port the board reports but the set
     *  does not have). Returns the number, -1 for a left-out input, or null when the input is not listed. */
    fun mappedSource(inputId: String): Int? {
        return InputSources.mapped(config.inputSources, inputId)
    }

    /** Switches to a passthrough input (HDMI, AV). An input mapped in `input_sources` is opened by sending
     *  `input_action` with its source number as the int extra "input" (Xiaomi China firmware, whose tvplayer
     *  answers com.xiaomi.mitv.tvplayer.EXTSRC_PLAY and nothing else). Otherwise the standard way is
     *  ACTION_VIEW on its channel URI, which the Live TV app handles; where nothing takes that, the
     *  configured `input_action` is sent with the same URI instead (XGIMI's HDMI player only answers
     *  com.xgimi.action.hdmiPlayer). With `input_uri_scheme` set (YaOS: live-tv) the same URI goes out
     *  with that scheme and no MIME type. A player guarded by a permission this build does not declare
     *  throws SecurityException, which used to take Home down with it (YaOS before the manifest asked
     *  for ACCESS_TV_SOURCE). */
    fun openInput(inputId: String) {
        // A CEC device is a child input of its HDMI port; the vendor source number belongs to the port
        // (MiTV-MFFU0). Only that lookup uses the parent: the generic path below still opens the child.
        var sourceId = inputId
        val tim = host.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        if (tim != null) {
            try {
                val info = tim.getTvInputInfo(inputId)
                if (info != null && info.parentId != null) sourceId = info.parentId
            } catch (_: RuntimeException) {
            }
        }
        val source = mappedSource(sourceId)
        if (source != null && source >= 0 && config.inputAction.isNotEmpty()) {
            try {
                host.startActivity(Intent(config.inputAction).putExtra("input", source)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // fall through to the standard way
            } catch (_: SecurityException) {
                // fall through to the standard way
            }
        }
        val uri = TvContract.buildChannelUriForPassthroughInput(inputId)
        val view = (if (config.inputUriScheme.isEmpty())
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                else Intent(Intent.ACTION_VIEW, uri.buildUpon().scheme(config.inputUriScheme).build()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Aim it at the TV app first: apps installed later (a streaming app, a file manager) can claim the same
        // channel URI too, and with two or more handlers Android shows a chooser instead of the input (WhaleOS).
        if (config.liveTvPackage.isNotEmpty()) {
            try {
                host.startActivity(Intent(view).setPackage(config.liveTvPackage))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        try {
            host.startActivity(view)
            return
        } catch (_: ActivityNotFoundException) {
            if (config.inputAction.isEmpty()) {
                Toast.makeText(host, R.string.not_available, Toast.LENGTH_SHORT).show()
                return
            }
        } catch (_: SecurityException) {
            if (config.inputAction.isEmpty()) {
                Toast.makeText(host, R.string.not_available, Toast.LENGTH_SHORT).show()
                return
            }
        }
        open(Intent(config.inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel"))
    }
}
