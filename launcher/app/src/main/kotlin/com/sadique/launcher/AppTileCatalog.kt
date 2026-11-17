package com.sadique.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet

import com.sadique.launcher.AppAdapter.Entry

internal object AppTileCatalog {
    data class Listing(val leanbackPackages: Set<String>, val entries: MutableList<Entry>)

    fun load(
        ctx: Context, cards: List<Cards.Card>, leanback: List<ResolveInfo>,
        libraryPreload: Set<String>, hidden: Set<String>, appLabels: Map<String, String>,
        appBanners: Map<String, String>, uniformTiles: Boolean, iconTiles: Set<String>,
        savedRanks: Map<String, Int>, favourites: List<String>, showHomeSettings: Boolean,
    ): Listing {
        val pm = ctx.packageManager
        // The LEANBACK query is shared with the module-card check (MainActivity); copy it before adding
        // library apps so the caller's list stays as it was.
        val found = ArrayList(leanback)
        val tvPackages = HashSet<String>()
        for (ri in found) {
            val ai = ri.activityInfo
            if (ai != null) tvPackages.add(ai.packageName)
        }
        // Copy before merging: LibraryApps.load() may return an immutable empty set.
        val added = HashSet(LibraryApps.load(ctx))
        added.addAll(libraryPreload)
        val removed = LibraryApps.loadRemoved(ctx)
        if (added.isNotEmpty()) {
            val seen = HashSet(tvPackages)
            for (ri in pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                val ai = ri.activityInfo
                if (ai != null && added.contains(ai.packageName) && seen.add(ai.packageName)) found.add(ri)
            }
        }
        val cardByPkg = HashMap<String, Cards.Card>()
        for (c in cards) cardByPkg[c.pkg] = c
        val fresh = ArrayList<Entry>()
        // Some system apps declare two leanback entry points under one name (Sony's Timers & Clock on the
        // BRAVIA VU31), which would show as two identical tiles: keep the first per package and label.
        val seen = HashSet<String>()
        for (ri in found) {
            val ai = ri.activityInfo ?: continue
            val pkg = ai.packageName
            if (pkg == ctx.packageName || !ai.exported
                || !LibraryApps.visible(tvPackages.contains(pkg), hidden.contains(pkg), added.contains(pkg), removed.contains(pkg))) continue
            val component = ComponentName(pkg, ai.name)
            val card = cardByPkg[pkg]
            if (card != null && card.component != component) continue   // the card stands in for this tile
            // Same cleaner as the card prompt: an app label with a bidi override or line separator must not
            // redraw the tile name as something else.
            val label = if (card != null) card.label else Cards.cleanLabel(
                if (appLabels.containsKey(pkg)) appLabels[pkg] else ri.loadLabel(pm).toString(), pkg)
            if (!seen.add(pkg + "\n" + label)) continue
            var banner: Drawable? = null
            // uniform_tiles draws the icon, so an app's banner is never loaded. A module card keeps its banner.
            if (!(uniformTiles && card == null)) {
                banner = bundledBanner(ctx, appBanners[pkg])
                if (banner == null) banner = if (iconTiles.contains(pkg)) iconBanner(ctx, ri.loadIcon(pm)) else loadBanner(ctx, pm, ri)
            }
            // The icon is only drawn (and sampled for the glow) when there is no banner, so a tile with a
            // banner skips loading and decoding it.
            val icon = if (banner == null) ri.loadIcon(pm) else null
            if (card != null) {
                fresh.add(Entry(Entry.CARD, label, component, banner, icon, card.caption, card.accent))
            } else {
                fresh.add(Entry(label, component, banner, icon))
            }
        }
        fresh.sortWith(Comparator { a, b ->
            // The owner's own order first; tiles it does not know (new installs) follow by the rule below.
            val saved = Order.compare(savedRanks, a.component.flattenToShortString(), b.component.flattenToShortString())
            if (saved != 0) saved
            else {
                val fa = favourites.indexOf(a.pkg)
                val fb = favourites.indexOf(b.pkg)
                if (fa >= 0 || fb >= 0) {
                    if (fa < 0) 1 else if (fb < 0) -1 else Integer.compare(fa, fb)
                } else {
                    // Cards come right after the configured dock apps, before the alphabetical grid.
                    val ca = a.kind == Entry.CARD
                    val cb = b.kind == Entry.CARD
                    if (ca != cb) (if (ca) -1 else 1)
                    else a.label.compareTo(b.label, ignoreCase = true)
                }
            }
        })
        // Retain the optional Default-home tile until dependent tile-ordering work is merged.
        if (showHomeSettings && HomeSettingsActivity.available(ctx)) {
            fresh.add(Entry(Entry.HOME_SETTINGS, ctx.getString(R.string.home_settings),
                ComponentName(ctx, HomeSettingsActivity::class.java), null,
                ctx.getDrawable(R.drawable.ic_home_settings), null, 0))
        }
        return Listing(tvPackages, fresh)
    }

    /** icon_tiles: the app's icon centred on a dark banner-shaped tile, for apps whose own banner crops badly. */
    private fun iconBanner(ctx: Context, icon: Drawable): Drawable {
        val w = 320
        val h = 180
        val side = 104
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF15202B.toInt())
        icon.setBounds((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2)
        icon.draw(canvas)
        return BitmapDrawable(ctx.resources, bmp)
    }

    /** A banner_* drawable bundled with the launcher, or null for none or a name that is not one. */
    private fun bundledBanner(ctx: Context, name: String?): Drawable? {
        if (name == null || !name.startsWith("banner_")) return null
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        return if (id != 0) ctx.getDrawable(id) else null
    }

    private fun loadBanner(ctx: Context, pm: PackageManager, ri: ResolveInfo): Drawable? {
        var id = ri.activityInfo.banner
        if (id == 0) id = ri.activityInfo.applicationInfo.banner
        var banner: Drawable? = null
        if (id != 0) {
            try {
                val resources = pm.getResourcesForApplication(ri.activityInfo.applicationInfo)
                // Keep at least xhdpi detail rather than downsampling artwork before the focus zoom.
                val density = Math.max(DisplayMetrics.DENSITY_XHIGH, ctx.resources.displayMetrics.densityDpi)
                banner = resources.getDrawableForDensity(id, density, null)
            } catch (ignored: PackageManager.NameNotFoundException) {
            } catch (ignored: Resources.NotFoundException) {
            }
        }
        if (banner == null) banner = ri.activityInfo.loadBanner(pm)
        // A square "banner" counts as none: the tile shows the app's icon and name instead of a blur.
        return if (banner != null && AppAdapter.isBannerShaped(banner.intrinsicWidth, banner.intrinsicHeight)) banner else null
    }
}
