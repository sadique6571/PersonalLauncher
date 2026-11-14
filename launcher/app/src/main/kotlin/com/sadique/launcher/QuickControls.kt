package com.sadique.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.media.AudioManager
import android.os.Build
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/** Inline media-volume control. */
object QuickControls {
    private const val STREAM = AudioManager.STREAM_MUSIC

    @JvmStatic
    fun attach(host: Activity, rows: LinearLayout): Runnable {
        val audio = host.getSystemService(AudioManager::class.java)
        val maximum = audio?.getStreamMaxVolume(STREAM) ?: 0
        val fixed = audio == null || maximum <= 0 || audio.isVolumeFixed
        val label = GlassSettings.label(host, if (fixed) "Media volume · fixed by device" else "Media volume",
            13, 0xffe0ecf7.toInt())
        val volume = GlassSeekBar(host).apply {
            max = maxOf(1, maximum)
            isEnabled = !fixed
            contentDescription = "Media volume"
            if (audio != null) progress = audio.getStreamVolume(STREAM)
        }
        if (!fixed) label.text = volumeText(volume.progress, maximum)
        addSlider(host, rows, label, volume)
        volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (fixed) return
                label.text = volumeText(value, maximum)
                if (fromUser) {
                    try {
                        audio?.setStreamVolume(STREAM, value, 0)
                    } catch (_: RuntimeException) {
                        Toast.makeText(host, "Volume control is unavailable on this TV", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (audio != null && !fixed) volume.progress = audio.getStreamVolume(STREAM)
            }
        }
        val registered = try {
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            if (Build.VERSION.SDK_INT >= 33) host.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else host.registerReceiver(receiver, filter)
            true
        } catch (_: RuntimeException) {
            false
        }
        return Runnable {
            if (registered) {
                try {
                    host.unregisterReceiver(receiver)
                } catch (_: RuntimeException) {
                    // The receiver may already be released.
                }
            }
        }
    }

    private fun volumeText(value: Int, maximum: Int): String =
        "Media volume · ${Math.round(value * 100f / maximum)}%"

    private fun addSlider(host: Activity, rows: LinearLayout, label: TextView, slider: SeekBar) {
        fun dp(value: Float) = GlassSettings.dp(host, value)
        val card = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GlassSettings.background(host, false)
            setPadding(dp(12f), dp(7f), dp(10f), dp(7f))
        }
        val icon = ImageView(host).apply {
            setImageDrawable(TvSymbols.icon(host, R.drawable.ic_volume))
            setColorFilter(0xfff1f5fb.toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        card.addView(icon, LinearLayout.LayoutParams(dp(24f), dp(24f)))
        val controls = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, 0, 0)
            addView(label)
        }
        slider.progressTintList = ColorStateList.valueOf(0xff55baff.toInt())
        slider.thumbTintList = ColorStateList.valueOf(0xffd4efff.toInt())
        slider.progressBackgroundTintList = ColorStateList.valueOf(0xff2a3d56.toInt())
        val glass = TvGlassStyle.enabled(ThemePreferences.get(host))
        controls.addView(slider, LinearLayout.LayoutParams(-1, dp(if (glass) 36f else 27f)))
        slider.setOnFocusChangeListener { _, focused -> card.background = GlassSettings.background(host, focused) }
        card.addView(controls, LinearLayout.LayoutParams(0, -2, 1f))
        rows.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(3f), 0, dp(3f))
        })
    }
}
