package com.sadique.launcher

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.SystemClock

/**
 * The boot's app: boot_to_live_tv, or the Open on start app picked in Launcher settings. The first time Home
 * comes up after the TV starts, that app opens instead. Once per boot, keyed on Settings.Global.BOOT_COUNT and
 * committed to disk before the app starts: on a low-memory TV the app can push the launcher out of memory, and
 * an apply() still in flight was lost then, so every Home press opened the app again.
 */
class MainBootOpener(
    private val context: Context,
    private val handler: Handler,
    /** open_on_start_network_wait, in ms; the boot's app while it waits for the network, and until when. */
    private val openOnStartWaitMs: Long,
    /** open_on_start_choice: the app picked in Launcher settings may open on the first Home of each boot. */
    private val openOnStartChoice: Boolean,
    /** boot_to_live_tv: open the Live TV app on the first Home of each boot. */
    private val bootToLiveTv: Boolean,
    private val liveTvPkg: String,
    private val liveTvIntent: () -> Intent?,
    private val open: (Intent) -> Unit,
) {
    @Volatile
    private var startPending: Intent? = null
    private var startDeadline = 0L

    /** Opens the boot's app once the network is validated (networkCallback posts this) or the wait is over. */
    private val openWhenOnline = object : Runnable {
        override fun run() {
            if (startPending == null) return
            if (!OpenOnStart.opensNow(OpenOnStart.online(context), SystemClock.elapsedRealtime(),
                    startDeadline)) return
            handler.removeCallbacks(this)   // the deadline's run, when the network came first
            val i = startPending
            startPending = null
            open(i!!)
        }
    }

    fun openAfterBoot(): Boolean {
        val app = (if (openOnStartChoice) context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(OpenOnStart.KEY, "") else "") ?: ""
        if (!bootToLiveTv && app.isEmpty()) return false
        val boot = OpenOnStart.boot(context)
        val prefs = context.getSharedPreferences(OpenOnStart.BOOT_PREFS, Context.MODE_PRIVATE)
        val target = if (bootToLiveTv) liveTvPkg else app
        if (!OpenOnStart.due(boot, prefs.getInt(OpenOnStart.BOOT_KEY, -1), target, context.packageName)) return false
        if (!prefs.edit().putInt(OpenOnStart.BOOT_KEY, boot).commit()) return false
        val i = if (bootToLiveTv) liveTvIntent() else OpenOnStart.intent(context.packageManager, app)
        if (i == null) return false
        // open_on_start_network_wait: Home stays on screen until the network is back or the wait is over.
        if (openOnStartWaitMs > 0 && !OpenOnStart.online(context)) {
            startPending = i
            startDeadline = SystemClock.elapsedRealtime() + openOnStartWaitMs
            handler.postDelayed(openWhenOnline, openOnStartWaitMs)
            // Validated between the check above and startPending: networkCallback may have skipped it.
            if (OpenOnStart.online(context)) handler.post(openWhenOnline)
            return false
        }
        open(i)
        return true
    }

    /** The network just validated: open the boot's app if one is waiting for it. */
    fun onNetworkValidated() {
        if (startPending != null) handler.post(openWhenOnline)
    }

    /** Leaving Home before the network came back: the boot's app stays closed. */
    fun cancel() {
        handler.removeCallbacks(openWhenOnline)
        startPending = null
    }
}
