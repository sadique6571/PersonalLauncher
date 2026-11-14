package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.Calendar
import java.util.WeakHashMap
import java.util.function.Consumer
import java.util.function.IntConsumer
import java.util.function.IntPredicate
import java.util.function.IntSupplier

/** Shared remote-friendly glass cards for settings and every launcher option page. */
internal object GlassSettings {
    @JvmStatic
    fun dismiss(host: Activity) {
        val ref = PANELS[host]
        val panel = ref?.get()
        if (panel != null) panel.dialog.dismiss()
    }

    private fun bindSymbol(view: ImageView, host: Activity, resource: Int) {
        view.setTag(R.id.theme_symbol_resource, resource)
        view.setImageDrawable(TvSymbols.icon(host, resource))
        view.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    @JvmStatic
    fun showing(host: Activity): Boolean {
        val ref = PANELS[host]
        val panel = ref?.get()
        return panel != null && panel.dialog.isShowing
    }

    @JvmStatic
    fun dp(host: Activity, n: Float): Int {
        return Math.round(n * host.resources.displayMetrics.density)
    }

    /** Theme accent color, mirrored from the launcher's theme picker. */
    @JvmStatic
    fun themeAccent(ctx: Context): Int {
        val prefs = ThemePreferences.get(ctx)
        if (ThemeStyle.custom(prefs) || ThemeStyle.harmony(prefs)) return ThemeStyle.focusAccent(prefs)
        return when (prefs.getInt("theme_name", 0)) {
            1 -> 0xffff9a5c.toInt() // Sunset Orange
            2 -> 0xffb388ff.toInt() // Deep Purple
            3 -> TvGlassStyle.readable(prefs.getInt("custom_accent", 0xff40d9ff.toInt()))
            else -> 0xff40d9ff.toInt() // Midnight Cyan
        }
    }

    @JvmStatic
    fun background(host: Activity, focused: Boolean): GradientDrawable {
        val now = Calendar.getInstance()
        val prefs = ThemePreferences.get(host)
        if (ThemeStyle.harmony(prefs)) return HarmonyVisionSurface.control(host, focused)
        if (TvGlassStyle.enabled(prefs)) return TvGlassStyle.surface(dp(host, 18f), dp(host, if (focused) 2f else 1f), focused, prefs)
        val day = HomeAppearance.daylight(
            prefs.getString("theme_mode", "dark"),
            now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        )
        val light = day >= .5f
        // Theme-tinted glass (user 2026-11-01): the row background takes the theme's hue.
        val accent = themeAccent(host)
        val base = if (light) intArrayOf(if (focused) 0xf04c7b99.toInt() else 0xed3b607d.toInt(), if (focused) 0xf0375d7b.toInt() else 0xed28475f.toInt())
            else intArrayOf(if (focused) 0xe51a374b.toInt() else 0xc5132539.toInt(), if (focused) 0xed0b2337.toInt() else 0xd009182a.toInt())
        val tinted = intArrayOf(tint(base[0], accent), tint(base[1], accent))
        val shape = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, tinted)
        shape.cornerRadius = dp(host, 10f).toFloat()
        shape.setStroke(dp(host, if (focused) 2f else 1f), if (focused) themeAccent(host) else 0x55415a72)
        // Glass intensity: 0=airy, 100=solid.
        val intensity = Math.max(0, Math.min(100, prefs.getInt("glass_alpha", 70)))
        shape.alpha = PanelTransparency.alpha(ClassicGlassAlpha.card(intensity, focused), prefs, false)
        return shape
    }

    /** Mixes a glass base color 30% toward the theme accent. */
    private fun tint(base: Int, accent: Int): Int {
        return Color.rgb(
            (Color.red(base) + (Color.red(accent) - Color.red(base)) * 0.3f).toInt(),
            (Color.green(base) + (Color.green(accent) - Color.green(base)) * 0.3f).toInt(),
            (Color.blue(base) + (Color.blue(accent) - Color.blue(base)) * 0.3f).toInt()
        )
    }

