package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.Toast

/** Displays a translucent panel over the foreground app without raising the Home task. */
class QuickSettingsActivity : Activity() {
    private var panel: AlertDialog? = null
    private var gate: AlertDialog? = null
    private var authorized = false
    private val main = Handler(Looper.getMainLooper())

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LauncherLocale.wrap(base))
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        overridePendingTransition(0, 0)
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        })
        val prompt = LauncherPin.require(this) {
            if (isFinishing || isDestroyed) return@require
            authorized = true
            showPanel()
        }
        gate = prompt
        if (prompt != null) {
            prompt.setOnDismissListener { main.post { if (!authorized) finish() } }
        } else if (!authorized) {
            finish()
        }
    }

    private fun showPanel() {
        val dialog = HomeDashboard.show(this, network(), Runnable {}, ::open, true) { action ->
            action == HomeDashboard.INPUTS || action == HomeDashboard.DISPLAY || action == HomeDashboard.SOUND
        }
        panel = dialog
        dialog.setOnDismissListener { finish() }
    }

    private fun open(action: Int) {
        when (action) {
            HomeDashboard.SETTINGS -> openActivity(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS))
            HomeDashboard.WIFI -> openActivity(SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS))
            HomeDashboard.BLUETOOTH -> {
                SystemSettings.openBluetoothAccessories(this)
                finish()
            }
            HomeDashboard.INPUTS -> inputs()
            HomeDashboard.DISPLAY -> TvControlPreview.show(this, true)
            HomeDashboard.SOUND -> TvControlPreview.show(this, false)
            HomeDashboard.SCREENSAVER -> openActivity(Intent(this, LandscapeActivity::class.java))
            HomeDashboard.STORE -> {
                val store = packageManager.getLaunchIntentForPackage("com.android.vending")
                openActivity(store ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://apps")))
            }
            else -> openActivity(SystemSettings.intent(this, Settings.ACTION_SETTINGS))
        }
    }

    private fun network(): String {
        val manager = getSystemService(ConnectivityManager::class.java)
        val active = manager?.activeNetwork ?: return "Disconnected"
        val caps = manager.getNetworkCapabilities(active) ?: return "Disconnected"
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            "Connected"
        } else {
            "Network connected · internet not verified"
        }
    }

    private fun inputs() {
        val rows = HomeLibraryUi.rows(this)
        val manager = getSystemService(TvInputManager::class.java)
        for (input in manager?.tvInputList.orEmpty()) {
            if (input.isHidden(this)) continue
            val uri = if (input.isPassthroughInput) {
                TvContract.buildChannelUriForPassthroughInput(input.id)
            } else TvContract.Channels.CONTENT_URI
            rows.addView(GlassSettings.row(this, input.loadLabel(this).toString(), null, R.drawable.ic_input,
                GlassSettings.themeAccent(this)) { openActivity(Intent(Intent.ACTION_VIEW, uri)) })
        }
        if (rows.childCount == 0) {
            rows.addView(GlassSettings.row(this, "Open TV input settings", null, R.drawable.ic_input,
                GlassSettings.themeAccent(this)) { openActivity(SystemSettings.intent(this, Settings.ACTION_SETTINGS)) })
        }
        GlassSettings.panel(this, getString(R.string.h_inputs), rows).show()
    }

    private fun openActivity(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finish()
    }

    override fun onPause() {
        super.onPause()
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        gate?.apply {
            setOnDismissListener(null)
            dismiss()
        }
        panel?.apply {
            setOnDismissListener(null)
            dismiss()
        }
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
