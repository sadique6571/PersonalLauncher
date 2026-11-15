package com.sadique.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * With stock_home_redirect set, it also sends the stock launcher's home screen back here, for firmware that turns
 * that launcher on again after it was disabled (XGIMI's xrmservice does, on its own remote keys), or that cannot be
 * disabled at all, and a short press of Home opens this launcher even when the stock home is already on screen.
 *
 * With power_on_home set, a Live TV window (live_tv_package) that the firmware opens by itself at power-on is sent
 * back here too, until the first key press after boot or after the screen turns on (Changhong / Homwee boards).
 * With power_on_home_always set, Home comes up at every power-on whatever is in front, for TVs that resume their
 * last source: a Philips back on an HDMI input sends no window event until the input has a picture.
 *
 * With home_long_press_home set, it takes every Home press, so one held past the long-press timeout opens this launcher
 * instead of Android's ALL_APPS action, which nothing answers once the stock launcher is disabled (Philips TPM191E).
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.sadique.launcher/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
class HomeKeyService : AccessibilityService() {

    /** restore_key_services was on when the config was read: this helper is noted for ServiceRepair. */
    private var repairRecord = false

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private var settingsScancodes = IntArray(0)
    private var settingsKeycodes = IntArray(0)

    /** consume_volume_keys: Volume Up/Down go to VolumeKeys instead of on to the firmware. */
    private var consumeVolumeKeys = false
    private var volumeKeyFilter = false

    /** app_keys, the same maps InputKeyService reads, for setups that run only this service. */
    private var appKeys: Map<Int, String> = emptyMap()
    private var appScancodes: Map<Int, String> = emptyMap()
    private var holdAppKeys: Map<Int, String> = emptyMap()
    private var holdAppScancodes: Map<Int, String> = emptyMap()

    /** Waits for the user to unlock when Android binds this service at boot; null once the config is read. */
    private var unlockReceiver: BroadcastReceiver? = null

    /** The stock launcher's HOME activities (stock_home_redirect), disabled ones included. */
    private val stockHome = HashSet<String>()

    /** stock_home_redirect, the stock launcher's package. */
    private var stockHomePackage = ""

    /** power_on_home: the Live TV package to send back to Home, empty when off; armed until this uptime or a key. */
    private var powerOnLiveTv = ""
    private var powerOnUntil = 0L

    /** home_long_press_home: this service takes every Home press, held ones included. */
    private var homeLongPressHome = false

    /** app_keys "front:" entries, and the package of the activity in front, from window changes while there are any. */
    private var frontAppKeys: Map<Int, String> = emptyMap()
    private var frontAppScancodes: Map<Int, String> = emptyMap()
    private var frontPackage = ""

    /** power_on_home_always: Home at every power-on, not only over Live TV. */
    private var powerOnAlways = false

    /** power_on_home: re-arms after the screen turns on; null unless the key is on. */
    private var screenOnReceiver: BroadcastReceiver? = null

    /** Stops key filtering while a game controller is connected; null unless gamepad_passthrough and key_filter, or before the unlock. */
    private var gamepads: GamepadPassthrough? = null

    /** Screen-off receiver while guard_off_in_standby is set; null otherwise. */
    private var standbyExit: BroadcastReceiver? = null

    /** disableSelf is how this service leaves for standby, not the user turning it off. */
    private var steppingOut = false

    /** Opt-in rescue for the setup recovery that claims HOME when the stock launcher is disabled
     *  (config.xml recovery_home_redirect). */
    private var recoveryRedirect = false

    /** Last elapsedRealtime a recovery redirect fired; with the once-per-boot rule this stops a loop. */
    private var lastRecoveryRedirectMs = 0L

    /** Last elapsedRealtime openHome() launched; duplicate triggers inside the window are dropped. */
    private var lastOpenHomeMs = 0L

    /** Lets the system see the key, so a long press still does what the firmware gives it. */
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = this
        val connectedMs = SystemClock.elapsedRealtime()
        BootLog.mark("keyservice connected")
        settingsScancodes = resources.getIntArray(R.array.settings_key_scancodes)
        // The service is directBootAware: Android 11 skips a service that is not while the user is still
        // locked at boot, writes accessibility_enabled=0 and, on TCL G03 firmware, never binds it after the
        // unlock. Config.load reads credential-protected storage, so wait for the unlock before reading it.
        val users = getSystemService(UserManager::class.java)
        if (users.isUserUnlocked) {
            loadConfig()
            BootLog.mark("keyservice config (unlocked)", connectedMs)
            return
        }
        BootLog.mark("keyservice waiting for unlock")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                stopWaitingForUnlock()
                val t = SystemClock.elapsedRealtime()
                BootLog.mark("keyservice unlocked")
                loadConfig()
                BootLog.mark("keyservice config", t)
            }
        }
        unlockReceiver = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
        // The unlock may have happened between the check and the registration.
        if (users.isUserUnlocked) {
            stopWaitingForUnlock()
            loadConfig()
        }
    }

    private fun stopWaitingForUnlock() {
        val receiver = unlockReceiver ?: return
        unregisterReceiver(receiver)
        unlockReceiver = null
    }

    override fun onDestroy() {
        if (running == this) running = null
        RemoteKeys.release(this)
        stopWaitingForUnlock()
        val screenReceiver = screenOnReceiver
        if (screenReceiver != null) unregisterReceiver(screenReceiver)
        val exit = standbyExit
        if (exit != null) {
            try {
                unregisterReceiver(exit)
            } catch (ignored: RuntimeException) {
            }
            standbyExit = null
        }
        gamepads?.stop()
        if (consumeVolumeKeys || volumeKeyFilter) VolumeKeys.stop()
        VolumeSlider.release(this)
        super.onDestroy()
    }

    /** Some TCL boards never wake from the remote while this helper stays bound with the screen off. */
    private fun armStandbyExit() {
        if (standbyExit != null || !GuardKeep.offInStandby(this)) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (Intent.ACTION_SCREEN_OFF != intent.action) return
                leaveForStandby()
            }
        }
        standbyExit = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        val power = getSystemService(PowerManager::class.java)
        if (power != null && !power.isInteractive) leaveForStandby()
    }

    private fun disarmStandbyExit() {
        val exit = standbyExit ?: return
        try {
            unregisterReceiver(exit)
        } catch (ignored: RuntimeException) {
        }
        standbyExit = null
    }

    private fun leaveForStandby() {
        steppingOut = true
        GuardKeep.stepOut(this)
    }

    private fun loadConfig() {
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        val config = Config.load(this)
        // restore_key_services: note that this helper runs, so Home can put it back after a force stop. Here, not
        // in onServiceConnected: the note is in credential-protected storage too, readable only after the unlock.
        repairRecord = config.restoreKeyServices
        if (repairRecord) ServiceRepair.connected(this, javaClass)
        settingsKeycodes = config.settingsKeycodes
        consumeVolumeKeys = config.consumeVolumeKeys
        volumeKeyFilter = config.keyFilter
        VolumeSlider.observe(this)
        VolumeAutoStart.request(this)
        Log.d("PersonalVolume", "HomeKeyService keyFilter=$volumeKeyFilter compatibility=$consumeVolumeKeys")
        appKeys = RemoteKeys.parseAppKeys(config.appKeys)
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys)
        RemoteKeys.applyKeyFilter(this, config.keyFilter)
        RemoteKeys.setDebug(config.remoteKeysDebug)
        homeLongPressHome = config.homeLongPressHome
        frontAppKeys = RemoteKeys.parseFrontAppKeys(config.appKeys)
        frontAppScancodes = RemoteKeys.parseFrontAppScancodes(config.appKeys)
        val watchFront = frontAppKeys.isNotEmpty() || frontAppScancodes.isNotEmpty()
        stockHome.clear()
        stockHomePackage = config.stockHomeRedirect
        if (config.stockHomeRedirect.isNotEmpty()) {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(config.stockHomeRedirect)
            for (ri in packageManager.queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)) {
                stockHome.add(ri.activityInfo.name)
            }
        }
        // power_on_home, off by default. Not with boot_to_live_tv, or with Live TV picked as the Open on start app:
        // those open Live TV on purpose at boot.
        val openOnStart = (if (config.openOnStartChoice)
            getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getString(OpenOnStart.KEY, "") else "") ?: ""
        powerOnLiveTv = if (config.powerOnHome && !config.bootToLiveTv && config.liveTvPackage != openOnStart)
            config.liveTvPackage else ""
        powerOnAlways = config.powerOnHomeAlways && !config.bootToLiveTv && openOnStart.isEmpty()
        recoveryRedirect = config.recoveryHomeRedirect
        if (recoveryRedirect) BootLog.mark("recovery redirect opt-in on")
        if (powerOnLiveTv.isNotEmpty() || powerOnAlways) {
            armPowerOnHome()
        } else {
            val screenReceiver = screenOnReceiver
            if (screenReceiver != null) {
                unregisterReceiver(screenReceiver)
                screenOnReceiver = null
                powerOnUntil = 0
            }
        }
        // home_key_service.xml subscribes to no events, so the redirects ask for window changes only when one is on.
        val info = serviceInfo
        if (info != null && (stockHome.isNotEmpty() || powerOnLiveTv.isNotEmpty() || watchFront || recoveryRedirect)) {
            info.eventTypes = info.eventTypes or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            setServiceInfo(info)
        }
        if (stockHome.isNotEmpty() || powerOnLiveTv.isNotEmpty()) probeAtBoot()
        armStandbyExit()
        holdAppKeys = RemoteKeys.parseHoldAppKeys(config.appKeys)
        holdAppScancodes = RemoteKeys.parseHoldAppScancodes(config.appKeys)
        // gamepad_passthrough, off by default. Only with key_filter on: off, the service never filters keys.
        if (config.gamepadPassthrough && config.keyFilter && gamepads == null) {
            val pads = GamepadPassthrough(this)
            gamepads = pads
            pads.start()
        }
    }

    /** A tap's target: the "front:" entry while the button's own app is already in front, else the usual one. */
    private fun inFront(keys: Map<Int, String>, front: Map<Int, String>, code: Int): String? {
        val target = keys[code]
        val alt = front[code]
        return if (alt != null && RemoteKeys.inFront(target, frontPackage)) alt else target
    }

    private fun isSettingsKey(event: KeyEvent): Boolean {
        if (SettingsKeyPolicy.isNumberEntryKey(event.keyCode)) return false
        if (event.keyCode == KeyEvent.KEYCODE_SETTINGS) return true
        for (code in settingsScancodes) if (event.scanCode == code) return true
        for (code in settingsKeycodes) if (event.keyCode == code) return true
        return false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // power_on_home: the first key after power-on hands the TV to the user (power and wake keys don't count).
        if (powerOnUntil != 0L && event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode != KeyEvent.KEYCODE_POWER && event.keyCode != KeyEvent.KEYCODE_WAKEUP
        ) {
            powerOnUntil = 0
        }
        if (consumeVolumeKeys && VolumeKeys.handle(this, event)) return true
        if (volumeKeyFilter && VolumeKeys.control(this, event)) return true
        // A scancode entry is more specific than any keycode, so it goes first.
        if (RemoteKeys.handle(
                this, event, inFront(appScancodes, frontAppScancodes, event.scanCode),
                holdAppScancodes[event.scanCode]
            )
        ) return true
        // The remote's Settings button always opens the launcher's own settings menu.
        if (isSettingsKey(event)) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                if (!RemoteKeys.launchHomeSettings(this))
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
            }
            return true
        }
        if (RemoteKeys.handle(
                this, event, inFront(appKeys, frontAppKeys, event.keyCode),
                holdAppKeys[event.keyCode]
            )
        ) return true
        // home_long_press_home: Android never sees the Home key, so a held press cannot turn into ALL_APPS.
        if (homeLongPressHome && event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openHome()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_HOME && stockHome.isNotEmpty()) {
            redirectHomeKey(event)
            return false
        }
        if (event.keyCode != KeyEvent.KEYCODE_ALL_APPS) {
            RemoteKeys.debugUnmatched(event)
            return false
        }
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openHome()
        return true
    }

    /** Unbound by Android: the user turned the helper off, so Home doesn't put it back (ServiceRepair). */
    override fun onUnbind(intent: Intent): Boolean {
        RemoteKeys.release(this)
        if (repairRecord && !steppingOut) ServiceRepair.disconnected(this, javaClass)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // app_keys "front:" entries: only activities count, so a toast or the volume bar doesn't change the app in front.
        if ((frontAppKeys.isNotEmpty() || frontAppScancodes.isNotEmpty()) &&
            event.packageName != null && event.className != null
        ) {
            try {
                val window = ComponentName(event.packageName.toString(), event.className.toString())
                packageManager.getActivityInfo(window, 0)
                frontPackage = window.packageName
            } catch (notAnActivity: PackageManager.NameNotFoundException) {
                // a dialog, panel or overlay: the app under it is still in front
            }
        }
        // power_on_home: the firmware opened Live TV by itself at power-on, before anyone pressed a key.
        if (powerOnRedirect(powerOnLiveTv, event.packageName, SystemClock.elapsedRealtime(), powerOnUntil)) {
            openHome()
            return
        }
        // stock_home_redirect: the firmware turned the stock launcher back on and its home screen came up.
        val cls = event.className
        if (stockHome.isNotEmpty() && cls != null && stockHome.contains(cls.toString())) openHome()
        // Opt-in Haier/Google TV rescue (recovery_home_redirect): the setup recovery claims HOME
        // (priority 1) when the stock launcher is disabled and loops on a failing relaunch. Only the
        // exact RecoveryActivity is ever redirected, only while the configured stock home is
        // disabled/unavailable, and only once per boot, so this cannot fight a working setup.
        if (recoveryRedirect && isRecoveryWindow(event.packageName, event.className)) {
            maybeRedirectRecovery()
        }
    }

    private fun maybeRedirectRecovery() {
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val prefs = getSharedPreferences("boot", MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        if (!shouldRedirectRecovery(
                true, RECOVERY_PACKAGE, RECOVERY_ACTIVITY, stockHomeUnavailable(),
                boot, prefs.getInt("recovery_boot", -1), now, lastRecoveryRedirectMs
            )
        ) {
            return
        }
        prefs.edit().putInt("recovery_boot", boot).apply()
        lastRecoveryRedirectMs = now
        BootLog.mark("recovery redirect -> openHome")
        openHome()
    }

    /** True when the stock Home cannot serve (disabled for this user). The configured
     *  stock_home_redirect package, or Google TV's stock Home when none is configured.
     *  An unknown package never counts as unavailable: no redirect on an assumption. */
    private fun stockHomeUnavailable(): Boolean {
        val pkg = if (stockHomePackage.isEmpty()) DEFAULT_STOCK_HOME else stockHomePackage
        try {
            packageManager.getApplicationInfo(pkg, 0)
        } catch (unknown: PackageManager.NameNotFoundException) {
            return false
        }
        try {
            val state = packageManager.getApplicationEnabledSetting(pkg)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
            ) {
                return true
            }
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
            return packageManager.queryIntentActivities(home, 0).isEmpty()
        } catch (e: Exception) {
            return false
        }
    }

    /** power_on_home: armed for the boot window (if this bind is part of the boot) and after every screen-on. */
    private fun armPowerOnHome() {
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) {
            powerOnUntil = BOOT_WINDOW_MS
            if (powerOnAlways) handler.postDelayed({ homeAtPowerOn() }, POWER_ON_ALWAYS_DELAY_MS)
        }
        if (screenOnReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                powerOnUntil = SystemClock.elapsedRealtime() + SCREEN_ON_WINDOW_MS
                if (powerOnAlways) handler.postDelayed({ homeAtPowerOn() }, POWER_ON_ALWAYS_DELAY_MS)
            }
        }
        screenOnReceiver = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    /** power_on_home_always: Home, unless a key was pressed since the screen came on (the user has the TV). */
    private fun homeAtPowerOn() {
        if (stillArmed(SystemClock.elapsedRealtime(), powerOnUntil)) openHome()
    }

    /** stock_home_redirect at boot: the stock home is often on screen before Android binds this service, so no
     *  window event for it arrives (#1116). Once per boot, open ProbeActivity: when it closes, whatever was in
     *  front comes back and sends a window event, and onAccessibilityEvent covers it only if it is the stock home.
     *  An HDMI input or an app that the TV resumed at boot is left alone. */
    private fun probeAtBoot() {
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val prefs = getSharedPreferences("boot", MODE_PRIVATE)
        if (!firstBindAfterBoot(boot, prefs.getInt("probe_boot", -1), SystemClock.elapsedRealtime())) return
        prefs.edit().putInt("probe_boot", boot).apply()
        BootLog.mark("probe sent")
        try {
            startActivity(
                Intent(this, ProbeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        } catch (ignored: ActivityNotFoundException) {
        } catch (ignored: SecurityException) {
        }
    }

    /** stock_home_redirect on the Home key: when the stock home is already on screen (it came up at boot, before
     *  Android bound this service), Home brings it up again with no window change, so the redirect above never
     *  fires and the TV stays on the stock home until another app opens (#1116). A short press opens this
     *  launcher shortly after the system has handled the key; a long press (Google TV's quick settings) is left
     *  alone. Held time, not repeats: key repeats are made after accessibility filtering, so none arrive here. */
    private fun redirectHomeKey(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled &&
            isShortPress(event.downTime, event.eventTime, ViewConfiguration.getLongPressTimeout().toLong())
        ) {
            handler.postDelayed({ openHome() }, HOME_KEY_DELAY_MS)
        }
    }

    private fun openHome() {
        val now = SystemClock.elapsedRealtime()
        if (!shouldOpenHome(now, lastOpenHomeMs)) {
            BootLog.mark("openHome deduped")
            return
        }
        lastOpenHomeMs = now
        BootLog.mark("openHome")
        val open = if (stockHomeStays()) Intent(this, MainActivity::class.java)
            else Intent(Intent.ACTION_MAIN).setPackage(packageName)
        startActivity(
            open.addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
    }

    /** stock_home_redirect while Android's Home is still the stock launcher (launcherx on Google TV outranks this
     *  one). A Home intent would then put this launcher in the home task and make it the home process, and from
     *  Android 10 the stock launcher, no longer exempt from the limits on background activity starts, can't open
     *  apps for the remote's YouTube and Netflix buttons (#1274). openHome starts it like an app instead:
     *  CATEGORY_HOME without ACTION_MAIN is not a Home intent, but still sends MainActivity back to the first tile.
     *  A MainActivity already in the home task, as the install scripts' first start leaves it, is reused as it is
     *  until that task goes (a restart or a reinstall). With this launcher or Android's chooser as Home, openHome
     *  keeps the Home intent. */
    private fun stockHomeStays(): Boolean {
        if (stockHome.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val home = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        )
        return home != null && stockHomePackage == home.activityInfo.packageName
    }

    override fun onInterrupt() {
    }

    companion object {
        /** The bound helper, so the opt-in broadcast can arm it without waiting for a new connection. */
        private var running: HomeKeyService? = null

        /** Exact setup-recovery window that claims HOME (priority 1) on Google TV when the stock
         *  launcher is disabled. Matched exactly: no other setupwraith window is ever redirected. */
        const val RECOVERY_PACKAGE = "com.google.android.tungsten.setupwraith"
        const val RECOVERY_ACTIVITY = "com.google.android.tungsten.setupwraith.RecoveryActivity"

        /** Minimum gap between two recovery redirects; the once-per-boot rule is the real loop guard. */
        const val RECOVERY_REDIRECT_COOLDOWN_MS = 10_000L

        /** Duplicate openHome() triggers inside this window are dropped. */
        const val OPEN_HOME_DEDUP_MS = 2_000L

        /** Google TV's stock Home; the fallback's disabled-check target when no stock_home_redirect is set. */
        const val DEFAULT_STOCK_HOME = "com.google.android.apps.tv.launcherx"

        /** How long power_on_home stays armed after the screen turns on without a reboot. */
        const val SCREEN_ON_WINDOW_MS = 60 * 1000L

        /** power_on_home_always: after the firmware has brought its last source back (Philips: within 0.5 s). */
        const val POWER_ON_ALWAYS_DELAY_MS = 800L

        /** How long after boot the first bind still counts as "at boot" for probeAtBoot. */
        const val BOOT_WINDOW_MS = 3 * 60 * 1000L

        /** Lets the system see the key, so a long press still does what the firmware gives it. */
        private const val HOME_KEY_DELAY_MS = 250L

        /** The opt-in broadcast: arm this binding now, or drop the receiver if the opt-in was turned off. */
        @JvmStatic
        fun requestSleep(): Boolean {
            val service = running
            return service != null && Build.VERSION.SDK_INT >= 28 &&
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }

        @JvmStatic
        fun isConnected(): Boolean {
            return running != null
        }

        @JvmStatic
        fun applyStandbyOptIn() {
            val service = running ?: return
            if (GuardKeep.offInStandby(service)) service.armStandbyExit()
            else service.disarmStandbyExit()
        }

        @JvmStatic
        fun isRecoveryWindow(pkg: CharSequence?, cls: CharSequence?): Boolean =
            HomeKeyPolicy.isRecoveryWindow(pkg, cls)

        @JvmStatic
        fun shouldRedirectRecovery(
            optIn: Boolean, pkg: CharSequence?, cls: CharSequence?,
            stockHomeUnavailable: Boolean, boot: Int, lastRedirectBoot: Int, now: Long, lastRedirectMs: Long
        ): Boolean =
            HomeKeyPolicy.shouldRedirectRecovery(optIn, pkg, cls, stockHomeUnavailable, boot, lastRedirectBoot, now, lastRedirectMs)

        @JvmStatic
        fun shouldOpenHome(now: Long, lastOpenMs: Long): Boolean =
            HomeKeyPolicy.shouldOpenHome(now, lastOpenMs)

        @JvmStatic
        fun stillArmed(now: Long, armedUntil: Long): Boolean =
            HomeKeyPolicy.stillArmed(now, armedUntil)
        /** The settings menu offers Restart only while this helper is bound: it opens the system power menu. */
        @JvmStatic
        fun canOpenPowerMenu(): Boolean {
            return running != null
        }

        /** The system power menu (Restart, and Power off where the firmware has it); the TV asks before it acts. */
        @JvmStatic
        fun openPowerMenu() {
            val service = running
            if (service != null) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
        }

        @JvmStatic
        fun powerOnRedirect(liveTv: String, pkg: CharSequence?, now: Long, armedUntil: Long): Boolean =
            HomeKeyPolicy.powerOnRedirect(liveTv, pkg, now, armedUntil)

        @JvmStatic
        fun firstBindAfterBoot(boot: Int, lastBoot: Int, uptimeMs: Long): Boolean =
            HomeKeyPolicy.firstBindAfterBoot(boot, lastBoot, uptimeMs)

        @JvmStatic
        fun isShortPress(downTime: Long, upTime: Long, longPressTimeout: Long): Boolean =
            HomeKeyPolicy.isShortPress(downTime, upTime, longPressTimeout)

    }
}