    /** Runtime support can change under power-saving or compositor restrictions. */
    @JvmStatic
    fun frostedAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        if (MemoryProfile.effectiveTier(context, ThemePreferences.get(context)) == MemoryProfile.TIER_LIGHT) return false
        try {
            val manager = context.getSystemService(WindowManager::class.java)
            return manager != null && manager.isCrossWindowBlurEnabled
        } catch (unavailable: RuntimeException) {
            return false
        }
    }

    @JvmStatic
    fun applyBlur(dialog: Dialog?) {
        if (dialog == null || dialog.window == null) return
        val context = dialog.context
        val prefs = ThemePreferences.get(context)
        val glass = TvGlassStyle.enabled(prefs)
        val frosted = PanelTransparency.enabled(prefs) && TvGlassStyle.finish(prefs) == 2
        val blur = frostedAvailable(context) && frosted
        val window = dialog.window
        if (Build.VERSION.SDK_INT >= 31) {
            val attrs = window.attributes
            if (blur) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            attrs.setBlurBehindRadius(if (blur) (if (frosted) 28 else 40) else 0)
            // Preserve the updated flags when assigning the attributes again.
            attrs.flags = window.attributes.flags
            window.attributes = attrs
        }
        window.setDimAmount(if (glass) (if (frosted && !blur) .4f else (if (TvGlassStyle.finish(prefs) == 1) .22f else .32f)) else .12f)
    }

    private fun watchBlur(host: Activity, container: LinearLayout, dialog: AlertDialog) {
        if (Build.VERSION.SDK_INT < 31) return
        val manager = host.getSystemService(WindowManager::class.java)
        if (manager == null) return
        val listener = Consumer<Boolean> {
            if (dialog.isShowing) refresh(host)
        }
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            private var registered = false
            override fun onViewAttachedToWindow(view: View) {
                if (registered || Build.VERSION.SDK_INT < 31) return
                try {
                    manager.addCrossWindowBlurEnabledListener(host.mainExecutor, listener)
                    registered = true
                } catch (unavailable: RuntimeException) {
                    /* The opaque fallback is already applied. */
                }
            }

            override fun onViewDetachedFromWindow(view: View) {
                if (!registered || Build.VERSION.SDK_INT < 31) return
                try {
                    manager.removeCrossWindowBlurEnabledListener(listener)
                } catch (ignored: RuntimeException) {
                }
                registered = false
            }
        })
    }

    @JvmStatic
    fun label(host: Activity, text: String, size: Int, color: Int): TextView {
        val view = TextView(host)
        view.text = text
        view.setTextSize(size.toFloat())
        view.setTextColor(ThemeStyle.contentColor(ThemePreferences.get(host), color))
        applyAppFont(host, view)
        return view
    }

    /** Launcher-wide font (Settings → Theme → Launcher font) for settings UI text. (User 2026-11-01.) */
    @JvmStatic
    fun applyAppFont(ctx: Context, view: TextView) {
        val prefs = ThemePreferences.get(ctx)
        view.typeface = Typeface.create(ThemeStyle.fontFamily(prefs), Typeface.NORMAL)
        if (ThemeStyle.light(prefs)) {
            if (PanelTransparency.enabled(prefs) && TvGlassStyle.intensity(prefs) < 50)
                view.setShadowLayer(2 * ctx.resources.displayMetrics.density, 0f, 0f, 0xeeffffff.toInt())
            else view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
    }

    @JvmStatic
    fun row(host: Activity, title: String, subtitle: String?, icon: Int, tint: Int, action: Runnable): View {
        val row = LinearLayout(host)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(host, 12f), dp(host, 7f), dp(host, 12f), dp(host, 7f))
        row.minimumHeight = dp(host, 49f)
        row.isFocusable = true
        row.isFocusableInTouchMode = true
        row.isClickable = true
        row.background = background(host, false)
        row.setOnFocusChangeListener { _, focus -> row.background = background(host, focus || row.isSelected) }
        val symbol = ImageView(host)
        bindSymbol(symbol, host, icon)
        symbol.setColorFilter(tint)
        if (tint == themeAccent(host)) symbol.setTag("theme_accent")
        row.addView(symbol, LinearLayout.LayoutParams(dp(host, 24f), dp(host, 24f)))
        val copy = LinearLayout(host)
        copy.orientation = LinearLayout.VERTICAL
        copy.setPadding(dp(host, 13f), 0, dp(host, 6f), 0)
        val heading = label(host, title, 14, Color.WHITE)
        copy.addView(heading)
        if (subtitle != null && subtitle.isNotEmpty()) copy.addView(label(host, subtitle, 11, 0xffa9bfd3.toInt()))
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        val arrow = label(host, "›", 26, 0xff9bb6cb.toInt())
        row.addView(arrow)
        row.setOnClickListener { v ->
            v.requestFocus()
            action.run()
        }
        val position = LinearLayout.LayoutParams(-1, -2)
        position.setMargins(0, dp(host, 3f), 0, dp(host, 3f))
        row.layoutParams = position
        return row
    }

    private fun cardBackground(host: Activity, focused: Boolean): GradientDrawable {
        val shape = background(host, focused)
        shape.cornerRadius = dp(host, if (TvGlassStyle.enabled(ThemePreferences.get(host))) 20f else 16f).toFloat()
        return shape
    }

    /** Landscape tile: the icon and label share one row instead of stacking into a square. */
    @JvmStatic
    fun wideCard(host: Activity, title: String, icon: Int, tint: Int, action: Runnable): View {
        val card = LinearLayout(host)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(host, 11f), dp(host, 10f), dp(host, 11f), dp(host, 10f))
        card.minimumHeight = dp(host, 64f)
        card.isFocusable = true
        card.isFocusableInTouchMode = true
        card.isClickable = true
        card.setTag("wide_card")
        card.clipToOutline = true
        card.elevation = dp(host, 2f).toFloat()
        card.background = cardBackground(host, false)
        card.setOnFocusChangeListener { _, focus ->
            card.background = cardBackground(host, focus)
            card.translationZ = if (focus) dp(host, 3f).toFloat() else 0f
        }
        val symbol = ImageView(host)
        bindSymbol(symbol, host, icon)
        symbol.setColorFilter(tint)
        if (tint == themeAccent(host)) symbol.setTag("theme_accent")
        val image = LinearLayout.LayoutParams(dp(host, 26f), dp(host, 26f))
        image.marginEnd = dp(host, 9f)
        card.addView(symbol, image)
        val heading = label(host, title, 13, Color.WHITE)
        heading.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        heading.maxLines = 2
        heading.ellipsize = TextUtils.TruncateAt.END
        card.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        card.setOnClickListener { v ->
            v.requestFocus()
            action.run()
        }
        return card
    }

    private val PANELS = WeakHashMap<Activity, WeakReference<Panel>>()

    private class Panel(val dialog: AlertDialog, val content: LinearLayout) {
        val pages = ArrayDeque<Page>()
    }

    private class Page(val root: View) {
        val focus: View? = root.findFocus()
        val scroll: ScrollView = (root as LinearLayout).getChildAt(1) as ScrollView
        val scrollY: Int = scroll.scrollY

        fun restore() {
            if (focus != null && focus.isShown && focus.requestFocus()) {
                scroll.post { scroll.scrollTo(0, scrollY) }
            } else {
                focusRows(scroll.getChildAt(0) as LinearLayout)
            }
        }
    }

    @JvmStatic
    fun panel(host: Activity, title: String, rows: LinearLayout): AlertDialog {
        return panel(host, title, rows, false)
    }

    @JvmStatic
    fun panel(host: Activity, title: String, rows: LinearLayout, focusHeader: Boolean): AlertDialog {
        val reference = PANELS[host]
        var state = reference?.get()
        if (state == null || !state.dialog.isShowing) {
            val container = LinearLayout(host)
            container.orientation = LinearLayout.VERTICAL
            val frame = dp(host, 8f)
            container.setPadding(frame, frame, frame, frame)
            val dialog = ThemeDialogBuilder(host).setView(container).create()
            state = Panel(dialog, container)
            PANELS[host] = WeakReference(state)
            val current = state
            dialog.setOnKeyListener { _, key, event ->
                if (key != KeyEvent.KEYCODE_BACK || current.pages.isEmpty()) {
                    false
                } else {
                    if (event.action == KeyEvent.ACTION_UP) back(current)
                    true
                }
            }
            // Configure BEFORE show: no center-to-right reposition on the first visible frame.
            dialog.create()
            watchBlur(host, container, dialog)
            val window = dialog.window
            if (window != null) {
                val tv = (host.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
                val width = Math.min(dp(host, 350f), host.resources.displayMetrics.widthPixels - dp(host, 24f))
                window.setGravity(if (tv) Gravity.END else Gravity.CENTER)
                window.setLayout(width, if (tv) -1 else Math.round(host.resources.displayMetrics.heightPixels * .88f))
                window.setWindowAnimations(0)
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                val glass = TvGlassStyle.enabled(ThemePreferences.get(host))
                window.setBackgroundDrawableResource(android.R.color.transparent)
                window.setDimAmount(if (glass) .32f else .12f)
            }
        }
        val current = state!!
        if (current.content.childCount > 0) {
            val old = current.content.getChildAt(0)
            val saved = Page(old)
            current.content.removeView(old)
            current.pages.push(saved)
        }
        val page = LinearLayout(host)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(host, 16f), dp(host, 16f), dp(host, 16f), dp(host, 16f))
        page.setTag("settings_shell")
        styleShell(host, page)
        val bar = LinearLayout(host)
        bar.gravity = Gravity.CENTER_VERTICAL
        if (current.pages.isNotEmpty()) {
            val back = label(host, "‹", 26, Color.WHITE)
            back.gravity = Gravity.CENTER
            back.contentDescription = "Back to settings"
            back.isFocusable = true
            back.background = background(host, false)
            back.setOnClickListener { back(current) }
            bar.addView(back, LinearLayout.LayoutParams(dp(host, 32f), dp(host, 36f)))
        }
        val heading = label(host, title, 21, Color.WHITE)
        heading.setTypeface(heading.typeface, Typeface.BOLD)
        bar.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val settings = ImageView(host)
        bindSymbol(settings, host, R.drawable.ic_settings)
        settings.setColorFilter(ThemeStyle.foreground(ThemePreferences.get(host)))
        settings.setPadding(dp(host, 7f), dp(host, 7f), dp(host, 7f), dp(host, 7f))
        settings.contentDescription = host.getString(R.string.tv_settings)
        settings.id = View.generateViewId()
        settings.isFocusable = true
        settings.isClickable = true
        settings.nextFocusUpId = settings.id
        settings.background = background(host, false)
        bar.addView(settings, LinearLayout.LayoutParams(dp(host, 36f), dp(host, 36f)))
        page.addView(bar)
        val scroll = ScrollView(host)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(rows)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        current.content.addView(page, LinearLayout.LayoutParams(-1, -1))
        settings.setOnFocusChangeListener { _, f ->
            settings.background = background(host, f)
            if (f) scroll.scrollTo(0, 0)
        }
        settings.setOnClickListener {
            current.dialog.dismiss()
            try {
                host.startActivity(SystemSettings.intent(host, Settings.ACTION_SETTINGS))
            } catch (unavailable: ActivityNotFoundException) {
                Toast.makeText(host, R.string.not_available, Toast.LENGTH_SHORT).show()
            } catch (unavailable: SecurityException) {
                Toast.makeText(host, R.string.not_available, Toast.LENGTH_SHORT).show()
            }
        }
        val initialFocus = Runnable {
            if (focusHeader) {
                settings.requestFocus()
                scroll.scrollTo(0, 0)
            } else focusRows(rows)
        }
        current.dialog.setOnShowListener { initialFocus.run() }
        if (current.dialog.isShowing) rows.post(initialFocus)
        applyBlur(current.dialog)
        return current.dialog
    }

    private fun focusRows(rows: LinearLayout) {
        for (i in 0 until rows.childCount) {
            val row = rows.getChildAt(i)
            if (row.visibility == View.VISIBLE && row.isSelected && row.requestFocus()) return
        }
        for (i in 0 until rows.childCount) {
            val row = rows.getChildAt(i)
            if (row.visibility == View.VISIBLE && row.requestFocus()) return
        }
    }

    private fun back(current: Panel) {
        if (current.pages.isEmpty()) {
            current.dialog.dismiss()
            return
        }
        current.content.removeAllViews()
        val previous = current.pages.pop()
        current.content.addView(previous.root, LinearLayout.LayoutParams(-1, -1))
        previous.restore()
    }

    @JvmStatic
    fun options(host: Activity, title: String, names: Array<String>, hints: Array<String>?, selected: IntConsumer) {
        options(host, title, names, hints, null, selected)
    }

    @JvmStatic
    fun choices(
        host: Activity, title: String, names: Array<String>, current: IntSupplier,
        selected: IntConsumer
    ) {
        options(host, title, names, null, null, selected, current, null)
    }

    /** Retheme the visible page and saved parent pages without losing focus or scroll position. */
    @JvmStatic
    fun refresh(host: Activity) {
        val reference = PANELS[host]
        val state = reference?.get()
        if (state == null || !state.dialog.isShowing) return
        refreshViews(host, state.content)
        for (page in state.pages) refreshViews(host, page.root)
        applyBlur(state.dialog)
    }

    @JvmStatic
    fun styleDialog(host: Activity, dialog: AlertDialog) {
        if (dialog.window == null) return
        val shell = shellBackground(host)
        dialog.window!!.setBackgroundDrawable(shell)
        styleDialogText(host, dialog.window!!.decorView)
        applyBlur(dialog)
        for (which in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
            val button = dialog.getButton(which)
            if (button != null) {
                applyAppFont(host, button)
                button.setTextColor(ThemeStyle.foreground(ThemePreferences.get(host)))
            }
        }
    }

    private fun styleDialogText(host: Activity, view: View) {
        if (view is TextView) {
            val weight = view.typeface?.style ?: Typeface.NORMAL
            applyAppFont(host, view)
            view.setTypeface(view.typeface, weight)
            view.setTextColor(ThemeStyle.foreground(ThemePreferences.get(host)))
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) styleDialogText(host, view.getChildAt(i))
        }
    }

    private fun prefsFor(host: Activity): SharedPreferences {
        return ThemePreferences.get(host)
    }

    private fun styleShell(host: Activity, page: View) {
        val glass = TvGlassStyle.enabled(ThemePreferences.get(host))
        page.background = shellBackground(host)
        page.clipToOutline = true
        page.elevation = dp(host, if (glass) 6f else 0f).toFloat()
    }

    private fun shellBackground(host: Activity): GradientDrawable {
        if (ThemeStyle.harmony(ThemePreferences.get(host))) return HarmonyVisionSurface.sheet(host)
        if (ThemeStyle.id(ThemePreferences.get(host)) == ThemeStyle.GLASS) return GlassOsUi.controls(host)
        val glass = TvGlassStyle.enabled(ThemePreferences.get(host))
        val shell = if (glass) TvGlassStyle.surface(dp(host, 28f), dp(host, 1f), false, prefsFor(host))
            else GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xff122338.toInt(), 0xff09182a.toInt()))
        shell.cornerRadius = dp(host, if (glass) 28f else 24f).toFloat()
        val prefs = ThemePreferences.get(host)
        if (!glass) {
            shell.setStroke(dp(host, 1f), 0x55415a72)
            shell.alpha = PanelTransparency.alpha(ClassicGlassAlpha.panel(TvGlassStyle.intensity(prefs)), prefs, false)
        }
        return shell
    }

    private fun refreshViews(host: Activity, view: View) {
        if (view is ControlCenterGrid) view.refresh()
        if ("settings_shell" == view.tag) styleShell(host, view)
        if (view.isFocusable && view.background is GradientDrawable) {
            view.background = when {
                "wide_card" == view.tag -> cardBackground(host, view.isFocused)
                "control_center_tile" == view.tag && view.parent.parent is ControlCenterGrid ->
                    (view.parent.parent as ControlCenterGrid).surface(view.isFocused)
                else -> background(host, view.isFocused || view.isSelected)
            }
        }
        if (view is TextView) {
            val style = view.typeface?.style ?: Typeface.NORMAL
            applyAppFont(host, view)
            view.setTypeface(view.typeface, style)
            if (ThemeStyle.light(ThemePreferences.get(host))) view.setTextColor(ThemeStyle.foreground(ThemePreferences.get(host)))
        }
        if (view is ImageView) {
            val resource = view.getTag(R.id.theme_symbol_resource)
            if (resource is Int) bindSymbol(view, host, resource)
            if ("theme_accent" == view.tag) view.setColorFilter(themeAccent(host))
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) refreshViews(host, view.getChildAt(i))
        }
        if (view is ControlCenterGrid) view.status()
    }

    @JvmStatic
    fun options(host: Activity, title: String, names: Array<String>, hints: Array<String>?, icons: IntArray?, selected: IntConsumer) {
        options(host, title, names, hints, icons, selected, null, null)
    }

    @JvmStatic
    fun options(
        host: Activity, title: String, names: Array<String>, hints: Array<String>?, icons: IntArray?,
        selected: IntConsumer, visible: IntPredicate
    ) {
        options(host, title, names, hints, icons, selected, null, visible)
    }

    private fun options(
        host: Activity, title: String, names: Array<String>, hints: Array<String>?, icons: IntArray?,
        selected: IntConsumer, current: IntSupplier?, visible: IntPredicate?
    ) {
        val rows = LinearLayout(host)
        rows.orientation = LinearLayout.VERTICAL
        val dialog = panel(host, title, rows)
        val navigation = hints != null || title == "Online landscape screensaver"
        for (i in names.indices) {
            val index = i
            val hint = if (hints != null && i < hints.size) hints[i] else null
            val icon = if (icons != null && i < icons.size) icons[i] else R.drawable.ic_settings
            val checked = current?.asInt ?: -1
            val caption = names[i].replace("  ✓", "") + if (i == checked) "  ✓" else ""
            val option = row(host, caption, hint, icon, themeAccent(host), Runnable {})
            option.isSelected = i == checked
            option.setOnClickListener { v ->
                v.requestFocus()
                selected.accept(index)
                if (!navigation) {
                    val checkedNow = current?.asInt ?: index
                    for (j in 0 until rows.childCount) {
                        val copy = (rows.getChildAt(j) as LinearLayout).getChildAt(1) as LinearLayout
                        (copy.getChildAt(0) as TextView).text = names[j].replace("  ✓", "") + if (j == checkedNow) "  ✓" else ""
                        val choice = rows.getChildAt(j)
                        choice.isSelected = j == checkedNow
                        choice.background = background(host, choice.isFocused || choice.isSelected)
                    }
                }
            }
            rows.addView(option)
        }
        if (visible != null) {
            val updateRows = Runnable {
                for (i in 0 until rows.childCount) {
                    rows.getChildAt(i).visibility = if (visible.test(i)) View.VISIBLE else View.GONE
                }
            }
            updateRows.run()
            rows.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    updateRows.run()
                }

                override fun onViewDetachedFromWindow(view: View) {
                }
            })
        }
        applyBlur(dialog)
        dialog.show()
    }
}
