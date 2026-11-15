package com.sadique.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.tv.TvContract
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.function.Consumer
import java.util.function.ToIntFunction
import java.util.regex.Pattern

/**
 * Remote buttons that only the stock launcher answered, for InputKeyService and HomeKeyService: app_keys
 * (config.xml or a theme) maps a keycode to the app it opens, as "190:com.google.android.youtube.tv" or
 * "KEYCODE_BUTTON_3:com.google.android.youtube.tv", or a scancode as "scan:490:com.wbd.hbomax". A "hold:"
 * prefix makes an entry the long-press action instead: "hold:KEYCODE_BUTTON_8:org.xbmc.kodi" opens Kodi when
 * that button is held for HOLD_MS, while the unprefixed entry still opens on a tap; "hold:scan:490:..." does
 * the same for a scancode. One set of maps for both services; Android hands a key to every service that filters,
 * so both may call handle() for one event, and it answers each press once.
 * A target of "input:<input id>" switches to that TV input (an HDMI port) instead of opening an app, a target
 * of "inputs" opens the input switcher, for a remote whose source button sends a key Android does not map,
 * one of the action words (back, home, recents, mute, sleep, screensaver, settings, wifi, home_settings) does that action, and
 * "package/.Activity" opens that activity rather than the app's launcher entry (an add-on's quick bar, say).
 * "home_settings" opens the translucent QuickSettingsActivity over the current app, without raising Home.
 */
internal class RemoteKeys private constructor() {

    /**
     * Each button's presses, kept per device, keycode and scancode: KeyEvent.getDownTime() can't tell presses apart,
     * since a key pressed together with another can report the other's. Both services get every event, so a DOWN
     * or UP whose event time was already seen for that button is the other service's copy and is skipped. One small
     * record per mapped button that has been pressed, not a history.
     */
    class Presses(private val timer: Timer) {
        interface Timer {
            fun post(hold: Runnable, delayMs: Long)
            fun cancel(hold: Runnable)
        }

        private class Press {
            var downAt = -1L
            var upAt = -1L
            var fired = false
            var hold: Runnable? = null

            /** The helpers that saw this DOWN while its hold is pending, each with the way it opens a target. */
            val owners = LinkedHashMap<Any, Consumer<String>>()
        }

        private val buttons = HashMap<String, Press>()

        fun down(
            button: String, eventTime: Long, repeatCount: Int, holdApp: String?, owner: Any,
            open: Consumer<String>
        ) {
            if (repeatCount != 0) return
            val press = buttons.getOrPut(button) { Press() }
            if (press.downAt == eventTime) {
                // The other helper's copy: it can answer the hold too, should the first one leave.
                if (press.hold != null) press.owners.putIfAbsent(owner, open)
                return
            }
            if (press.hold != null) timer.cancel(press.hold!!) // its UP never came
            press.downAt = eventTime
            press.fired = false
            press.hold = null
            press.owners.clear()
            if (holdApp == null) return
            press.owners[owner] = open
            val hold = Runnable {
                val first = press.owners.values.iterator().next()
                press.hold = null
                press.owners.clear()
                press.fired = true
                first.accept(holdApp)
            }
            press.hold = hold
            timer.post(hold, RemoteKeys.HOLD_MS)
        }

        /** Drops this helper from every pending hold, cancelling a hold it was the last to own. */
        fun release(owner: Any) {
            for (press in buttons.values) {
                if (press.hold == null || press.owners.remove(owner) == null || press.owners.isNotEmpty()) continue
                timer.cancel(press.hold!!)
                press.hold = null
                press.fired = true // the press is over: an UP that still comes later opens nothing
            }
        }

        fun up(
            button: String, eventTime: Long, downTime: Long, canceled: Boolean, app: String?, holdApp: String?,
            open: Consumer<String>
        ) {
            val press = buttons.getOrPut(button) { Press() }
            if (press.upAt == eventTime) return
            val sawDown = press.downAt > press.upAt
            press.upAt = eventTime
            if (press.hold != null) {
                timer.cancel(press.hold!!)
                press.hold = null
                press.owners.clear()
            }
            if (canceled || (sawDown && press.fired)) return
            // Without its DOWN (the service started while the button was held), the event's own down time will do.
            val target = RemoteKeys.target(app, holdApp, eventTime - if (sawDown) press.downAt else downTime)
            if (target != null) open.accept(target)
        }
    }

