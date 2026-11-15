package com.sadique.launcher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.HashMap
import com.sadique.launcher.MainActivity.Companion.COLUMNS

internal class LauncherShelf(
    private val host: Activity,
    cfg: Config,
    private val density: Float,
    private val currentAdapter: () -> AppAdapter,
    private val isRtlGrid: () -> Boolean,
) {
    private lateinit var ambient: View
    private lateinit var heroName: TextView
    /** The top shelf: heroName, plus the cinematic shelf's "Last opened" line and dock position dots. */
    private lateinit var hero: View
    private lateinit var heroMeta: TextView
    private lateinit var heroDots: LinearLayout
    /** Cinematic top shelf (config.xml, cinematic_home): the focused dock app's artwork behind a larger shelf. */
    private var cinematic = false
    /** config.xml ambient_glow, overridable by a theme. Off: the glow view is gone and no fade or colour animator
     *  is started for it, so a focus move costs nothing extra on a launcher that runs all day. */
    private var ambientGlow = false
    /** config.xml glow_style: the shape setAmbient paints the glow mask in. */
    private lateinit var glowStyle: String
    /** config.xml cinematic_white_focus: the cinematic shelf keeps the white highlight. */
    private lateinit var heroArt: ImageView
    /** config.xml cinematic_dots: false leaves the dock position dots out of the cinematic shelf. */
    private var heroDotsShown = true
    /** The artwork's opacity behind the cinematic shelf (config.xml cinematic_art); 0 leaves it out. */
    private var heroArtAlpha = 0.5f
    /** Softened artwork per app (HeroArt, about 5 KB each), made the first time its dock tile is focused. */
    private val heroArtCache: MutableMap<String, Bitmap> = HashMap()
    /** The app whose artwork heroArt shows, so focusing it again only fades it back in. */
    private var heroArtKey: String? = null
    /** The focus glow's colour while the cinematic shelf tints it, 0 for plain white glass. */
    private var ambientColor = -1
    private var ambientAnimator: ValueAnimator? = null
    private var heroShown = false

    private val animations = cfg.animations
    private val reducedMotion = cfg.reducedMotion
    private val advancedEffects = cfg.animations && cfg.advancedEffects
    private val adapter get() = currentAdapter()
    private val rtlAppGrid get() = isRtlGrid()
    val measuredHeight: Int get() = hero.measuredHeight

    init {
        ambient = host.findViewById(R.id.ambient)
        // The glow is one bitmap, so its alpha can be applied while drawing it. Otherwise every frame of
        // a fade renders it into a full-screen offscreen buffer first.
        ambient.forceHasOverlappingRendering(false)
        ambientGlow = cfg.ambientGlow
        if (!ambientGlow) ambient.visibility = View.GONE
        heroName = host.findViewById(R.id.heroName)
        hero = host.findViewById(R.id.hero)
        // The hero is now a group (name, and the cinematic lines): without this every fade of the default
        // shelf would render it through an offscreen buffer first, as the glow above notes.
        hero.forceHasOverlappingRendering(false)
        heroMeta = host.findViewById(R.id.heroMeta)
        heroDots = host.findViewById(R.id.heroDots)
        heroArt = host.findViewById(R.id.heroArt)
        cinematic = cfg.cinematicHome
        glowStyle = cfg.glowStyle
        heroDotsShown = cfg.cinematicDots
        heroArtAlpha = cfg.cinematicArt / 100f
        if (cinematic) setUpCinematicShelf(cfg.cinematicScrim)
        // dock_title false: the name above the dock (and the cinematic lines under it) never shows; the glow
        // still follows the dock.
        if (!cfg.dockTitle) hero.visibility = View.GONE
    }

    fun resetForReload() {
        heroShown = false
        hero.alpha = 0f
        ambient.alpha = 0f
        heroArt.animate().cancel()
        heroArt.alpha = 0f
    }

    fun clearArtwork() {
        heroArtCache.clear()
        heroArtKey = null
    }

    fun cancelAnimations() {
        hero.animate().cancel()
        ambient.animate().cancel()
        heroArt.animate().cancel()
    }

    // ---- top shelf -------------------------------------------------------------------------------

    fun showHero(position: Int, dockTopInRoot: Float) {
        val label = adapter.labelAt(position)
        val color = adapter.ambientColorAt(position)
        val y = dockTopInRoot - 14 * density - 26 * density - hero.height.toFloat()
        val changed = !label.contentEquals(heroName.text)
        if (cinematic && heroArtAlpha > 0f) showHeroArt(position)

        if (!animations || reducedMotion) {
            // Snap the shelf instead of sliding and fading it: each of those frames repaints the
            // full-screen panel, and this switch exists for the GPUs that cannot afford it.
            hero.animate().cancel()
            ambient.animate().cancel()
            fillHero(position, label)
            hero.y = y
            hero.translationX = 0f
            hero.alpha = 1f
            if (ambientGlow) {
                setAmbient(color)
                ambient.alpha = 1f
            }
            heroShown = true
            return
        }

        if (!heroShown) {
            stopAmbientAnimation()
            hero.animate().cancel()
            ambient.animate().cancel()
            fillHero(position, label)
            hero.y = y
            hero.translationX = 0f
            hero.animate().alpha(1f).setDuration(260).start()
            if (ambientGlow) {
                setAmbient(color)
                ambient.animate().alpha(1f).setDuration(600).start()
            }
            heroShown = true
        } else if (changed) {
            if (advancedEffects) hero.animate().cancel()
            hero.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction {
                fillHero(position, label)
                hero.y = y
                hero.translationX = 12 * density
                hero.animate().alpha(1f).translationX(0f).setDuration(200).start()
            }.start()
            if (ambientGlow && advancedEffects) {
                transitionAmbient(color)
            } else if (ambientGlow && color != ambientColor) {
                ambient.animate().alpha(0.35f).setDuration(200).withEndAction {
                    setAmbient(color)
                    ambient.animate().alpha(1f).setDuration(500).start()
                }.start()
            }
        } else {
            hero.y = y
            // onPause cancels the fade-ins, and a second Home during one lands on the same tile, which starts none:
            // finish them, or the shelf and the glow stay dim until focus moves to another app.
            if (hero.alpha < 1f) hero.animate().alpha(1f).setDuration(260).start()
            if (ambientGlow && color == ambientColor && ambient.alpha < 1f) {
                ambient.animate().alpha(1f).setDuration(600).start()
            }
        }
    }

    /** The shelf's text for this dock app: its name and, on the cinematic shelf, "Last opened" and the dots. */
    private fun fillHero(position: Int, label: String) {
        heroName.text = label
        if (!cinematic) return
        updateHeroMeta(position)
        updateHeroDots(position)
    }

    // ---- cinematic shelf -------------------------------------------------------------------------

    /**
     * The parts that never change: a larger name, the meta line and the dots, the artwork and the scrim, at
     * scrimPercent of its strength (cinematic_scrim). cinematic_art 0 leaves the artwork out, 0 percent the scrim.
     */
    private fun setUpCinematicShelf(scrimPercent: Int) {
        heroName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 68f)
        heroName.letterSpacing = -0.025f
        heroMeta.visibility = View.INVISIBLE   // keeps its height, so the name does not jump when it fills in
        heroDots.visibility = if (heroDotsShown) View.VISIBLE else View.GONE
        heroArt.visibility = if (heroArtAlpha > 0f) View.VISIBLE else View.GONE
        // One stretched bitmap: apply its alpha while drawing instead of through an offscreen buffer.
        heroArt.forceHasOverlappingRendering(false)
        if (scrimPercent <= 0) return   // cinematic_scrim 0: no scrim at all
        val scrim = host.findViewById<View>(R.id.heroScrim)
        val shade = scrimDrawable()
        // On the bitmap's paint, not the view: a view's alpha would draw the full screen through an offscreen buffer.
        shade.alpha = scrimPercent * 255 / 100
        scrim.background = shade
        scrim.visibility = View.VISIBLE
    }

    /**
     * Darkens the side the shelf's text is on and the lower part of the screen, so the name, the dock and
     * the grid stay readable over the artwork. One small bitmap stretched over the screen, like the glow.
     */
    private fun scrimDrawable(): Drawable {
        val w = 480
        val h = 270
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rtl = host.resources.getBoolean(R.bool.rtl_menu_bar)
        val paint = Paint()
        paint.shader = LinearGradient(if (rtl) w.toFloat() else 0f, 0f, if (rtl) 0f else w.toFloat(), 0f,
                intArrayOf(0xD1060406.toInt(), 0x59060406.toInt(), 0x00060406), floatArrayOf(0f, 0.42f, 0.7f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                intArrayOf(0x00060406, 0x00060406, 0xDB060406.toInt(), 0xFF060406.toInt()), floatArrayOf(0f, 0.28f, 0.58f, 0.76f),
                Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        val scrim = BitmapDrawable(host.resources, bmp)
        scrim.setGravity(Gravity.FILL)
        return scrim
    }

    /** Swaps the backdrop to this app's artwork: fade out, swap, fade in, the way the shelf's name changes. */
    private fun showHeroArt(position: Int) {
        val key = adapter.keyAt(position) ?: return
        heroArt.animate().cancel()
        if (key == heroArtKey) {
            fadeHeroArt(420)
            return
        }
        var art = heroArtCache[key]
        if (art == null) {
            art = HeroArt.from(adapter.artworkAt(position))
            if (art != null) heroArtCache[key] = art
        }
        heroArtKey = key
        val next = art
        if (!animations || reducedMotion || heroArt.alpha == 0f) {
            heroArt.setImageBitmap(next)
            fadeHeroArt(600)
            return
        }
        heroArt.animate().alpha(0f).setDuration(140).withEndAction {
            heroArt.setImageBitmap(next)
            heroArt.animate().alpha(heroArtAlpha).setDuration(420).start()
        }.start()
    }

    private fun fadeHeroArt(ms: Int) {
        if (!animations) heroArt.alpha = heroArtAlpha
        else heroArt.animate().alpha(heroArtAlpha).setDuration(if (reducedMotion) Math.min(ms, Motion.REDUCED_MS).toLong() else ms.toLong()).start()
    }

    /** "Last opened yesterday, 9:40 PM", the time in white; nothing (but the same height) when it was never seen. */
    private fun updateHeroMeta(position: Int) {
        val whenMs = LastOpened.get(host, adapter.packageAt(position))
        if (whenMs <= 0) {
            heroMeta.visibility = View.INVISIBLE
            return
        }
        val rel = LastOpened.describe(host, whenMs)
        val text = host.getString(R.string.last_opened, rel)
        val line = SpannableString(text)
        val at = text.indexOf(rel)
        if (at >= 0) line.setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), at, at + rel.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        heroMeta.text = line
        heroMeta.visibility = View.VISIBLE
    }

    /** One dot per dock app, the focused one drawn long and white. Hebrew docks start on the right, so do the dots. */
    private fun updateHeroDots(position: Int) {
        if (!heroDotsShown) return
        val count = Math.min(adapter.dockFilled(), COLUMNS)
        heroDots.visibility = if (count > 1) View.VISIBLE else View.INVISIBLE
        if (heroDots.childCount != count) {
            heroDots.removeAllViews()
            for (i in 0 until count) {
                val dot = View(host)
                val bg = GradientDrawable()
                bg.cornerRadius = 3 * density
                dot.background = bg
                val lp = LinearLayout.LayoutParams((6 * density).toInt(), (6 * density).toInt())
                if (i > 0) lp.leftMargin = (6 * density).toInt()
                heroDots.addView(dot, lp)
            }
        }
        for (i in 0 until count) {
            val dot = heroDots.getChildAt(if (rtlAppGrid) count - 1 - i else i)
            val on = i == position
            (dot.background as GradientDrawable).setColor(if (on) 0xFFFFFFFF.toInt() else 0x4DFFFFFF.toInt())
            val lp = dot.layoutParams
            val w = ((if (on) 20 else 6) * density).toInt()
            if (lp.width != w) {
                lp.width = w
                dot.layoutParams = lp
            }
        }
    }

    /**
     * The glow used to be a full-screen radial GradientDrawable. On Android 10 that drawable is
     * drawn on the CPU, so fading it for half a second redrew 1920×1080 every frame and the
     * highlight looked like it was running at a few frames per second. A small bitmap stretched
     * over the screen is one texture. It is painted once in white and tinted with the app's colour,
     * so changing apps neither repaints it on the CPU (about 11 ms) nor uploads it again.
     */
    private fun setAmbient(color: Int) {
        if (color == ambientColor && ambient.background != null) return
        ambientColor = color
        var glow = ambient.background as? BitmapDrawable
        if (glow == null) {
            val w = 480
            val h = 270
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (cinematic) {
                // Higher and stronger, from the corner away from the shelf's text: the app's colour floods
                // the top of the screen over its artwork, and the scrim keeps the text side dark.
                val rtl = host.resources.getBoolean(R.bool.rtl_menu_bar)
                paint.shader = RadialGradient(
                        w * (if (rtl) 0.20f else 0.80f), h * 0.06f, w * 0.78f,
                        intArrayOf(0xA6FFFFFF.toInt(), 0x47FFFFFF.toInt(), 0x00FFFFFF),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP)
            } else if (Config.GLOW_HORIZON == glowStyle) {
                // The colour washes down from the top edge and fades out three quarters of the way down,
                // with a brighter band along the top like light over a horizon.
                paint.shader = LinearGradient(0f, 0f, 0f, h * 0.75f,
                        intArrayOf(0x66FFFFFF.toInt(), 0x24FFFFFF.toInt(), 0x00FFFFFF),
                        floatArrayOf(0f, 0.4f, 1f),
                        Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = RadialGradient(
                        w * 0.5f, -h * 0.25f, w * 0.55f,
                        intArrayOf(0x40FFFFFF.toInt(), 0x00FFFFFF),
                        null,
                        Shader.TileMode.CLAMP)
            } else {
                paint.shader = RadialGradient(
                        w * 0.32f, h * 0.42f, w * 0.62f,
                        intArrayOf(0x5CFFFFFF.toInt(), 0x26FFFFFF.toInt(), 0x00FFFFFF),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            glow = BitmapDrawable(host.resources, bmp)
            glow.setGravity(Gravity.FILL)
            ambient.background = glow
        }
        // The default tint mode, SRC_IN, keeps the mask's alpha and takes the colour from the tint.
        glow.setTint(0xFF000000.toInt() or color)
    }

    private fun transitionAmbient(color: Int) {
        // A rapid focus change continues from the colour actually displayed on screen.
        stopAmbientAnimation()
        ambient.animate().cancel()
        if (color == ambientColor) {
            ambient.animate().alpha(1f).setDuration(180).start()
            return
        }
        ambient.animate().alpha(1f).setDuration(180).start()
        val animator = ValueAnimator.ofObject(ArgbEvaluator(), ambientColor, color)
        ambientAnimator = animator
        animator.duration = 360
        animator.addUpdateListener { animation -> setAmbient(animation.animatedValue as Int) }
        animator.start()
    }

    fun stopAmbientAnimation() {
        ambientAnimator?.cancel()
        ambientAnimator = null
    }

    /**
     * Grid tiles (every tile with dock_row false): the glow behind the grid takes the focused tile's colour; the big
     * name above the dock does not show (every grid tile has its name underneath).
     */
    fun showGlowOnly(position: Int) {
        val color = adapter.ambientColorAt(position)
        stopAmbientAnimation()
        hero.animate().cancel()
        ambient.animate().cancel()
        hero.alpha = 0f
        heroArt.animate().cancel()
        heroArt.alpha = 0f
        heroShown = false
        if (!ambientGlow) return
        if (!animations || reducedMotion) {
            setAmbient(color)
            ambient.alpha = 1f
            return
        }
        if (color == ambientColor && ambient.background != null) {
            ambient.animate().alpha(1f).setDuration(300).start()
            return
        }
        // The same dip-and-swap as the dock's top shelf.
        ambient.animate().alpha(0.35f).setDuration(if (ambient.alpha > 0.35f) 200L else 0L).withEndAction {
            setAmbient(color)
            ambient.animate().alpha(1f).setDuration(500).start()
        }.start()
    }

    fun hideHero() {
        if (!heroShown) return
        stopAmbientAnimation()
        hero.animate().cancel()
        ambient.animate().cancel()
        heroArt.animate().cancel()
        heroShown = false
        if (animations && !reducedMotion) {
            hero.animate().alpha(0f).setDuration(160).start()
            if (ambientGlow) ambient.animate().alpha(0f).setDuration(300).start()
            heroArt.animate().alpha(0f).setDuration(300).start()
        } else {
            hero.alpha = 0f
            ambient.alpha = 0f
            heroArt.alpha = 0f
        }
    }
}
