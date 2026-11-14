package com.sadique.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Android owns the selected dream; normal app permissions cannot replace it silently. */
class ScreensaverSetupActivity : Activity() {
    private lateinit var state: TextView

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = GlassSettings.dp(this@ScreensaverSetupActivity, 24f)
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(0xff071426.toInt())
        }

        page.addView(GlassSettings.label(this, "Automatic landscape screensaver", 24, 0xffffffff.toInt()))
        state = GlassSettings.label(this, "", 15, 0xffa9cfe2.toInt())
        page.addView(state)
        page.addView(
            GlassSettings.label(
                this,
                "In Android screensaver settings, choose Personal Launcher Landscapes, enable automatic start, and choose the idle timeout. The old screensaver stays active until Android's selection changes. This is a system selection, not a normal permission prompt.",
                16,
                0xffd9e8f4.toInt()
            )
        )
        page.addView(
            GlassSettings.row(
                this,
                "Select in Android settings",
                "Choose Personal Launcher Landscapes",
                R.drawable.ic_settings,
                0xff40d9ff.toInt()
            ) {
                try {
                    startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
                } catch (_: RuntimeException) {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (_: RuntimeException) {
                        Toast.makeText(this, "This device does not expose screensaver settings", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
        page.addView(
            GlassSettings.row(
                this,
                "Start after 5 minutes on Home",
                "Home-only fallback if your TV restricts system screensavers",
                R.drawable.ic_picture,
                0xff40d9ff.toInt()
            ) {
                ThemePreferences.get(this).edit().putInt("idle_screensaver_minutes", 5).apply()
                Toast.makeText(this, "Home idle slideshow enabled after 5 minutes", Toast.LENGTH_LONG).show()
            }
        )
        page.addView(
            GlassSettings.row(
                this,
                "Preview landscapes",
                "Preview does not change Android's selection",
                R.drawable.ic_picture,
                0xff40d9ff.toInt()
            ) {
                startActivity(Intent(this, LandscapeActivity::class.java))
            }
        )
        setContentView(page)
    }

    override fun onResume() {
        super.onResume()
        try {
            val components = Settings.Secure.getString(contentResolver, "screensaver_components")
            var selected = false
            if (components != null) {
                for (value in components.split(",")) {
                    val component = ComponentName.unflattenFromString(value.trim())
                    if (ComponentName(this, LandscapeDream::class.java) == component) {
                        selected = true
                    }
                }
            }
            val enabled = Settings.Secure.getInt(contentResolver, "screensaver_enabled", 0)
            state.text = if (selected) {
                if (enabled != 0) "Android selection: Personal Launcher Landscapes · enabled"
                else "Landscapes selected · enable screensaver in Android settings"
            } else {
                "Android has not selected Personal Launcher Landscapes"
            }
        } catch (_: RuntimeException) {
            state.text = "Check the selected screensaver in Android settings; this TV restricts reading its selection."
        }
    }
}
