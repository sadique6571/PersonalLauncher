package com.sadique.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

import androidx.recyclerview.widget.RecyclerView

import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order,
 * then empty slots up to a full row, see {@link DockSlots}), then the module cards the owner chose to show
 * (by label), the rest is alphabetical.
 */
class AppAdapter internal constructor(
    ctx: Context,
    listener: TileListener,
    dockSize: Int,
    cfg: Config
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface TileListener {
        fun onTileFocused(position: Int, card: View)

        /** Called for a D-pad direction key on a tile. Return true when handled (focus stays put). */
        fun onTileVerticalKey(position: Int, keyCode: Int): Boolean

        fun onLibraryRequested()
        fun onLibraryFocused()

        /** Hold OK on a tile: the activity shows the tile menu (move, remove from Home, app info). */
        fun onTileLongPress(position: Int, card: View)
        fun onLibraryUp()

        /** A tile whose launch_actions entry is "input:<id>": switch to that TV input instead of opening an app. */
        fun onOpenInput(inputId: String)
    }

    internal class Entry(
        @JvmField val kind: Int,
        @JvmField val label: String,
        @JvmField val component: ComponentName,
        @JvmField val banner: Drawable?,
        @JvmField val icon: Drawable?,
        caption: String?,
        accent: Int
    ) {
        @JvmField val pkg: String = component.packageName

        /** Text under the tile when it is focused in the grid: the label, plus the caption for a card. */
        @JvmField val name: String = if (caption == null) label else label + " \u00B7 " + caption

        /** Ambient glow colour; 0 until sampled from the banner, or the card's accent from the start. */
        @JvmField var ambient: Int = accent

        /** uniform_tiles card fill, top and bottom colour; null until sampled from the icon. */
        @JvmField var backdrop: IntArray? = null

        constructor(label: String, component: ComponentName, banner: Drawable?, icon: Drawable?) :
            this(APP, label, component, banner, icon, null, 0)

        companion object {
            const val APP = 0
            const val HOME_SETTINGS = 1
            const val CARD = 2
            const val EMPTY = 3
        }
    }

    companion object {
        const val FOCUS_SCALE = AppTileGeometry.FOCUS_SCALE

        /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
        const val DESIGN_W_DP = AppTileGeometry.DESIGN_W_DP
        const val DESIGN_H_DP = AppTileGeometry.DESIGN_H_DP

        /**
         * Tile width for a grid of this inner width: the cell minus room for the focus zoom and the glass
         * highlight, capped at the design size so 4 columns on a 1080p TV look exactly as before.
         */
        @JvmStatic
        fun tileWidthPx(innerWidthPx: Int, columns: Int, density: Float): Int =
            AppTileGeometry.tileWidthPx(innerWidthPx, columns, density)

        @JvmStatic
        fun tileHeightPx(tileWidthPx: Int): Int =
            AppTileGeometry.tileHeightPx(tileWidthPx)

        /** Height of one grid item: the tile, the name label under it and the item's own padding. */
        @JvmStatic
        fun itemHeightPx(tileHeightPx: Int, density: Float): Int =
            AppTileGeometry.itemHeightPx(tileHeightPx, density)

        /** The same for either layout: the plain grid (dock_row false) uses the compact spacing. */
        @JvmStatic
        fun itemHeightPx(tileHeightPx: Int, dockRow: Boolean, density: Float): Int =
            AppTileGeometry.itemHeightPx(tileHeightPx, dockRow, density)

        /**
         * dock_row false: 4dp padding above and below, a 4dp gap and a 20dp name label, and 16dp kept under the last
         * row. The plain grid fits more rows on the screen, and with item_app.xml's roomier spacing the tiles came
         * out small with wide gaps between them.
         */
        const val COMPACT_ITEM_EXTRA_DP = AppTileGeometry.COMPACT_ITEM_EXTRA_DP
        const val COMPACT_BOTTOM_ROOM_DP = AppTileGeometry.COMPACT_BOTTOM_ROOM_DP

        /** item_app.xml: 8dp padding above and below, a 7dp gap and the 22dp name label under the tile. */
        const val ITEM_EXTRA_DP = AppTileGeometry.ITEM_EXTRA_DP

        /** DockDecoration's offsets above and below the dock row. */
        const val DOCK_EXTRA_DP = AppTileGeometry.DOCK_EXTRA_DP

        /** Room kept under the last row rows_below_dock asks for (MainActivity places the dock with the same). */
        const val BOTTOM_ROOM_DP = AppTileGeometry.BOTTOM_ROOM_DP

        /** Room kept above the dock for the dock title (48sp hero name plus its 40dp gap) when dock_title is on. */
        const val TITLE_ROOM_DP = AppTileGeometry.TITLE_ROOM_DP

        /** dock_row false: room above the first row for its zoom and glass (MainActivity places the grid with it). */
        const val TOP_ROOM_DP = AppTileGeometry.TOP_ROOM_DP

        /**
         * Tile width that fits the columns across and, with rows_below_dock > 0, the dock (with its title above it,
         * if shown) plus that many rows down the grid's height; the smaller wins. With rows_below_dock = 0, or before
         * the grid has a height, only the width counts, as before. Without this, 2 or 3 rows below the dock did not
         * fit: the dock was pushed to the top and its title drew over the clock. With dock_row false there is no band
         * or title to make room for: the first row plus rows_below_dock rows fit under a small top margin.
         */
        @JvmStatic
        fun tileWidthPx(innerWidthPx: Int, gridHeightPx: Int, columns: Int, rowsBelowDock: Int, dockTitle: Boolean,
                        dockRow: Boolean, density: Float): Int =
            AppTileGeometry.tileWidthPx(innerWidthPx, gridHeightPx, columns, rowsBelowDock, dockTitle, dockRow, density)

        /** The smallest share of the width-only tile size that fitting rows_below_dock into the height may go to. */
        const val MIN_HEIGHT_FIT = AppTileGeometry.MIN_HEIGHT_FIT

        private const val ICON_SHARE = AppIconColors.ICON_SHARE

        /**
         * Some apps declare a small square image as their banner (Rockchip's Media Center ships a 64 px one). Cropped
         * over a 16:9 tile it is a blur. A drawable with no intrinsic size (a colour, a shape) still fills the tile.
         */
        @JvmStatic
        fun isBannerShaped(width: Int, height: Int): Boolean {
            return width <= 0 || height <= 0 || width.toFloat() >= height * 1.4f
        }

        /** Corner radius for a tile of this width: 16dp at the design width, proportionally less when smaller. */
        @JvmStatic
        fun cornerRadius(tileWidthPx: Int, density: Float): Float =
            AppTileGeometry.cornerRadius(tileWidthPx, density)
    }

    private val ctx: Context
    private val rowsBelowDock: Int
    private val dockTitle: Boolean

    /** The owner's saved order (reorder button, Order): key -> rank. Empty when there is none or it no longer applies. */
    private var savedRanks: Map<String, Int> = Collections.emptyMap()

    /** Dock size recorded with the saved order (Order.docked), or -1: then dock_apps says which tiles are docked. */
    private var savedDocked = -1

    /** Reorder mode: no empty dock slots, so every position holds a tile that can move. */
    private var reordering = false
    private val listener: TileListener
    private val dockSize: Int

    /** config dock_row: false makes the dock apps the first tiles of one plain grid (names shown, no empty slots). */
    private val dockRow: Boolean

    /** config single_row: tiles sit in one sideways row, each as wide as a grid column would be. */
    private val singleRow: Boolean

    /** config always_show_names: the alpha of an unfocused tile's name (0 hides it). */
    private val restNameAlpha: Float

    /** cinematic_home: only then does a launch record the last-opened app the shelf uses. */
    private val cinematic: Boolean

    /** Dock order (config.xml dock_apps, or a theme's config.json). */
    private val favourites: List<String>

    /** Grid apps fill the dock slots dock_apps leaves free (config.xml, fill_dock). */
    private val fillDock: Boolean

    /** Dock apps found at the last reload, and the empty slots after them. */
    private var docked = 0
    private var emptySlots = 0

    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private val hidden: Set<String>

    /** Apps pre-seeded into the App library's "added" set (config.xml library_apps); empty by default. */
    private val libraryPreload: Set<String>
    private val animations: Boolean
    private val advancedEffects: Boolean
    private val showLibrary: Boolean

    /** Packages with a leanback entry at the last reload(): their tiles launch with the leanback category. */
    private var leanbackPackages: Set<String> = HashSet()
    private val showHomeSettings: Boolean

    /** Calmer motion for weak GPUs (config.xml, reduced_motion): no overshoot, shorter fades. */
    private val reducedMotion: Boolean

    /** The focus zoom may overshoot (config.xml, focus_overshoot). */
    private val focusOvershoot: Boolean

    /** Package to the action its tile sends instead of the start screen (config.xml, launch_actions). */
    private val launchActions: Map<String, String>

    /** Hold OK opens the activity's tile menu (config.xml, tile_menu); false opens App info, as before. */
    private val tileMenu: Boolean

    /** Tile names and bundled banners that replace the app's own (config.xml, app_labels and app_banners). */
    private val appLabels: Map<String, String>
    private val appBanners: Map<String, String>

    /** Every app tile is its icon on a card tinted from that icon, never its banner (config.xml, uniform_tiles). */
    private val uniformTiles: Boolean

    /** Packages drawn from their icon and name even when they have a banner (config.xml icon_tiles). */
    private val iconTiles: Set<String>
    private val items: MutableList<Entry> = ArrayList()

    init {
        this.ctx = ctx
        this.listener = listener
        this.dockSize = dockSize
        this.favourites = cfg.dockApps
        this.fillDock = cfg.fillDock
        this.hidden = HashSet(cfg.hiddenApps)
        this.libraryPreload = HashSet(cfg.libraryApps)
        this.animations = cfg.animations
        this.advancedEffects = cfg.animations && cfg.advancedEffects
        this.showHomeSettings = cfg.showHomeSettingsShortcut
        this.showLibrary = LibraryApps.showPill(cfg.appLibrary, cfg.libraryButton, cfg.showLibraryPill)
        this.reducedMotion = cfg.reducedMotion
        this.focusOvershoot = cfg.focusOvershoot
        this.rowsBelowDock = cfg.rowsBelowDock
        this.dockTitle = cfg.dockTitle
        this.dockRow = cfg.dockRow
        this.singleRow = cfg.singleRow
        this.restNameAlpha = if (cfg.alwaysShowNames) 0.75f else 0f
        this.cinematic = cfg.cinematicHome
        this.launchActions = cfg.launchActions
        this.iconTiles = ctx.resources.getStringArray(R.array.icon_tiles).toHashSet()
        this.tileMenu = cfg.tileMenu
        this.appLabels = cfg.appLabels
        this.appBanners = cfg.appBanners
        this.uniformTiles = cfg.uniformTiles
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true)
    }

    /**
     * Rebuilds the tiles. A shown module card stands in for every tile of its package (same component,
     * so focus follows it); hidden_apps wins over a card, and a card's package in dock_apps takes that slot.
     */
    internal fun reload(cards: List<Cards.Card>, leanback: List<ResolveInfo>) {
        val listing = AppTileCatalog.load(
            ctx, cards, leanback, libraryPreload, hidden, appLabels, appBanners, uniformTiles,
            iconTiles, savedRanks, favourites, showHomeSettings,
        )
        leanbackPackages = listing.leanbackPackages
        val fresh = listing.entries
        docked = 0
        while (docked < fresh.size && isDocked(fresh[docked])) docked++
        emptySlots = if (dockRow && !reordering) DockSlots.empty(docked, dockSize, fresh.size, fillDock) else 0
        for (i in 0 until emptySlots) {
            fresh.add(docked + i, Entry(Entry.EMPTY, "", ComponentName(ctx, "empty-dock-slot-$i"),
                null, null, null, 0))
        }
        items.clear()
        items.addAll(fresh)
        notifyDataSetChanged()
    }

    /** Lets go of every tile and its artwork (Home is in the background and memory is short). */
    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == 1) {
            val row = LayoutInflater.from(ctx).inflate(R.layout.row_app_library, parent, false)
            val button: View = row.findViewById(R.id.btnLibrary)
            button.setOnClickListener { listener.onLibraryRequested() }
            button.setOnFocusChangeListener { _, focused -> if (focused) listener.onLibraryFocused() }
            button.setOnKeyListener { _, key, event ->
                if (event.action != KeyEvent.ACTION_DOWN) false
                else {
                    if (key == KeyEvent.KEYCODE_DPAD_UP) listener.onLibraryUp()
                    key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                        || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                }
            }
            return object : RecyclerView.ViewHolder(row) {}
        }
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        val density = parent.resources.displayMetrics.density
        val width = if (parent.width > 0) parent.width else parent.measuredWidth
        val inner = width - parent.paddingLeft - parent.paddingRight
        val w = tileWidthPx(inner, parent.height, dockSize, rowsBelowDock, dockTitle, dockRow, density)
        val h = tileHeightPx(w)
        val card: View = v.findViewById(R.id.card)
        val cardLp = card.layoutParams
        cardLp.width = w
        cardLp.height = h
        card.layoutParams = cardLp
        val name: View = v.findViewById(R.id.name)
        val nameLp = name.layoutParams as ViewGroup.MarginLayoutParams
        nameLp.width = w
        nameLp.topMargin = h + Math.round((if (dockRow) 7 else 4) * density)
        if (!dockRow) {
            // Compact spacing (COMPACT_ITEM_EXTRA_DP): 4dp item padding and a 20dp name instead of 8dp and 22dp.
            nameLp.height = Math.round(20 * density)
            val pad = Math.round(4 * density)
            v.setPadding(v.paddingLeft, pad, v.paddingRight, pad)
        }
        name.layoutParams = nameLp
        // A sideways row would stretch a match_parent item across the whole screen: give it a grid column's width.
        if (singleRow) v.layoutParams.width = inner / dockSize
        if (uniformTiles) {
            // tvOS-style tile: a large centred mark, no caption inside the card.
            val icon: View = v.findViewById(R.id.icon)
            val iconLp = icon.layoutParams as FrameLayout.LayoutParams
            iconLp.width = Math.round(h * ICON_SHARE)
            iconLp.height = Math.round(h * ICON_SHARE)
            iconLp.bottomMargin = 0
            icon.layoutParams = iconLp
        } else if (h < Math.round(DESIGN_H_DP * density)) {
            // Shrunk tiles (e.g. 5 or 6 columns): scale icon and bottom margin proportionally with tile height so the label does not overlap the icon.
            val icon: View = v.findViewById(R.id.icon)
            val iconLp = icon.layoutParams as FrameLayout.LayoutParams
            val scale = h.toFloat() / (DESIGN_H_DP * density)
            iconLp.width = Math.round(48f * density * scale)
            iconLp.height = Math.round(48f * density * scale)
            iconLp.bottomMargin = Math.round(14f * density * scale)
            icon.layoutParams = iconLp
            val label: View = v.findViewById(R.id.label)
            label.setPadding(label.paddingLeft, label.paddingTop, label.paddingRight, Math.round(4f * density))
        }
        return Holder(v, listener, animations, advancedEffects, reducedMotion, restNameAlpha, focusOvershoot)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (isLibraryAt(position)) return
        val h = holder as Holder
        val e = items[position]
        if (e.kind == Entry.EMPTY) {
            // A free dock slot keeps its place in the row and the band, but shows nothing and takes no focus.
            h.card.visibility = View.INVISIBLE
            h.card.isFocusable = false
            h.name.visibility = View.INVISIBLE
            h.banner.setImageDrawable(null)
            h.icon.setImageDrawable(null)
            h.card.setOnClickListener(null)
            h.card.setOnLongClickListener(null)
            return
        }
        h.card.visibility = View.VISIBLE
        h.card.isFocusable = true
        h.name.text = e.name
        // The dock has no labels, unless always_show_names asks for them (DockDecoration then makes room in the band).
        // In a single row every tile is the dock's kind: the big title above names it.
        val dockKind = dockRow && (singleRow || position < dockSize)
        h.name.visibility = if (dockKind && restNameAlpha == 0f) View.INVISIBLE else View.VISIBLE
        h.inDock = dockKind
        val focused = h.card.isFocused   // a rebind after reload() can hit the focused tile
        h.name.alpha = if (focused) 1f else restNameAlpha
        h.card.scaleX = if (focused && animations) focusScale() else 1f
        h.card.scaleY = if (focused && animations) focusScale() else 1f
        h.card.translationY = if (focused && advancedEffects && !dockKind) -4f * ctx.resources.displayMetrics.density else 0f
        h.card.translationZ = if (focused && advancedEffects) 8f * ctx.resources.displayMetrics.density else 0f
        h.banner.scaleX = if (focused && advancedEffects) 1.035f else 1f
        h.banner.scaleY = if (focused && advancedEffects) 1.035f else 1f
        // A fully opaque banner covers the whole card (centerCrop), so the card's own fill under it is drawn for
        // nothing on every frame; keep it under banners with transparent parts and under icon tiles.
        h.card.background = if (e.banner != null && e.banner.opacity == PixelFormat.OPAQUE) null else h.cardBackground
        if (uniformTiles && e.banner == null) {
            if (e.backdrop == null) e.backdrop = AppIconColors.backdrop(e.icon, ambientColorAt(position))
            h.card.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, e.backdrop!!)
        }
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner)
            h.banner.visibility = View.VISIBLE
            h.icon.visibility = View.GONE
            h.label.visibility = View.GONE
        } else {
            h.banner.setImageDrawable(null)
            h.banner.visibility = View.GONE
            h.icon.setImageDrawable(e.icon)
            h.icon.visibility = View.VISIBLE
            h.label.text = e.label
            h.label.visibility = if (uniformTiles) View.GONE else View.VISIBLE
        }
        h.card.setOnClickListener { launch(e) }
        h.card.setOnLongClickListener { v ->
            if (tileMenu) {
                val pos = h.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) listener.onTileLongPress(pos, v)
            } else {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(i)
                } catch (ignored: ActivityNotFoundException) {
                }
            }
            true
        }
    }

    /** Opens the tile's activity by explicit component, with no extras: a card can name nothing else. */
    private fun launch(e: Entry) {
        if (e.kind == Entry.HOME_SETTINGS) {
            ctx.startActivity(Intent(ctx, HomeSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        var i = Intent(Intent.ACTION_MAIN)
            .setComponent(e.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        // TV apps launch with the same leanback intent as from the stock launcher or the remote, so an app
        // already running resumes instead of restarting; only phone apps added from the library use plain MAIN.
        if (leanbackPackages.contains(e.pkg)) i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val action = launchActions[e.pkg]
        val inputId = LaunchActions.inputId(action)
        if (inputId != null) {
            listener.onOpenInput(inputId)
            return
        }
        if (action != null) {
            // NEW_TASK only: with RESET_TASK_IF_NEEDED, Android just brings a running task of the app to the front
            // ("Activity not started"), so the screen never opens while the app is up, e.g. MagentaTV's boot-time task.
            val byAction = LaunchActions.intent(e.pkg, action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (ctx.packageManager.resolveActivity(byAction, 0) != null) i = byAction
        }
        try {
            ctx.startActivity(i)
            if (cinematic) LastOpened.record(ctx, e.pkg)
            if (advancedEffects && !reducedMotion && ctx is Activity) {
                ctx.overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit)
            }
        } catch (ex: Exception) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int {
        return items.size + (if (showLibrary) 1 else 0)
    }

    fun appCount(): Int = items.size

    fun isLibraryAt(position: Int): Boolean = showLibrary && position == items.size

    override fun getItemViewType(position: Int): Int = if (isLibraryAt(position)) 1 else 0

    fun containsPackage(pkg: String): Boolean {
        for (entry in items) if (entry.pkg == pkg) return true
        return false
    }

    override fun getItemId(position: Int): Long {
        return if (isLibraryAt(position)) Long.MIN_VALUE else items[position].component.flattenToShortString().hashCode().toLong()
    }

    /** Uses this saved order and dock size from the next reload() (Order.ranks, Order.docked). */
    fun setSavedOrder(ranks: Map<String, Int>, docked: Int) {
        savedRanks = ranks
        savedDocked = docked
    }

    /**
     * A tile in the dock: one of the first savedDocked tiles of the saved order when one was recorded, so an app
     * the owner moved into the dock stays there (#499); else a dock_apps entry.
     */
    private fun isDocked(e: Entry): Boolean {
        if (savedDocked < 0) return favourites.contains(e.pkg)
        val rank = savedRanks[e.component.flattenToShortString()]
        return rank != null && rank < savedDocked
    }

    /** Reorder mode on or off; takes effect at the next reload() (empty dock slots are left out while on). */
    fun setReordering(on: Boolean) {
        reordering = on
    }

    fun hasEmptySlots(): Boolean {
        return emptySlots > 0
    }

    /** Tiles that can be moved: everything but the "Default home" tile, which always stays last. */
    fun movableCount(): Int {
        val n = items.size
        return if (n > 0 && items[n - 1].kind == Entry.HOME_SETTINGS) n - 1 else n
    }

    /** Keys of the movable tiles in their current order, for Order (empty dock slots are not tiles). */
    fun movableKeys(): List<String> {
        val keys = ArrayList<String>()
        for (i in 0 until movableCount()) {
            val e = items[i]
            if (e.kind != Entry.EMPTY) keys.add(e.component.flattenToShortString())
        }
        return keys
    }

    /** Moves one tile; the others shift by one. Stable ids keep the moved tile's view (and its focus). */
    fun move(from: Int, to: Int) {
        if (from == to || from < 0 || to < 0 || from >= movableCount() || to >= movableCount()) return
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
        // The dock row hides names by position: rebind the tiles whose side of the dock edge changed.
        if (from < dockSize || to < dockSize) {
            val lo = Math.min(from, to)
            val hi = Math.max(from, to)
            notifyItemRangeChanged(lo, hi - lo + 1)
        }
    }

    /** Stable key for the app at a position (its component), or null. */
    fun keyAt(position: Int): String? {
        return if (position >= 0 && position < items.size) items[position].component.flattenToShortString() else null
    }

    /** Position of the app with this key, or -1 when it is gone. */
    fun positionOf(key: String?): Int {
        if (key == null) return -1
        for (i in items.indices) {
            if (items[i].component.flattenToShortString() == key) return i
        }
        return -1
    }

    /** Dock slots that hold an app: the whole dock unless some slots are empty. */
    fun dockFilled(): Int {
        return if (emptySlots > 0) docked else dockSize
    }

    /** A position focus can land on: an empty dock slot gives the last dock app. */
    fun focusablePosition(position: Int): Int {
        return DockSlots.focusable(position, docked, emptySlots)
    }

    fun labelAt(position: Int): String {
        return if (position >= 0 && position < items.size) items[position].label else ""
    }

    fun packageAt(position: Int): String? {
        return if (position >= 0 && position < items.size) items[position].pkg else null
    }

    /** The tile's artwork, its banner or else its icon, for the cinematic shelf's backdrop (HeroArt). */
    fun artworkAt(position: Int): Drawable? {
        if (position < 0 || position >= items.size) return null
        val e = items[position]
        return if (e.banner != null) e.banner else e.icon
    }

    fun focusScale(): Float {
        return if (!animations) 1f else if (advancedEffects) 1.12f else 1.10f
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry; a card's accent when it set one. */
    fun ambientColorAt(position: Int): Int {
        if (position < 0 || position >= items.size) return 0xFF444444.toInt()
        val e = items[position]
        if (e.ambient == 0) {
            e.ambient = if (uniformTiles && e.banner == null) AppIconColors.dominantColor(e.icon) else AppIconColors.vividColor(if (e.banner != null) e.banner else e.icon)
        }
        return e.ambient
    }

    internal class Holder(
        v: View,
        listener: TileListener?,
        animations: Boolean,
        advancedEffects: Boolean,
        reducedMotion: Boolean,
        restNameAlpha: Float,
        overshoot: Boolean
    ) : RecyclerView.ViewHolder(v) {
        val card: View = v.findViewById(R.id.card)

        /** item_app.xml's tile_bg: the dark card behind an icon tile, or behind a banner that has transparent parts. */
        val cardBackground: Drawable? = card.background
        val banner: ImageView = v.findViewById(R.id.banner)
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
        val name: TextView = v.findViewById(R.id.name)
        val animations: Boolean = animations
        val advancedEffects: Boolean = advancedEffects
        val reducedMotion: Boolean = reducedMotion

        /** Alpha of the name while the tile is not focused (always_show_names). */
        val restNameAlpha: Float = restNameAlpha
        val overshoot: Boolean = overshoot

        /** A dock tile: it grows in place without advanced_effects' lift, which reads as off-centre in the band (#2135). */
        var inDock = false

        init {
            val density = v.resources.displayMetrics.density
            card.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    // 16dp at the design width, scaled down with the tile so small tiles keep the same shape.
                    outline.setRoundRect(0, 0, view.width, view.height, AppAdapter.cornerRadius(view.width, density))
                }
            }
            card.clipToOutline = true

            card.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) false
                else if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                    && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) false
                else {
                    val pos = bindingAdapterPosition
                    listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode)
                }
            }

            card.setOnFocusChangeListener { view, focused ->
                view.animate().cancel()
                banner.animate().cancel()
                name.animate().cancel()
                val scale = if (!animations) 1f else if (advancedEffects) 1.12f else 1.10f
                val s = if (focused) scale else 1f
                if (!animations) {
                    view.scaleX = 1f
                    view.scaleY = 1f
                    view.translationY = 0f
                    view.translationZ = 0f
                    banner.scaleX = 1f
                    banner.scaleY = 1f
                    name.alpha = if (focused) 1f else restNameAlpha
                    name.translationY = 0f
                } else if (reducedMotion) {
                    // A short zoom with no hardware layer: building a layer for so short an animation costs more
                    // frames than it saves. The name just appears instead of fading.
                    view.animate().scaleX(s).scaleY(s)
                        .translationY(if (focused && advancedEffects && !inDock) -4f * density else 0f)
                        .translationZ(if (focused && advancedEffects) 8f * density else 0f)
                        .setDuration((if (focused) Motion.REDUCED_MS else 60).toLong())
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    name.alpha = if (focused) 1f else restNameAlpha
                    // Same place as the full motion's slide ends: the layout's -6dp is only its starting point, and
                    // left there the name overlaps the zoomed tile.
                    // Only on focus: snapping it back to -6dp while it fades out would jump it up over the tile.
                    if (focused) name.translationY = density * 4f
                } else {
                    view.animate().scaleX(s).scaleY(s)
                        .translationY(if (focused && advancedEffects && !inDock) -4f * density else 0f)
                        .translationZ(if (focused && advancedEffects) 8f * density else 0f)
                        .setDuration((if (advancedEffects) (if (focused) 220 else 170) else (if (focused) 260 else 180)).toLong())
                        .setInterpolator(if (focused && !overshoot) DecelerateInterpolator(1.5f)
                            else OvershootInterpolator(if (focused) (if (advancedEffects) 0.7f else 1.6f) else 0f))
                        .withLayer().start()
                    if (advancedEffects) {
                        banner.animate().scaleX(if (focused) 1.035f else 1f).scaleY(if (focused) 1.035f else 1f)
                            .setDuration(280).start()
                    }
                    name.animate().alpha(if (focused) 1f else restNameAlpha)
                        .translationY(if (focused) density * 4f else -density * 6f)
                        .setDuration(220).start()
                }
                val pos = bindingAdapterPosition
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view)
                }
            }
        }
    }
}
