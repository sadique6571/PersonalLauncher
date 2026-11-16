package com.sadique.launcher

import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView

/**
 * The glass highlight that springs between tiles: its springs, its scroll bookkeeping, fitting it to the
 * focused tile, and hiding it when focus leaves the grid. Everything it needs from Home's config is fixed
 * when Home is built, so it takes plain values; only the adapter arrives as a callback.
 */
internal class MainGlassHighlight(
    private val glass: GlassView,
    private val root: FrameLayout,
    private val grid: RecyclerView,
    private val shelf: LauncherShelf,
    private val currentAdapter: () -> AppAdapter,
    private val density: Float,
    private val animations: Boolean,
    private val reducedMotion: Boolean,
    private val advancedEffects: Boolean,
    private val cinematic: Boolean,
    private val appGlow: Boolean,
    private val cinematicWhiteFocus: Boolean,
    private val festive: Boolean,
    private val singleRow: Boolean,
    private val dockRow: Boolean,
    private val focusOvershoot: Boolean,
    private val isDestroyed: () -> Boolean,
    /** Back to the Activity's onTileFocused, which records the focused tile before delegating here. */
    private val retarget: (Int, View) -> Unit,
) {
    private lateinit var springX: SpringAnimation
    private lateinit var springY: SpringAnimation
    private var glassTargetX = 0f
    private var glassTargetY = 0f
    // How far the grid has scrolled the glass. springY works without it, in grid content space, so a
    // scroll moves the glass in lockstep with its tile instead of becoming a moving spring target.
    private var glassScrollShift = 0f
    /** The same for springX, when single_row scrolls the grid sideways. */
    private var glassScrollShiftX = 0f
    private var glassCardW = 0   // width of the tile the glass was last fitted to
    private var glassShown = false
    private val tileRect = Rect()
    private val revealRect = Rect()
    private var glassAccent = 0

    init {
        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!glassShown) return
                glassTargetY -= dy.toFloat()
                glassScrollShift -= dy.toFloat()
                glass.translationY = glass.translationY - dy.toFloat()
                // single_row scrolls sideways: the same bookkeeping on the other axis.
                if (dx != 0) {
                    glassTargetX -= dx.toFloat()
                    glassScrollShiftX -= dx.toFloat()
                    glass.translationX = glass.translationX - dx.toFloat()
                }
            }
        })

        // Liquid feel: soft springs on the highlight's position.
        springX = SpringAnimation(glass, object : FloatPropertyCompat<View>("glassContentX") {
            override fun getValue(v: View): Float {
                return v.translationX - glassScrollShiftX
            }

            override fun setValue(v: View, value: Float) {
                v.translationX = value + glassScrollShiftX
            }
        })
        springY = SpringAnimation(glass, object : FloatPropertyCompat<View>("glassContentY") {
            override fun getValue(v: View): Float {
                return v.translationY - glassScrollShift
            }

            override fun setValue(v: View, value: Float) {
                v.translationY = value + glassScrollShift
            }
        })
        for (s in arrayOf(springX, springY)) {
            if (reducedMotion) {
                Motion.reduce(s)
            } else if (!focusOvershoot) {
                // Critically damped: glides straight onto the tile without swinging past it.
                s.setSpring(SpringForce().setStiffness(if (advancedEffects) 520f else 440f).setDampingRatio(1f))
            } else {
                s.setSpring(SpringForce().setStiffness(if (advancedEffects) 320f else 260f)
                        .setDampingRatio(if (advancedEffects) 0.76f else 0.58f))
            }
        }
    }

    fun setAccent(color: Int) {
        glass.setAccent(color)
    }

    /** The highlight for a reload: hidden until focus lands again. */
    fun resetForReload() {
        glassShown = false
        glass.alpha = 0f
    }

    fun onTileFocused(position: Int, card: View) {
        val adapter = currentAdapter()
        // Focus restored before the first layout (cold start): the tile has no size yet, and the glass
        // would be sized to 2*pad. Run again once the tile is laid out.
        if (card.width == 0) {
            card.post {
                if (card.isFocused && card.width > 0) retarget(position, card)
            }
            return
        }
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        val r = tileRect
        r.set(0, 0, card.width, card.height)
        root.offsetDescendantRectToMyCoords(card, r)
        val pad = 6f * density        // matches GlassView's glow inset
        val scale = adapter.focusScale()
        val w = card.width * scale + 2 * pad
        val h = card.height * scale + 2 * pad
        val x = r.exactCenterX() - w / 2f
        val y = r.exactCenterY() - h / 2f - (if (advancedEffects) 4f * density else 0f)

        val lp = glass.layoutParams
        if (lp.width != w.toInt() || lp.height != h.toInt() || card.width != glassCardW) {
            lp.width = w.toInt()
            lp.height = h.toInt()
            glass.layoutParams = lp
            // GlassView draws its slab inset by pad, i.e. exactly over the zoomed tile: same corners as the tile.
            glass.setRadius(AppAdapter.cornerRadius(card.width, density) * scale)
        }
        glassCardW = card.width
        glassTargetX = x
        glassTargetY = y
        // Cinematic shelf: the highlight glows in the dock app's colour, plain white in the grid (and on the dock with
        // cinematic_white_focus). app_glow: every tile. In a single row every tile is the dock's kind.
        val coloured = (appGlow || cinematic && !cinematicWhiteFocus && (singleRow || position < MainActivity.COLUMNS)) && !festive
        val accent = if (coloured) adapter.ambientColorAt(position) else 0
        if ((cinematic || appGlow) && !festive && accent != glassAccent) {
            glassAccent = accent
            glass.setAccent(accent)
        }
        if (!glassShown) {
            springX.cancel()
            springY.cancel()
            glass.translationX = x
            glass.translationY = y
            if (animations && !reducedMotion) glass.animate().alpha(1f).setDuration(180).start()
            else glass.alpha = 1f
            glassShown = true
        } else if (!animations) {
            springX.cancel()
            springY.cancel()
            glass.translationX = x
            glass.translationY = y
        } else {
            springX.animateToFinalPosition(x - glassScrollShiftX)
            springY.animateToFinalPosition(y - glassScrollShift)
        }
        val item = card.parent as View
        if (dockRow && (singleRow || position < MainActivity.COLUMNS)) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app. In a single row
            // every tile is in the dock's row, which never scrolls vertically.
            val decoratedTop = item.top - (6 * density).toInt()
            val dy = decoratedTop - grid.paddingTop
            // A 1px mismatch used to start a scroll on every dock move. Only scroll when the row is
            // actually off; reduced motion does it in one jump so it does not fight the highlight.
            val scroll = !singleRow && Math.abs(dy) > (8 * density).toInt()
            if (scroll) {
                if (!animations || reducedMotion) grid.scrollBy(0, dy)
                else grid.smoothScrollBy(0, dy)
            }
            shelf.showHero(position, if (scroll) (r.top - dy).toFloat() else r.top.toFloat())
        } else {
            // Below the dock (or in a plain grid) the glow follows the tile without the big name; letting it
            // fade out left the grid on plain black.
            shelf.showGlowOnly(position)
            // Scroll just enough so the whole glass (not only the tile) is visible.
            revealRect.set(0, -pad.toInt() * 2, item.width, item.height + pad.toInt())
            grid.requestChildRectangleOnScreen(item, revealRect, reducedMotion)
        }
    }

    /** Refits the glass to the focused holder once the grid layout and tile dimensions settle. */
    fun refit(lastFocused: Int, libraryFocusPending: Boolean) {
        val adapter = currentAdapter()
        if (isDestroyed() || adapter.appCount() == 0 || libraryFocusPending) return
        val pos = Math.max(0, Math.min(lastFocused, adapter.appCount() - 1))
        val vh = grid.findViewHolderForAdapterPosition(pos)
        val card: View? = vh?.itemView?.findViewById(R.id.card)
        if (card == null || card.width <= 0 || !card.isFocused) return
        val pad = 6f * density
        val scale = adapter.focusScale()
        val w = (card.width * scale + 2 * pad).toInt()
        val h = (card.height * scale + 2 * pad).toInt()
        if (!glassShown || card.width != glassCardW || glass.width != w || glass.height != h) {
            retarget(pos, card)
        }
    }

    fun hide() {
        if (!glassShown) return
        glassShown = false
        glass.animate().cancel()
        if (animations && !reducedMotion) glass.animate().alpha(0f).setDuration(160).start()
        else glass.alpha = 0f
    }
}