    companion object {
        /** How long a button is held before its "hold:" entry wins (Android's long-press timeout). */
        const val HOLD_MS = 500L

        /** The app_keys target that opens the input switcher, as "scan:365:inputs". */
        const val INPUTS = "inputs"

        /**
         * The flags on the KEYCODE_MENU pair that Xiaomi's China firmware (MiTV-MFTR0, Android 11) injects for a short
         * press of the remote's Menu key, after keeping the real press for itself (#1637). They include FLAG_CANCELED,
         * so the pair reads as a cancelled press, and the undocumented 0x1000, 0x2000 and 0x4000, which no real
         * cancelled press carries.
         */
        const val XIAOMI_SHORT_MENU_FLAGS = 0x7531

        /** Pre-compiled regex patterns to avoid re-compiling Patterns on every entry during key parsing. */
        private val NUMERIC_KEY = Pattern.compile("[0-9]{1,6}")
        private val KEYCODE_KEY = Pattern.compile("KEYCODE_[A-Z0-9_]+")

        /** remote_keys_debug (config.xml or a theme): verbose key logging stays off unless this is on. */
        @Volatile
        private var DEBUG = false

        /** Created on first use: building a Handler while the class loads breaks the JVM tests. */
        private var presses: Presses? = null

        /** Called by the key services from their config; off by default. */
        @JvmStatic
        fun setDebug(debug: Boolean) {
            DEBUG = debug
        }

        private fun debug(msg: String) {
            if (DEBUG) Log.d("RemoteKeys", msg)
        }

        /**
         * Debug aid for a key event nothing consumed: with debugging on, "mapping not matched" gets its own
         * log line, so it reads apart from "event never delivered" (no line at all) and "activity launch
         * failed" (logged where the launch runs). Only DOWN, to halve the noise.
         */
        @JvmStatic
        fun debugUnmatched(event: KeyEvent) {
            if (!DEBUG || event.action != KeyEvent.ACTION_DOWN) return
            debug(
                "unmatched key DOWN device=" + event.deviceId +
                    " keyCode=" + event.keyCode + " scanCode=" + event.scanCode
            )
        }

        /** keycode -> package, with key names resolved by Android. */
        @JvmStatic
        fun parseAppKeys(entries: Array<String>): Map<Int, String> {
            return parseAppKeys(entries.asList(), KeyEvent::keyCodeFromString)
        }

        @JvmStatic
        fun parseAppKeys(entries: Iterable<String>): Map<Int, String> {
            return parseAppKeys(entries, KeyEvent::keyCodeFromString)
        }

        /**
         * keycode -> package. The key is a number or a KEYCODE_ name (resolved by {@code names}; 0, KEYCODE_UNKNOWN,
         * means it isn't one). Entries without a colon, with an unknown key or with no package are skipped; for a
         * keycode listed twice the last entry wins.
         */
        @JvmStatic
        fun parseAppKeys(entries: Iterable<String>, names: ToIntFunction<String>): Map<Int, String> {
            val keys = HashMap<Int, String>()
            for (entry in entries) {
                if (entry == null) continue
                val parts = entry.split(":", limit = 2)
                if (parts.size != 2 || parts[1].trim().isEmpty()) continue
                val key = parts[0].trim()
                val code: Int
                if (NUMERIC_KEY.matcher(key).matches()) {
                    code = Integer.parseInt(key)
                } else if (KEYCODE_KEY.matcher(key).matches()) {
                    code = names.applyAsInt(key) // keyCodeFromString takes digits only from API 29, names always
                } else {
                    continue
                }
                if (code > 0) keys[code] = parts[1].trim()
            }
            return keys
        }

        /** keycode -> package from the "hold:" entries; the rest of each entry reads as it does in parseAppKeys. */
        @JvmStatic
        fun parseHoldAppKeys(entries: Array<String>): Map<Int, String> {
            return parseHoldAppKeys(entries.asList(), KeyEvent::keyCodeFromString)
        }

        @JvmStatic
        fun parseHoldAppKeys(entries: Iterable<String>): Map<Int, String> {
            return parseHoldAppKeys(entries, KeyEvent::keyCodeFromString)
        }

        @JvmStatic
        fun parseHoldAppKeys(entries: Iterable<String>, names: ToIntFunction<String>): Map<Int, String> {
            return parseAppKeys(prefixed(entries, "hold:"), names)
        }

        /** keycode -> target from the "front:" entries, used while the button's own app is already in front. */
        @JvmStatic
        fun parseFrontAppKeys(entries: Iterable<String>): Map<Int, String> {
            return parseFrontAppKeys(entries, KeyEvent::keyCodeFromString)
        }

        @JvmStatic
        fun parseFrontAppKeys(entries: Iterable<String>, names: ToIntFunction<String>): Map<Int, String> {
            return parseAppKeys(prefixed(entries, "front:"), names)
        }

        @JvmStatic
        fun parseFrontAppScancodes(entries: Iterable<String>): Map<Int, String> {
            return parseAppScancodes(prefixed(entries, "front:"))
        }

        /**
         * Applies the key_filter choice (config.xml or a theme): when false the service drops
         * FLAG_REQUEST_FILTER_KEY_EVENTS at runtime, so it stays enabled for nothing but stops filtering.
         * Some firmware (TCL Google TV on Android 12) drops key auto-repeat once ANY service filters keys:
         * long-pressing volume then steps once per press. Filtering can only be skipped while the service
         * runs, since the flag is also declared in the manifest metadata.
         */
        @JvmStatic
        fun applyKeyFilter(service: AccessibilityService, keyFilter: Boolean) {
            if (keyFilter) return
            val info = service.serviceInfo ?: return
            info.flags = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
            service.setServiceInfo(info)
        }

        /**
         * scancode -> package, from the "scan:490:com.wbd.hbomax" entries of the same list. For remotes that send
         * several app buttons as one keycode and tell them apart by scancode (Sony BRAVIA: YouTube, Prime Video and
         * Disney+ are all keycode 196, scancodes 485, 490 and 491). Find it with `getevent -lt` while pressing the
         * button: the EV_KEY code, in hex. Other entries are skipped; for a scancode listed twice the last entry wins.
         */
        @JvmStatic
        fun parseAppScancodes(entries: Iterable<String>): Map<Int, String> {
            val keys = HashMap<Int, String>()
            for (entry in entries) {
                if (entry == null) continue
                val parts = entry.split(":", limit = 3)
                if (parts.size != 3 || parts[0].trim() != "scan" || parts[2].trim().isEmpty()) continue
                val code = parts[1].trim()
                if (!NUMERIC_KEY.matcher(code).matches()) continue
                val scan = Integer.parseInt(code)
                if (scan > 0) keys[scan] = parts[2].trim()
            }
            return keys
        }

        /** scancode -> package from the "hold:scan:..." entries; the rest reads as it does in parseAppScancodes. */
        @JvmStatic
        fun parseHoldAppScancodes(entries: Iterable<String>): Map<Int, String> {
            return parseAppScancodes(prefixed(entries, "hold:"))
        }

        /**
         * Rewrites the two known-wrong scan:240 settings targets to the "home_settings" action, in place:
         * they opened a full-screen settings page (LauncherSettingsActivity) or the quick settings panel
         * (QuickSettingsActivity) instead of the Home settings popup. Only those exact targets are rewritten,
         * with or without a "hold:" prefix; every other entry — including a deliberate scan:240 mapping to
         * something else — is left untouched.
         */
        @JvmStatic
        fun migrateScanSettingsKeys(entries: List<String>): List<String> {
            val out = ArrayList<String>(entries.size)
            for (entry in entries) out.add(migrateScanSettingsKey(entry) ?: entry)
            return out
        }

        private fun migrateScanSettingsKey(entry: String?): String? {
            if (entry == null) return null
            val text = entry.trim()
            var prefix = ""
            var rest = text
            if (rest.startsWith("hold:")) {
                prefix = "hold:"
                rest = rest.substring(5).trim()
            }
            if (rest == "scan:240:com.sadique.launcher/.LauncherSettingsActivity" ||
                rest == "scan:240:com.sadique.launcher/.QuickSettingsActivity"
            ) {
                return prefix + "scan:240:home_settings"
            }
            return entry
        }

        /** The entries with this prefix ("hold:", "front:"), the prefix removed, so the normal parsers can read them. */
        private fun prefixed(entries: Iterable<String>, prefix: String): Iterable<String> {
            val found = ArrayList<String>()
            for (entry in entries) {
                if (entry == null) continue
                val text = entry.trim()
                if (text.startsWith(prefix)) found.add(text.substring(prefix.length).trim())
            }
            return found
        }

        /** The package of a "guide:<package>" target, or null for any other target. */
        @JvmStatic
        fun guidePackage(target: String?): String? {
            if (target == null || !target.startsWith("guide:")) return null
            val pkg = target.substring(6).trim()
            return if (pkg.isEmpty()) null else pkg
        }

        /**
         * True when a tap's target is the app already in front ("package" or "package/.Activity"), so its "front:"
         * entry applies instead. Inputs and action words never match.
         */
        @JvmStatic
        fun inFront(target: String?, front: String?): Boolean {
            if (target == null || front == null || front.isEmpty()) return false
            val slash = target.indexOf('/')
            return (if (slash < 0) target else target.substring(0, slash)) == front
        }

        /**
         * Consumes a key mapped by either list: the hold app opens once the button has been down for HOLD_MS, the
         * normal one on an earlier release. False means neither list has this key, so the caller can pass it on.
         */
        @JvmStatic
        fun handle(ctx: Context, event: KeyEvent, app: String?, holdApp: String?): Boolean {
            // Both accessibility helpers share this path. Guard scan, keycode, front and hold mappings
            // before consuming DOWN or scheduling a hold, including migrated scan:240 settings targets.
            val safeApp = SettingsKeyPolicy.safeTarget(event.keyCode, app)
            val safeHoldApp = SettingsKeyPolicy.safeTarget(event.keyCode, holdApp)
            if (safeApp == null && safeHoldApp == null) return false
            var current = presses
            if (current == null) {
                val handler = Handler(Looper.getMainLooper())
                current = Presses(object : Presses.Timer {
                    override fun post(hold: Runnable, delayMs: Long) {
                        handler.postDelayed(hold, delayMs)
                    }

                    override fun cancel(hold: Runnable) {
                        handler.removeCallbacks(hold)
                    }
                })
                presses = current
            }
            debug(
                "key " + (if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP") +
                    " device=" + event.deviceId +
                    " keyCode=" + event.keyCode + " scanCode=" + event.scanCode +
                    " -> " + if (safeHoldApp != null) "$safeApp (hold: $safeHoldApp)" else safeApp
            )
            val button = "${event.deviceId}:${event.keyCode}:${event.scanCode}"
            if (event.action == KeyEvent.ACTION_DOWN) {
                current.down(button, event.eventTime, event.repeatCount, safeHoldApp, ctx) { target -> launch(ctx, target) }
            } else if (event.action == KeyEvent.ACTION_UP) {
                current.up(button, event.eventTime, event.downTime, event.isCanceled, safeApp, safeHoldApp) { target -> launch(ctx, target) }
            }
            return true
        }

        /**
         * For a helper that stops receiving keys (unbound, destroyed, or no longer filtering): no UP will reach it to
         * cancel a hold it saw go down, so the hold goes to the other helper if that one saw the DOWN too, and is
         * cancelled otherwise. Safe to call more than once.
         */
        @JvmStatic
        fun release(helper: Context) {
            presses?.release(helper)
        }

        /** True for the UP that ends a Menu press: not cancelled, or cancelled only as Xiaomi's injected short press is. */
        @JvmStatic
        fun menuReleased(action: Int, flags: Int): Boolean {
            if (action != KeyEvent.ACTION_UP) return false
            return (flags and KeyEvent.FLAG_CANCELED) == 0 ||
                (flags and XIAOMI_SHORT_MENU_FLAGS) == XIAOMI_SHORT_MENU_FLAGS
        }

        /** The app a mapped key opens: its hold entry after HOLD_MS, otherwise its normal one. */
        @JvmStatic
        fun target(app: String?, holdApp: String?, heldMs: Long): String? {
            return if (heldMs >= HOLD_MS && holdApp != null) holdApp else app
        }

        /** The TV input id of an "input:<id>" target, e.g. "input:com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413743104"; null for a package. */
        @JvmStatic
        fun inputId(target: String?): String? {
            if (target == null || !target.startsWith("input:")) return null
            val id = target.substring("input:".length).trim()
            return if (id.isEmpty()) null else id
        }

        /**
         * The accessibility global action for an action word, or 0 when the target is something else. "sleep" is
         * GLOBAL_ACTION_LOCK_SCREEN, which puts a TV in standby (as the Sleep Timer add-on does); Android has it from 28.
         */
        @JvmStatic
        fun globalAction(target: String, sdk: Int): Int {
            return when (target) {
                "back" -> AccessibilityService.GLOBAL_ACTION_BACK
                "home" -> AccessibilityService.GLOBAL_ACTION_HOME
                "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
                "sleep" -> if (sdk >= 28) AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN else 0
                else -> 0
            }
        }

        /**
         * Whether a target is an action word rather than an app or an input. A package name always has a dot.
         * "ignore" does nothing on purpose: the button is swallowed, for a remote shortcut that is pressed by accident.
         * "home_settings" opens the translucent quick panel over the foreground app.
         */
        @JvmStatic
        fun isAction(target: String): Boolean {
            return when (target) {
                "back", "home", "recents", "mute", "sleep",
                "screensaver", "settings", "wifi", "ignore",
                "home_settings" -> true
                else -> false
            }
        }

        /** Runs an action word; false for an app or an input. Needs no permission the launcher does not already have. */
        private fun runAction(ctx: Context, target: String): Boolean {
            if (!isAction(target)) return false
            val global = globalAction(target, Build.VERSION.SDK_INT)
            if (global != 0) {
                if (ctx is AccessibilityService) ctx.performGlobalAction(global)
                return true
            }
            when (target) {
                "mute" -> {
                    // The media stream a TV plays everything on, with its own volume bar, as a remote's mute button would.
                    val audio = ctx.getSystemService(AudioManager::class.java)
                    if (audio != null) audio.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI
                    )
                }
                "screensaver" -> {
                    // The screensaver chosen in the TV's settings, through the system's "start now" shortcut; the
                    // launcher's own clock where the firmware has no such shortcut.
                    var dream = Intent(Intent.ACTION_MAIN)
                        .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                    if (ctx.packageManager.resolveActivity(dream, 0) == null) dream = Intent(ctx, ClockActivity::class.java)
                    start(ctx, dream)
                }
                "settings" -> start(ctx, SystemSettings.intent(ctx, Settings.ACTION_SETTINGS))
                "wifi" -> start(ctx, SystemSettings.intent(ctx, Settings.ACTION_WIFI_SETTINGS))
                "home_settings" -> launchHomeSettings(ctx)
                else -> {
                    // "ignore", or "sleep" below Android 9: consumed, nothing to do
                }
            }
            return true
        }

        /** Opens the separate translucent panel task; Home is not part of this route. */
        @JvmStatic
        fun launchHomeSettings(ctx: Context): Boolean {
            val intent = homeSettingsIntent(ctx.packageName)
            try {
                ctx.startActivity(intent)
                debug("home_settings launched: " + intent.component)
                return true
            } catch (e: ActivityNotFoundException) {
                debug("home_settings launch FAILED: $e")
                return false
            } catch (e: SecurityException) {
                debug("home_settings launch FAILED: $e")
                return false
            }
        }

        /** The intent launchHomeSettings sends; package-visible so tests can check it without a Context. */
        @JvmStatic
        fun homeSettingsIntent(packageName: String): Intent {
            return Intent()
                .setComponent(ComponentName(packageName, "$packageName.QuickSettingsActivity"))
                .setAction(MainActivity.ACTION_SHOW_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        private fun start(ctx: Context, intent: Intent) {
            try {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ignored: ActivityNotFoundException) {
            } catch (ignored: SecurityException) {
            }
        }

        /**
         * True for a "package/.Activity" target. An input id has slashes too, and ComponentName.unflattenFromString()
         * would read "input:com.tcl.tvinput" as its package, so an "input:" target never counts.
         */
        @JvmStatic
        fun isActivity(target: String?): Boolean {
            return target != null && inputId(target) == null && target.indexOf('/') > 0
        }

        /** Opens an app from a remote button: its TV entry if it has one, else its phone entry. An "input:<id>" target
         *  switches to that TV input (an HDMI port) instead, an action word runs that action, and a
         *  "package/.Activity" target opens that activity. */
        @JvmStatic
        fun launch(ctx: Context, pkg: String?) {
            if (pkg == null || runAction(ctx, pkg)) return
            if (INPUTS == pkg) {
                // This launcher's own Inputs menu, as InputsRequestActivity opens it: addressed to Home directly, so a
                // vendor input picker that also answers VIEW_INPUTS never turns it into a chooser.
                try {
                    ctx.startActivity(
                        Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_VIEW_INPUTS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (ignored: ActivityNotFoundException) {
                }
                return
            }
            val guide = guidePackage(pkg)
            if (guide != null) {
                // The program guide, asked for the way Android TV's Live TV apps take it: the programs directory.
                start(
                    ctx, Intent(Intent.ACTION_VIEW)
                        .setDataAndType(TvContract.Programs.CONTENT_URI, "vnd.android.cursor.dir/program")
                        .setPackage(guide)
                )
                return
            }
            val input = inputId(pkg)
            if (input != null) {
                // The same passthrough URI the Inputs menu opens, which the Live TV app handles.
                val view = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(
                        TvContract.buildChannelUriForPassthroughInput(input),
                        "vnd.android.cursor.item/channel"
                    )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(view)
                } catch (ignored: ActivityNotFoundException) {
                }
                return
            }
            val activity = if (isActivity(pkg)) ComponentName.unflattenFromString(pkg) else null
            if (activity != null) {
                try {
                    ctx.startActivity(Intent().setComponent(activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (ignored: ActivityNotFoundException) {
                } catch (ignored: SecurityException) {
                }
                return
            }
            var open = ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg)
            if (open == null) open = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (open == null) return
            try {
                ctx.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ignored: ActivityNotFoundException) {
            }
        }
    }
}
