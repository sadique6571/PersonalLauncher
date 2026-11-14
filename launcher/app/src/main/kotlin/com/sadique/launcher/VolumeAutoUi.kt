package com.sadique.launcher

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast

/** Presents the existing volume recovery settings and status. */
object VolumeAutoUi {
    @JvmStatic
    fun show(host: Activity) {
        if (Build.VERSION.SDK_INT < 30) {
            Toast.makeText(host, "Automatic wireless pairing needs Android 11 or newer", Toast.LENGTH_LONG).show()
            return
        }
        val rows = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val status = GlassSettings.label(host, VolumeAutoStart.status(host), 14, 0xffe5f2ff.toInt())
        rows.addView(status)
        val explanation = GlassSettings.label(host,
            "Auto-start uses this TV's local wireless debugging. One-time pairing authorizes local ADB access. Home & remote setup can reuse this connection to keep the launcher as Home and restore its remote permissions. Port changes are detected automatically.",
            12, 0xffb7cbdf.toInt())
        explanation.setPadding(0, 12, 0, 12)
        rows.addView(explanation)
        fun row(title: String, detail: String, icon: Int, action: Runnable) {
            rows.addView(GlassSettings.row(host, title, detail, icon, GlassSettings.themeAccent(host), action))
        }
        row("Pairing setup", "Open Wireless debugging → Pair device with pairing code. Keep that dialog open while completing the one-time setup from your existing ADB connection.",
            R.drawable.ic_settings) {
            try {
                host.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (_: RuntimeException) {
                Toast.makeText(host, "Open Developer options from TV settings", Toast.LENGTH_LONG).show()
            }
        }
        row("Enable / Start now", "Requires completed pairing and the existing settings grant", R.drawable.ic_volume) {
            VolumeAutoStart.enable(host)
        }
        row("Disable auto-start", "Leave the current helper running; skip future automatic starts", R.drawable.ic_settings) {
            VolumeAutoStart.disable(host)
        }
        val dialog = GlassSettings.panel(host, "Volume auto-start", rows)
        val handler = Handler(Looper.getMainLooper())
        val refresh = object : Runnable {
            override fun run() {
                if (!dialog.isShowing || !rows.isAttachedToWindow) return
                status.text = "Last result: ${VolumeAutoStart.status(host)}"
                handler.postDelayed(this, 1000)
            }
        }
        rows.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                handler.removeCallbacks(refresh)
                handler.post(refresh)
            }

            override fun onViewDetachedFromWindow(view: View) {
                handler.removeCallbacks(refresh)
            }
        })
        dialog.show()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }
}
