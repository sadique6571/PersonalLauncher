package com.sadique.launcher

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View

object LauncherUpdatesUi {
    @JvmStatic
    fun show(host: Activity) {
        val rows = HomeLibraryUi.rows(host)
        val state = GlassSettings.label(host, "", 14, 0xffe5f2ff.toInt())
        rows.addView(state)
        val handler = Handler(Looper.getMainLooper())
        val panel = GlassSettings.panel(host, "Updates · GitHub", rows)
        fun addRow(title: String, detail: String, icon: Int, action: Runnable) {
            rows.addView(GlassSettings.row(host, title, detail, icon, GlassSettings.themeAccent(host), action))
        }
        addRow("Automatic GitHub updates", "Check and install while Home is idle", R.drawable.ic_settings) {
            GitHubUpdates.enabled(host, !GitHubUpdates.enabled(host))
        }
        addRow("Check now", "Check the latest stable release", R.drawable.ic_internet) {
            panel.dismiss()
            handler.postDelayed({ GitHubUpdates.check(host, true) }, 300)
        }
        addRow("Install update", "Install a verified download", R.drawable.ic_apps) {
            panel.dismiss()
            handler.postDelayed({ GitHubUpdates.installReady(host) }, 300)
        }
        val refresh = object : Runnable {
            override fun run() {
                if (!panel.isShowing || !rows.isAttachedToWindow) return
                val enabled = if (GitHubUpdates.enabled(host)) "On" else "Off"
                val summary = "Automatic updates: $enabled\n${GitHubUpdates.status(host)}"
                if (!summary.contentEquals(state.text)) state.text = summary
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
        panel.show()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }
}
