package com.sadique.launcher

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

internal class HomeTileFactory(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val tv: Boolean,
    private val dp: (Int) -> Int,
    private val text: (String, Int, Int) -> TextView,
    private val tileRadius: () -> Int,
    private val cardHeight: (Int) -> Int,
    private val captionHeight: () -> Int,
    private val surface: (Int, Boolean) -> GradientDrawable,
    private val border: (Int, Boolean) -> Drawable?,
    private val perfAllows: (String) -> Boolean,
    private val accentColor: () -> Int,
    private val selectApp: (PersonalApp) -> Unit,
    private val updateFocusWallpaper: (View, PersonalApp, Boolean) -> Unit,
    private val launch: (Intent) -> Unit,
    private val isCarryingApp: () -> Boolean,
    private val showOptions: (PersonalApp) -> Unit,
    private val trackAttachment: (View, PersonalApp, HomeAppGrid<PersonalApp>) -> Unit,
) {
    private fun harmony(): Boolean = ThemeStyle.harmony(prefs)
    private fun glassOs(): Boolean = ThemeStyle.id(prefs) == ThemeStyle.GLASS || harmony()
    private fun tvGlass(): Boolean = tv && TvGlassStyle.enabled(prefs)

    fun create(app: PersonalApp, width: Int, moving: Boolean, g: HomeAppGrid<PersonalApp>): View {
        val tile: FrameLayout = if (harmony()) HarmonyAppTile(host, app.name, app.icon, app.accent, HarmonyVisionLayout.dockPlateDp(host.resources.configuration.screenHeightDp), g.isHorizontalDock())
                else if (glassOs()) GlassIconTile(host, app.name, app.icon, app.accent, cardHeight(width) - dp(captionHeight()), dp(captionHeight()), tileRadius(), moving)
                else if (ThemeStyle.custom(prefs)) DemoTile(host, ThemeStyle.id(prefs), tileRadius().toFloat())
                else FrameLayout(host)
        if (tile !is GlassIconTile && tile !is HarmonyAppTile) {
            tile.background = surface(app.accent, false)
            tile.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, tileRadius().toFloat())
                }
            }
            tile.clipToOutline = !ThemeStyle.custom(prefs)
        } else tile.clipToOutline = false
        tile.elevation = (if (perfAllows("parallax")) dp(if (tvGlass()) 4 else 3) else 0).toFloat()
        tile.isFocusable = true
        tile.isClickable = true
        tile.contentDescription = app.name
        tile.setTag(app.accent)
        if (tile !is GlassIconTile && tile !is HarmonyAppTile) tile.foreground = border(app.accent, false)
        if (moving) {
            tile.scaleX = 1.07f
            tile.scaleY = 1.07f
            tile.elevation = dp(10).toFloat()
            if (tile is HarmonyAppTile) tile.appearance(true)
            else if (tile is GlassIconTile) tile.appearance(app.accent, true)
            else tile.foreground = border(accentColor(), true)
        }
        val icon = ImageView(host)
        if (app.icon != null) icon.setImageDrawable(app.icon)
        else icon.setImageDrawable(TvSymbols.icon(host, R.drawable.ic_apps))
        // Artwork is composed at the card's 16:9 ratio; no second inset or crop here.
        icon.scaleType = if (ThemeStyle.custom(prefs)) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.FIT_XY
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        if (ThemeStyle.custom(prefs)) {
            val logoWidth = Math.max(dp(26), Math.round(width * 0.25f))
            val logo = FrameLayout.LayoutParams(logoWidth, -1, Gravity.START or Gravity.CENTER_VERTICAL)
            logo.marginStart = dp(10)
            logo.topMargin = dp(10)
            logo.bottomMargin = dp(10)
            tile.addView(icon, logo)
            val title = text(app.name, prefs.getInt("app_label_size", if (tv) 17 else 14), ThemeStyle.foreground(prefs))
            title.setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), if (ThemeStyle.light(prefs)) 0xeeffffff.toInt() else 0xee000000.toInt())
            title.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            title.maxLines = 2
            title.ellipsize = TextUtils.TruncateAt.END
            title.setTypeface(title.typeface, if (ThemeStyle.id(prefs) == ThemeStyle.DUNE) Typeface.NORMAL else Typeface.BOLD)
            val caption = FrameLayout.LayoutParams(-1, -1)
            if (ThemeStyle.id(prefs) != ThemeStyle.DUNE) {
                val separator = View(host)
                separator.setBackgroundColor(if (ThemeStyle.light(prefs)) 0x44708070 else 0x6682a2cf)
                val rule = FrameLayout.LayoutParams(dp(1), -1)
                rule.marginStart = logoWidth + dp(15)
                rule.topMargin = dp(18)
                rule.bottomMargin = dp(18)
                tile.addView(separator, rule)
            }
            caption.marginStart = logoWidth + dp(24)
            caption.marginEnd = dp(10)
            tile.addView(title, caption)
        } else if (tile !is GlassIconTile && tile !is HarmonyAppTile) tile.addView(icon, FrameLayout.LayoutParams(-1, -1))
        tile.setOnFocusChangeListener { v, focus ->
            if (tile is HarmonyAppTile) tile.appearance(focus || moving)
            else if (tile is GlassIconTile) tile.appearance(app.accent, focus || moving)
            else { tile.background = surface(app.accent, focus); if (!moving) tile.foreground = border(app.accent, focus) }
            if (focus) selectApp(app)
            // Java passed null to clear the expansion; expandKey takes a non-null String,
            // and "" behaves identically (HomeAppGrid.index returns -1 for both).
            if (glassOs() && !harmony() && !moving) g.expandKey(if (focus) app.info.activityInfo.packageName else "")
            if (!moving) {
                // Focus zoom: gentle scale animation.
                val scale = if (focus) (if (glassOs()) 1f else if (tvGlass()) 1.06f else 1.05f) else 1f
                val sy = if (glassOs()) 1f else scale
                val lift = 0f
                v.animate().cancel()
                if (harmony()) {
                    v.scaleX = 1f
                    v.scaleY = 1f
                    v.translationY = 0f
                } else if (MemoryProfile.effectiveTier(host, prefs) == MemoryProfile.TIER_LIGHT) {
                    v.scaleX = scale
                    v.scaleY = sy
                    v.translationY = lift
                } else v.animate().scaleX(scale).scaleY(sy).translationY(lift).setUpdateListener { _ -> g.refreshReflections() }.setDuration(if (tvGlass()) 180 else 150).start()
                if (glassOs() && !harmony()) g.refreshReflections()
            }
            tile.translationZ = (if (focus && prefs.getBoolean("focus_glow", true)) dp(10) else 0).toFloat()
            val tileParent = tile.parent
            if (glassOs() && !harmony() && tileParent is View) tileParent.translationZ = (if (focus) dp(10) else 0).toFloat()
            if (Build.VERSION.SDK_INT >= 28) {
                tile.outlineAmbientShadowColor = app.accent
                tile.outlineSpotShadowColor = app.accent
            }
            updateFocusWallpaper(tile, app, focus)
        }
        tile.setOnClickListener { v ->
            val open = Runnable {
                launch(Intent(Intent.ACTION_MAIN)
                        .setClassName(app.info.activityInfo.packageName, app.info.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            }
            if (isCarryingApp()) { open.run(); return@setOnClickListener }
            if (v is HarmonyAppTile && g.isHorizontalDock()) {
                v.launchEffect(open)
                return@setOnClickListener
            }
            v.animate().scaleX(if (glassOs()) 1f else 1.08f).scaleY(if (glassOs()) 1f else 1.08f).alpha(0.7f).setDuration(120).withEndAction {
                v.scaleX = if (glassOs()) 1f else 1.05f
                v.scaleY = if (glassOs()) 1f else 1.05f
                v.alpha = 1f
                open.run()
            }.start()
        }
        tile.setOnLongClickListener { showOptions(app); true }
        val position = FrameLayout.LayoutParams(-1, -2)
        val spacing = Math.max(0, Math.min(2, prefs.getInt("tile_spacing", 1)))
        val hm = if (harmony()) dp(3) else (if (tvGlass()) intArrayOf(dp(6), dp(10), dp(16)) else intArrayOf(dp(4), dp(8), dp(14)))[spacing]
        val vm = if (harmony()) dp(4) else (if (glassOs()) intArrayOf(dp(6), dp(8), dp(12)) else if (tvGlass()) intArrayOf(dp(8), dp(12), dp(18)) else intArrayOf(dp(5), dp(10), dp(16)))[spacing]
        position.width = width - hm * 2
        position.height = if (ThemeStyle.custom(prefs)) Math.max(dp(54), Math.round(position.width / DemoGeometry.ratio())) + Math.round(DemoGeometry.footer(ThemeStyle.id(prefs), dp(1).toFloat())) else Math.round(position.width * 9f / 16f)
        if (harmony()) { position.height = dp(HarmonyVisionLayout.dockTileHeightDp(host.resources.configuration.screenHeightDp)); position.width = -1 }
        else if (glassOs()) { position.height = cardHeight(width); position.width = -1 }
        if (g.isHorizontalDock()) position.height += dp(HarmonyVisionLayout.DOCK_HEADROOM_DP)
        position.setMargins(hm, vm, hm, vm)
        tile.layoutParams = position
        trackAttachment(tile, app, g)
        return tile
    }
}
