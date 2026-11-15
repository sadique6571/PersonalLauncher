package com.sadique.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast

import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import java.text.Collator
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Predicate

/** On-demand installed-app picker. Uses the launcher's glass resources; no backdrop capture or blur. */
internal class AppLibraryDialog(
    activity: Activity,
    animations: Boolean,
    reducedMotion: Boolean,
    selectionMode: Boolean,
    okOpens: Boolean,
    onHome: Predicate<String>,
    changed: Runnable,
    dismissed: Runnable,
    excludedApps: Set<String>
) {
    companion object {
        /**
         * The catalogue, the labels and the icons load here, off the UI thread: each label and icon opens the app's
         * resources, and on a TCL G09 that froze Home for 60-140 ms (2 to 4 janky frames) every time the library opened.
         */
        private val LOADER: ExecutorService = Executors.newSingleThreadExecutor()

    }

    private val main = Handler(Looper.getMainLooper())

    /** Icons loaded for this opening, dropped with it. */
    private val icons: MutableMap<String, Drawable?> = HashMap()
    private val iconsLoading: MutableSet<String> = HashSet()
    private val dismissed: Runnable

    private var closed = false

    /** Only the latest load fills the catalogue, and it shows the popup if an opening is waiting. */
    private val loads = LibraryLoads()
    private val activity: Activity
    private val onHome: Predicate<String>
    private val changed: Runnable
    private val panel: LinearLayout
    private val reducedMotion: Boolean
    private val selectionMode: Boolean

    /** library_ok_opens without app_library_selection: OK opens every app, Add to Home is in the hold menu. */
    private val okOpens: Boolean
    private val animations: Boolean
    private val rtl: Boolean
    private var instructions: TextView? = null
    private var totalApps = 0

    @JvmField
    val popup: PopupWindow
    private val density: Float
    private var apps: MutableList<ResolveInfo> = ArrayList()
    private var selectedPackage: String? = null
    private var selectedPosition = 0
    private var selectedOffset = 0
    private val installTimes: MutableMap<String, Long> = HashMap()
    private val labels: MutableMap<String, String> = HashMap()
    private var catalogTime = 0L
    private var actionPopup: PopupWindow? = null
    private var catalog: RecyclerView? = null
    private var shelf: FrameLayout? = null
    private var highlight: GlassView? = null
    private var spring: SpringAnimation? = null
    private var scrollOffset = 0f
    private var highlightShown = false
    private val catalogWidth: Int
    private val harmonyDrawer: Boolean
    private val drawerColumns: Int

    /** library_excluded_apps (config.xml), never offered in the catalogue; empty by default, so existing
     *  setups are unaffected. See LibraryApps.excludedFromCatalog(). */
    private val excludedApps: Set<String>

    /** Packages with a leanback entry, from the last refresh. */
    private val tvPackages: MutableSet<String> = HashSet()

    init {
        this.activity = activity
        this.animations = animations
        this.rtl = activity.resources.getBoolean(R.bool.rtl_app_grid)
        this.reducedMotion = reducedMotion
        this.selectionMode = selectionMode
        this.okOpens = okOpens && !selectionMode
        this.onHome = onHome
        this.changed = changed
        this.dismissed = dismissed
        this.excludedApps = excludedApps
        density = activity.resources.displayMetrics.density
        harmonyDrawer = ThemeStyle.harmony(ThemePreferences.get(activity))
        panel = LinearLayout(activity)
        panel.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(12), dp(8), dp(12), dp(12))
        panel.background = if (harmonyDrawer) HarmonyVisionSurface.sheet(activity) else null
        panel.clipToOutline = harmonyDrawer
        val decor = activity.window.decorView
        catalogWidth = Math.min(dp(800), Math.max(dp(200), decor.width - dp(64)))
        drawerColumns = HarmonyVisionLayout.drawerColumns(Math.round(catalogWidth / density))
        val width = catalogWidth
        val height = dp(if (harmonyDrawer) HarmonyVisionLayout.drawerHeightDp(activity.resources.configuration.screenHeightDp) else 280)
        popup = PopupWindow(panel, width, height, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.isOutsideTouchable = true
        popup.animationStyle = 0
        popup.setOnDismissListener {
            closed = true
            dismissActions()
            stopHighlight()
            icons.clear()
            dismissed.run()
        }
    }

    private fun stopHighlight() {
        spring?.cancel()
        catalog?.stopScroll()
        catalog = null
        highlightShown = false
    }

    private fun highlight(card: View, animate: Boolean) {
        val hl = highlight ?: return
        if (card.parent == null) return
        val rect = Rect(0, 0, card.width, card.height)
        shelf!!.offsetDescendantRectToMyCoords(card, rect)
        val lp = hl.layoutParams
        lp.width = rect.width() + dp(12)
        lp.height = rect.height() + dp(12)
        hl.layoutParams = lp
        hl.translationY = (rect.top - dp(6)).toFloat()
        val x = rect.left - dp(6) + scrollOffset
        if (!highlightShown || !animate || !animations) {
            spring!!.cancel()
            hl.translationX = x - scrollOffset
        } else {
            spring!!.animateToFinalPosition(x)
        }
        highlightShown = true
        hl.alpha = 1f
    }

    private fun surface(color: Int, radius: Int): GradientDrawable {
        if (radius > 8 && ThemeStyle.custom(ThemePreferences.get(activity))) return TvGlassStyle.drawerCard(dp(radius), dp(1), false, ThemePreferences.get(activity))
        val bg = GradientDrawable()
        bg.setColor(color)
        bg.cornerRadius = dp(radius).toFloat()
        bg.setStroke(dp(1), 0x18FFFFFF)
        return bg
    }

    private fun resize(width: Int, height: Int) {
        popup.width = width
        popup.height = height
        if (popup.isShowing) popup.update(width, height)
    }

    fun close() {
        dismissActions()
        if (popup.isShowing) {
            popup.dismiss()
        } else if (!closed) {
            closed = true   // still loading: it never shows
            dismissed.run()
        }
    }

    fun show() {
        load(true)
    }

    private fun reveal() {
        popup.showAtLocation(activity.window.decorView, Gravity.CENTER, 0, 0)
        val container = panel.rootView
        val params = container.layoutParams
        if (params is WindowManager.LayoutParams) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            params.dimAmount = 0.55f
            activity.windowManager.updateViewLayout(container, params)
        }
        panel.alpha = 0f
        panel.animate().alpha(1f).setDuration((if (reducedMotion) Motion.REDUCED_MS else 180).toLong()).start()
    }

    /** Called by the activity's existing package receiver; an uninstall cannot leave a stale action. */
    fun refresh() {
        load(false)
    }

    /**
     * Reads the catalogue on LOADER, then fills it on the UI thread. reveal: from opening the library; the popup
     * appears once the latest load has filled it, even when a refresh replaced the opening's own load.
     */
    private fun load(reveal: Boolean) {
        dismissActions()
        val load = loads.start(reveal)
        val pm = activity.packageManager
        val self = activity.packageName
        LOADER.execute {
            val listing = AppLibraryCatalog.load(pm, self, excludedApps)
            main.post {
                if (closed || !loads.isLatest(load)) return@post
                tvPackages.clear()
                tvPackages.addAll(listing.tvPackages)
                installTimes.clear()
                installTimes.putAll(listing.installTimes)
                labels.clear()
                labels.putAll(listing.labels)
                fill(listing.apps)
                if (loads.takeReveal()) reveal()
            }
        }
    }

    private fun fill(catalogue: MutableList<ResolveInfo>) {
        totalApps = catalogue.size
        apps = catalogue
        val home = HashMap<String, Boolean>()
        for (app in apps) {
            val pkg = app.activityInfo.packageName
            home[pkg] = onHome.test(pkg)
        }
        catalogTime = System.currentTimeMillis()
        val collator = Collator.getInstance()
        apps.sortWith(Comparator { a, b ->
            val pa = a.activityInfo.packageName
            val pb = b.activityInfo.packageName
            if (selectionMode) {
                LibraryApps.compareCatalog(home[pa]!!, label(a), pa, home[pb]!!, label(b), pb, collator)
            } else {
                val priority = LibraryApps.comparePriority(home[pa]!!, installTimes[pa]!!, home[pb]!!, installTimes[pb]!!, catalogTime)
                if (priority != 0) {
                    priority
                } else {
                    val byLabel = collator.compare(label(a), label(b))
                    if (byLabel != 0) byLabel else pa.compareTo(pb)
                }
            }
        })
        showCatalog()
    }

    private fun label(app: ResolveInfo): String {
        val pkg = app.activityInfo.packageName
        val cached = labels[pkg]
        if (cached != null) return cached
        val value = AppLibraryCatalog.loadLabel(activity.packageManager, app)
        labels[pkg] = value
        return value
    }

    /** The app's icon from this opening's cache, or loaded on LOADER and set once it arrives. */
    private fun bindIcon(icon: ImageView, app: ResolveInfo) {
        val pkg = app.activityInfo.packageName
        icon.tag = pkg
        val cached = icons[pkg]
        icon.setImageDrawable(cached)
        if (cached != null || !iconsLoading.add(pkg)) return
        val pm = activity.packageManager
        LOADER.execute {
            val loaded: Drawable? = try {
                app.loadIcon(pm)
            } catch (e: RuntimeException) {
                null
            }
            val result = loaded
            main.post {
                iconsLoading.remove(pkg)
                if (closed) return@post
                val shown = result ?: activity.getDrawable(R.drawable.ic_apps)
                icons[pkg] = shown
                // The view may show another app by now; a view bound to it later takes it from the cache.
                if (pkg == icon.tag) icon.setImageDrawable(shown)
            }
        }
    }

    private fun heading(value: String) {
        val title = text(value, 18, 0xFFFFFFFF.toInt())
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        title.setPadding(dp(12), dp(10), dp(12), dp(8))
        panel.addView(title, LinearLayout.LayoutParams(-1, -2))
    }

    /** Reuse translated actions instead of shipping another hint in every locale. */
    private fun hint(home: Boolean): String {
        if (selectionMode) return activity.getString(android.R.string.ok) + ": " +
            activity.getString(if (home) R.string.library_remove_shortcut else R.string.library_add_home)
        return activity.getString(if (home || okOpens) R.string.library_hint_open else R.string.library_hint_add)
    }

    private fun showCatalog() {
        stopHighlight()
        resize(catalogWidth, dp(if (harmonyDrawer) HarmonyVisionLayout.drawerHeightDp(activity.resources.configuration.screenHeightDp) else 280))
        panel.removeAllViews()
        val header = LinearLayout(activity)
        header.layoutDirection = View.LAYOUT_DIRECTION_LTR
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(12), dp(4), dp(8), dp(4))
        val title = text(activity.getString(R.string.app_library), 22, Color.WHITE)
        header.addView(title, LinearLayout.LayoutParams(0, dp(36), 1f))
        title.gravity = Gravity.CENTER_VERTICAL or (if (rtl) Gravity.RIGHT else Gravity.LEFT)
        title.setSingleLine(true)
        title.ellipsize = TextUtils.TruncateAt.END
        val count = text(activity.resources.getQuantityString(
            R.plurals.library_app_count, totalApps, totalApps), 13, 0xA6FFFFFF.toInt())
        count.setSingleLine(true)
        header.addView(count, LinearLayout.LayoutParams(-2, -2))
        // The launcher has no global supportsRtl: explicitly mirror this horizontal row.
        if (rtl) {
            header.removeView(count)
            header.addView(count, 0)
            count.setPadding(0, 0, dp(12), 0)
        }
        panel.addView(header, LinearLayout.LayoutParams(-1, dp(44)))
        val instr = text(hint(false), 13, 0xBBFFFFFF.toInt())
        instructions = instr
        instr.setSingleLine(true)
        instr.ellipsize = TextUtils.TruncateAt.END
        instr.setPadding(dp(12), 0, dp(12), dp(6))
        panel.addView(instr, LinearLayout.LayoutParams(-1, dp(26)))
        panel.isFocusableInTouchMode = apps.isEmpty()
        if (apps.isEmpty()) {
            instr.visibility = View.GONE
            heading(activity.getString(R.string.library_empty))
            panel.isFocusableInTouchMode = true
            panel.requestFocus()
            return
        }
        shelf = FrameLayout(activity)
        val sh = shelf!!
        // The glass uses physical x coordinates; only the catalogue mirrors its item order.
        sh.layoutDirection = View.LAYOUT_DIRECTION_LTR
        sh.clipChildren = true
        val list = RecyclerView(activity)
        list.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        catalog = list
        if (harmonyDrawer) list.layoutManager = GridLayoutManager(activity, drawerColumns)
        else list.layoutManager = object : LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false) {
            override fun isLayoutRTL(): Boolean = rtl
        }
        list.itemAnimator = null
        list.overScrollMode = View.OVER_SCROLL_NEVER
        list.clipToPadding = true
        list.setPadding(dp(10), dp(8), dp(10), dp(8))
        list.adapter = Catalog()
        sh.addView(list, FrameLayout.LayoutParams(-1, -1))
        val hl = GlassView(activity, null)
        highlight = hl
        hl.setRadius(dp(20).toFloat())
        hl.alpha = 0f
        hl.isFocusable = false
        hl.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        sh.addView(hl, FrameLayout.LayoutParams(dp(144), dp(180)))
        if (harmonyDrawer) hl.visibility = View.GONE
        scrollOffset = 0f
        val sp = SpringAnimation(hl, object : FloatPropertyCompat<View>("libraryScrollX") {
            override fun getValue(view: View): Float = view.translationX + scrollOffset
            override fun setValue(view: View, value: Float) {
                view.translationX = value - scrollOffset
            }
        })
        spring = sp
        if (reducedMotion) Motion.reduce(sp)
        else sp.spring = SpringForce().setStiffness(260f).setDampingRatio(0.58f)
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!harmonyDrawer) {
                    scrollOffset += dx.toFloat()
                    val h = highlight!!
                    h.translationX = h.translationX - dx.toFloat()
                }
            }
        })
        panel.addView(sh, LinearLayout.LayoutParams(-1, 0, 1f))
        var position = Math.min(selectedPosition, apps.size - 1)
        for (i in apps.indices) {
            if (apps[i].activityInfo.packageName == selectedPackage) {
                position = i
                break
            }
        }
        if (harmonyDrawer) (list.layoutManager as GridLayoutManager).scrollToPositionWithOffset(position, selectedOffset)
        else (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, selectedOffset)
        val target = position
        list.post {
            if (catalog != list || !popup.isShowing) return@post
            val holder = list.findViewHolderForAdapterPosition(target)
            holder?.itemView?.requestFocus()
        }
    }

    private fun dismissActions() {
        val menu = actionPopup
        actionPopup = null
        menu?.dismiss()
    }

    private fun restoreCardFocus() {
        val cat = catalog ?: return
        if (!popup.isShowing) return
        cat.post {
            val cat2 = catalog
            if (cat2 == null || !popup.isShowing || actionPopup != null) return@post
            val holder = cat2.findViewHolderForAdapterPosition(selectedPosition)
            holder?.itemView?.requestFocus()
        }
    }

    private fun changeHome(app: ResolveInfo, add: Boolean) {
        val saved = if (activity is PersonalHomeActivity) activity.libraryVisibility(app.activityInfo.packageName, add)
            else LibraryApps.save(activity, app.activityInfo.packageName, add)
        if (!saved) {
            Toast.makeText(activity, R.string.library_save_failed, Toast.LENGTH_LONG).show()
            return
        }
        if (selectionMode) {
            updateSelectionFocus(app, add)
            changed.run()
            fill(ArrayList(apps))
            return
        }
        selectedOffset = 0
        val cat = catalog
        if (add && cat != null) {
            val holder = cat.findViewHolderForAdapterPosition(selectedPosition)
            if (holder != null) {
                val card = holder.itemView
                val params = card.layoutParams as RecyclerView.LayoutParams
                selectedOffset = if (rtl)
                    cat.width - cat.paddingRight - (cat.layoutManager?.getDecoratedRight(card) ?: 0) - params.rightMargin
                else
                    (cat.layoutManager?.getDecoratedLeft(card) ?: 0) - params.leftMargin - cat.paddingLeft
            }
            // Advance through missing shortcuts instead of following the newly checked app
            // into the Home group. Preserve its screen slot when the catalogue is rebuilt.
            val next = LibraryApps.nextFocus(selectedPosition, apps.size) { index -> !onHome.test(apps[index].activityInfo.packageName) }
            if (next >= 0) {
                selectedPackage = apps[next].activityInfo.packageName
                selectedPosition = next
            }
        }
        dismissActions()
        changed.run()
        refresh()
    }

    private fun updateSelectionFocus(app: ResolveInfo, add: Boolean) {
        val position = apps.indexOf(app)
        val next = if (add) -1 else LibraryApps.nextAfterRemoval(position, apps.size)
        selectedOffset = 0
        val cat = catalog
        if (!add && cat != null) {
            val holder = cat.findViewHolderForAdapterPosition(position)
            if (holder != null) {
                val card = holder.itemView
                val params = card.layoutParams as RecyclerView.LayoutParams
                selectedOffset = if (rtl)
                    cat.width - cat.paddingRight - (cat.layoutManager?.getDecoratedRight(card) ?: 0) - params.rightMargin
                else
                    (cat.layoutManager?.getDecoratedLeft(card) ?: 0) - params.leftMargin - cat.paddingLeft
            }
        }
        selectedPackage = if (next >= 0) apps[next].activityInfo.packageName else app.activityInfo.packageName
    }

    private fun openFromCard(app: ResolveInfo, card: View) {
        if (!harmonyDrawer) {
            open(app)
            return
        }
        if (card.hasTransientState()) return
        card.animate().scaleX(1.045f).scaleY(1.045f).alpha(0.72f).setDuration((if (reducedMotion) Motion.REDUCED_MS else 150).toLong())
            .withEndAction {
                card.alpha = 1f
                card.scaleX = 1f
                card.scaleY = 1f
                if (popup.isShowing) open(app)
            }.start()
    }

    private fun open(app: ResolveInfo) {
        close()
        val intent = Intent(Intent.ACTION_MAIN).setComponent(
            ComponentName(app.activityInfo.packageName, app.activityInfo.name))
        // TV apps keep the leanback launch intent, so an app already running resumes instead of restarting.
        if (tvPackages.contains(app.activityInfo.packageName)) intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showActions(app: ResolveInfo, anchor: View) {
        dismissActions()
        val pkg = app.activityInfo.packageName
        selectedPackage = pkg
        spring?.cancel()
        val menuPanel = activity.layoutInflater.inflate(R.layout.popup_inputs, panel, false)
        if (TvGlassStyle.enabled(ThemePreferences.get(activity))) {
            menuPanel.background = TvGlassStyle.surface(dp(24), dp(1), false, ThemePreferences.get(activity))
            menuPanel.clipToOutline = true
        }
        val rows: LinearLayout = menuPanel.findViewById(R.id.rows)
        if (activity is PersonalHomeActivity) activity.liquidPanel(menuPanel, 24)
        val menu = PopupWindow(menuPanel, dp(220), ViewGroup.LayoutParams.WRAP_CONTENT, true)
        actionPopup = menu
        menu.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        menu.isOutsideTouchable = true
        menu.animationStyle = 0
        menu.elevation = 0f
        menu.setOnDismissListener {
            if (actionPopup == menu) actionPopup = null
            restoreCardFocus()
        }
        addAction(rows, R.string.library_open) { open(app) }
        val home = onHome.test(pkg)
        addAction(rows, if (home) R.string.library_remove_shortcut else R.string.library_add_home) { changeHome(app, !home) }
        val info = SystemSettings.appInfo(activity, pkg)
        if (info != null) addAction(rows, R.string.library_app_info) {
            close()
            try {
                activity.startActivity(info)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show()
            }
        }
        var menuWidth = dp(220)
        for (i in 0 until rows.childCount) {
            val title: TextView = rows.getChildAt(i).findViewById(R.id.title)
            menuWidth = Math.max(menuWidth, Math.ceil(title.paint.measureText(title.text.toString()).toDouble()).toInt() + dp(58))
            title.ellipsize = TextUtils.TruncateAt.END
        }
        menuWidth = Math.min(menuWidth, panel.width - dp(24))
        menu.width = menuWidth
        menuPanel.measure(View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val panelLocation = IntArray(2)
        panel.getLocationOnScreen(panelLocation)
        val screen = Rect(panelLocation[0], panelLocation[1],
            panelLocation[0] + panel.width, panelLocation[1] + panel.height)
        val x = Math.max(screen.left + dp(12), Math.min(location[0] + anchor.width - dp(12), screen.right - menuWidth - dp(12)))
        val y = Math.max(screen.top + dp(12), Math.min(location[1] + dp(8), screen.bottom - menuPanel.measuredHeight - dp(12)))
        menuPanel.alpha = 0f
        menuPanel.pivotY = 0f
        menuPanel.scaleY = 0.92f
        menuPanel.translationY = -dp(8).toFloat()
        menu.showAtLocation(activity.window.decorView, Gravity.TOP or Gravity.START, x, y)
        menuPanel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration((if (reducedMotion) Motion.REDUCED_MS else 220).toLong())
            .setInterpolator(DecelerateInterpolator(1.6f)).start()
        rows.getChildAt(0).requestFocus()
    }

    private fun addAction(rows: LinearLayout, label: Int, action: Runnable) {
        val row = activity.layoutInflater.inflate(R.layout.item_input, rows, false)
        val title: TextView = row.findViewById(R.id.title)
        title.setText(label)
        if (ThemeStyle.custom(ThemePreferences.get(activity))) {
            title.setTextColor(ThemeStyle.foreground(ThemePreferences.get(activity)))
            row.background = GlassSettings.background(activity, false)
            row.setOnFocusChangeListener { v, focused -> v.setBackground(GlassSettings.background(activity, focused)) }
        }
        row.isFocusableInTouchMode = true
        row.findViewById<View>(R.id.icon).visibility = View.GONE
        row.findViewById<View>(R.id.dot).visibility = View.GONE
        row.findViewById<View>(R.id.status).visibility = View.GONE
        row.setOnClickListener { action.run() }
        rows.addView(row)
        row.setOnKeyListener { v, key, event ->
            val index = rows.indexOfChild(v)
            event.action == KeyEvent.ACTION_DOWN && (key == KeyEvent.KEYCODE_DPAD_LEFT
                || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_UP && index == 0
                || key == KeyEvent.KEYCODE_DPAD_DOWN && index == rows.childCount - 1)
        }
    }

    private fun text(value: String, size: Int, color: Int): TextView {
        val view = TextView(activity)
        view.text = value
        view.textDirection = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_FIRST_STRONG
        view.gravity = if (rtl) Gravity.RIGHT else Gravity.LEFT
        view.setTextSize(size.toFloat())
        view.setTextColor(ThemeStyle.contentColor(ThemePreferences.get(activity), color))
        view.setShadowLayer(dp(2).toFloat(), 0f, 0f, if (ThemeStyle.light(ThemePreferences.get(activity))) 0xccffffff.toInt() else 0xcc000000.toInt())
        view.typeface = UiFont.get(activity, R.string.font_medium)
        if (ThemeStyle.custom(ThemePreferences.get(activity))) GlassSettings.applyAppFont(activity, view)
        view.letterSpacing = 0.01f
        view.includeFontPadding = false
        return view
    }

    private fun dp(value: Int): Int = Math.round(value * density)

    private inner class Catalog : RecyclerView.Adapter<Row>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row {
            val row = FrameLayout(activity)
            val params = RecyclerView.LayoutParams(dp(132), dp(170))
            params.setMargins(dp(6), dp(2), dp(6), dp(2))
            row.layoutParams = params
            row.setPadding(dp(10), dp(12), dp(10), dp(10))
            row.isFocusableInTouchMode = true
            row.isClickable = true
            val content = LinearLayout(activity)
            content.orientation = LinearLayout.VERTICAL
            content.gravity = Gravity.CENTER_HORIZONTAL
            row.addView(content, FrameLayout.LayoutParams(-1, -1))
            val icon = ImageView(activity)
            icon.id = R.id.icon
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
            content.addView(icon, LinearLayout.LayoutParams(dp(60), dp(60)))
            val name = text("", 15, Color.WHITE)
            name.id = R.id.title
            name.gravity = Gravity.CENTER
            name.maxLines = 2
            val nameParams = LinearLayout.LayoutParams(-1, dp(44))
            nameParams.topMargin = dp(10)
            content.addView(name, nameParams)
            val status = text("", 11, 0xBBFFFFFF.toInt())
            status.id = R.id.status
            status.gravity = Gravity.CENTER
            status.maxLines = 2
            content.addView(status, LinearLayout.LayoutParams(-1, -2))
            val check = View(activity)
            check.id = R.id.dot
            val badge = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP or Gravity.END)
            badge.setMargins(0, dp(2), dp(2), 0)
            row.addView(check, badge)
            return Row(row)
        }

        override fun onBindViewHolder(holder: Row, position: Int) {
            val app = apps[position]
            val pkg = app.activityInfo.packageName
            bindIcon(holder.itemView.findViewById(R.id.icon), app)
            val name: TextView = holder.itemView.findViewById(R.id.title)
            name.text = label(app)
            name.ellipsize = TextUtils.TruncateAt.END
            val status: TextView = holder.itemView.findViewById(R.id.status)
            val home = onHome.test(pkg)
            val recent = !selectionMode && LibraryApps.recent(installTimes.getOrDefault(pkg, 0L), catalogTime)
            status.setText(if (home) R.string.library_on_home else if (recent) R.string.library_recent else R.string.library_add_home)
            status.visibility = if (home || !okOpens) View.VISIBLE else View.INVISIBLE
            holder.itemView.background = surface(if (home) 0x1475BBA4 else 0x0CFFFFFF, 18)
            val check: View = holder.itemView.findViewById(R.id.dot)
            check.layoutParams.width = dp(if (home) 18 else 8)
            check.layoutParams.height = dp(if (home) 18 else 8)
            if (home) check.setBackgroundResource(R.drawable.library_check)
            else check.background = surface(0xFF70D9A0.toInt(), 8)
            check.visibility = if (home || recent) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { v ->
                val onHomeNow = onHome.test(pkg)
                if (LibraryApps.okOpens(selectionMode, okOpens, onHomeNow)) openFromCard(app, v)
                else changeHome(app, !onHomeNow)
            }
            holder.itemView.setOnLongClickListener(if (selectionMode) null else View.OnLongClickListener { v ->
                showActions(app, v)
                true
            })
            holder.itemView.setOnFocusChangeListener { v, focused ->
                if (harmonyDrawer) v.background = surface(if (focused) 0x5056a9e7 else if (onHome.test(pkg)) 0x1475BBA4 else 0x0CFFFFFF, 18)
                if (focused) {
                    selectedPackage = pkg
                    instructions!!.setText(hint(onHome.test(pkg)))
                    if (!harmonyDrawer) v.post { if (v.hasFocus() && catalog != null) highlight(v, true) }
                    val index = holder.bindingAdapterPosition
                    if (index != RecyclerView.NO_POSITION) selectedPosition = index
                }
            }
            holder.itemView.setOnKeyListener { _, key, event ->
                if (harmonyDrawer) false
                else {
                    val index = holder.bindingAdapterPosition
                    event.action == KeyEvent.ACTION_DOWN && (key == KeyEvent.KEYCODE_DPAD_UP
                        || key == KeyEvent.KEYCODE_DPAD_DOWN
                        || (if (rtl) key == KeyEvent.KEYCODE_DPAD_RIGHT else key == KeyEvent.KEYCODE_DPAD_LEFT) && index == 0
                        || (if (rtl) key == KeyEvent.KEYCODE_DPAD_LEFT else key == KeyEvent.KEYCODE_DPAD_RIGHT) && index == apps.size - 1)
                }
            }
        }

        override fun getItemCount(): Int = apps.size
    }

    private class Row(view: View) : RecyclerView.ViewHolder(view)
}
