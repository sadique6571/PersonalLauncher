package com.sadique.launcher

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** Shared volume OSD for the remote services and launcher. */
object VolumeSlider {
    private const val TAG = "PersonalVolume"
    private val handler = Handler(Looper.getMainLooper())
    private var owner: Context? = null
    private var window: WindowManager? = null
    private var capsule: GlassVolumeCapsule? = null
    private var deviceCallback: AudioDeviceCallback? = null
    private var observedAudio: AudioManager? = null
    private var routeChecked = 0L
    private var routeIcon = R.drawable.ic_volume
    private val hideTask = Runnable(::hide)
    private val services = LinkedHashSet<AccessibilityService>()
    private var observerOwner: AccessibilityService? = null
    private var direct: VolumePanelUpdates? = null
    private var maximum = 0
    private var drawnStyle = 0

    @JvmStatic
    fun observe(service: AccessibilityService) {
        services.add(service)
        if (observerOwner != null) return
        val audio = service.getSystemService(AudioManager::class.java) ?: return
        observerOwner = service
        val updates = VolumePanelUpdates(handler::post) { sentAt ->
            if (VolumePanelUpdates.fresh(sentAt, SystemClock.uptimeMillis()) && observerOwner === service) {
                val first = capsule?.visibility != View.VISIBLE
                val shown = show(service, audio)
                if (first && shown) Log.d(TAG, "direct panel UI applied, age=${SystemClock.uptimeMillis() - sentAt}ms")
            }
        }
        direct = updates
        VolumePanelBinder.attach(updates)
        observedAudio = audio
        val callback = object : AudioDeviceCallback() {
            private fun changed() {
                routeChecked = 0
                if (capsule?.visibility == View.VISIBLE) show(service, audio)
            }

            override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>) { changed() }
            override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>) { changed() }
        }
        deviceCallback = callback
        try {
            audio.registerAudioDeviceCallback(callback, handler)
        } catch (_: RuntimeException) {
            deviceCallback = null
        }
        Log.d(TAG, "volume observer connected: ${service.javaClass.simpleName}")
    }

    @JvmStatic
    fun release(service: AccessibilityService) {
        services.remove(service)
        hide(service)
        if (observerOwner !== service) return
        deviceCallback?.let { observedAudio?.unregisterAudioDeviceCallback(it) }
        observedAudio = null
        deviceCallback = null
        direct?.let {
            VolumePanelBinder.detach(it)
            it.close()
        }
        direct = null
        observerOwner = null
        services.firstOrNull()?.let(::observe)
    }

    @JvmStatic
    fun show(context: Context, audio: AudioManager): Boolean {
        val host = observerOwner ?: context
        if (host !is AccessibilityService && host !is Activity) return false
        return try {
            val style = style(host)
            if (owner !== host || drawnStyle != style) dispose()
            val panel = capsule ?: createPanel(host, style)
            val first = panel.visibility != View.VISIBLE
            if (maximum == 0 || first) maximum = maxOf(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            update(host, panel, audio, !first)
            panel.visibility = View.VISIBLE
            handler.removeCallbacks(hideTask)
            handler.postDelayed(hideTask, 1600)
            true
        } catch (unavailable: RuntimeException) {
            Log.w(TAG, "Volume slider window unavailable", unavailable)
            dispose()
            false
        }
    }

    private fun style(context: Context): Int {
        val prefs = ThemePreferences.get(context)
        var style = ThemeStyle.id(prefs)
        style = style * 31 + prefs.getInt("app_font", 0)
        style = style * 31 + prefs.getInt("glass_finish", 0)
        style = style * 31 + prefs.getInt("glass_alpha", 70)
        style = style * 31 + prefs.getInt("glass_edge", 1)
        style = style * 31 + if (PanelTransparency.enabled(prefs)) 1 else 0
        style = style * 31 + prefs.getInt("theme_name", 0)
        return style * 31 + prefs.getInt("custom_accent", 0)
    }

    private fun createPanel(context: Context, style: Int): GlassVolumeCapsule {
        owner = context
        drawnStyle = style
        val manager = checkNotNull(context.getSystemService(WindowManager::class.java))
        window = manager
        val panel = GlassVolumeCapsule(context).apply { visibility = View.INVISIBLE }
        capsule = panel
        val type = if (context is AccessibilityService) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
        val position = WindowManager.LayoutParams(dp(context, 64), dp(context, 218), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT)
        position.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        position.x = dp(context, 20)
        if (context is Activity) position.token = context.window.decorView.windowToken
        manager.addView(panel, position)
        return panel
    }

    private fun update(context: Context, panel: GlassVolumeCapsule, audio: AudioManager, animate: Boolean) {
        val value = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        val now = SystemClock.uptimeMillis()
        if (routeChecked == 0L || now - routeChecked >= 500) {
            routeIcon = AudioOutputIcon.resource(audio)
            routeChecked = now
        }
        val icon = if (muted || value == 0) R.drawable.ic_volume_muted else routeIcon
        panel.state(VolumeLevel.fraction(value, maximum, muted), VolumeLevel.percent(value, maximum, muted),
            muted, icon, GlassSettings.themeAccent(context), animate)
    }

    @JvmStatic
    fun hide(context: Context) {
        if (owner === context) dispose()
    }

    private fun hide() {
        handler.removeCallbacks(hideTask)
        capsule?.let {
            it.stopAnimation()
            it.visibility = View.INVISIBLE
        }
    }

    private fun dispose() {
        handler.removeCallbacks(hideTask)
        capsule?.let {
            it.stopAnimation()
            try {
                window?.removeViewImmediate(it)
            } catch (_: RuntimeException) {
                // Android may have already detached the window.
            }
        }
        owner = null
        window = null
        capsule = null
        maximum = 0
        routeChecked = 0
    }

    private fun dp(context: Context, value: Int): Int = Math.round(value * context.resources.displayMetrics.density)
}
