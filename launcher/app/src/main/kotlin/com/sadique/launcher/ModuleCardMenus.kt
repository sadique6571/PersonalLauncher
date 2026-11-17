package com.sadique.launcher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayList
import java.util.Collections

internal class ModuleCardMenus(
    private val host: Activity,
    private val root: () -> FrameLayout,
    private val density: () -> Float,
    private val currentCards: () -> List<Cards.Card>,
    private val canPrompt: () -> Boolean,
    private val reloadTiles: () -> Unit,
    private val restoreFocus: () -> Unit,
    private val glassPopup: (View, Int) -> PopupWindow,
    private val dropIn: (PopupWindow, View, View, Int) -> Unit,
) {
    private var cardPrompt: Dialog? = null
    /** Set when a decision could not be saved: no more prompts until the next resume, instead of asking in a loop. */
    private var promptsPaused = false
    private val cards get() = currentCards()
    val promptShowing: Boolean get() = cardPrompt != null

    fun resumePrompts() { promptsPaused = false }
    fun dismissPrompt() { cardPrompt?.dismiss() }

    /**
     * Asks about the next card that has no decision for its current signing key. One prompt at a
     * time, only over a laid out grid with window focus (not under a popup or another app), and
     * the next one follows as soon as this one is answered. Nothing is shown until the owner says Show.
     */
    fun showPrompt() {
        if (!canPrompt() || promptsPaused || cardPrompt != null || !host.hasWindowFocus() || host.isFinishing) return
        if (cards.isEmpty() || !CardFeature.working()) return
        CardFeature.run<Unit?>({
            showCardPromptNow()
            null
        }, null)
    }

    private fun showCardPromptNow() {
        val card = Cards.firstPending(cards) ?: return

        val panel = host.layoutInflater.inflate(R.layout.dialog_card_prompt, root(), false)
        val rtl = host.resources.getBoolean(R.bool.rtl_menu_bar)
        if (rtl) panel.layoutDirection = View.LAYOUT_DIRECTION_RTL
        panel.findViewById<TextView>(R.id.title).text = host.getString(R.string.card_prompt_title, card.appLabel)
        panel.findViewById<TextView>(R.id.body).text = if (card.state == Cards.State.RESIGNED)
                host.getString(R.string.card_prompt_resigned, card.appLabel)
                else if (card.state == Cards.State.RELABELLED)
                host.getString(R.string.card_prompt_relabelled, card.appLabel)
                else host.getString(R.string.card_prompt_body, card.appLabel)
        val names = panel.findViewById<TextView>(R.id.names)
        if (Cards.fold(card.label) == Cards.fold(card.appLabel)) names.visibility = View.GONE
        else names.text = host.getString(R.string.card_prompt_names, card.label, card.appLabel)
        panel.findViewById<TextView>(R.id.identity).text =
                host.getString(R.string.card_prompt_identity, card.pkg, Cards.shortDigest(card.digest))
        val keep = panel.findViewById<TextView>(R.id.btnKeep)
        val show = panel.findViewById<TextView>(R.id.btnShow)
        if (rtl) {
            val buttons = keep.parent as LinearLayout
            buttons.removeAllViews()
            buttons.addView(show)
            buttons.addView(keep)
            keep.nextFocusLeftId = R.id.btnShow
            keep.nextFocusRightId = R.id.btnKeep
            show.nextFocusLeftId = R.id.btnShow
            show.nextFocusRightId = R.id.btnKeep
        }

        val dialog = Dialog(host)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(panel)
        dialog.setCanceledOnTouchOutside(false)
        val w = dialog.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout((440 * density()).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            w.setDimAmount(0.6f)
        }
        // One decision per prompt: the buttons and Back (cancel) all end here.
        var decided = false
        val choose = View.OnClickListener { v ->
            if (!decided) {
                decided = true
                cardPrompt = null
                dialog.dismiss()
                decideCard(card, v.id == R.id.btnShow)   // guards its own card calls; the tile reload stays outside
                restoreFocus()   // the tiles changed under the card approval dialog; this also chains the next prompt
            }
        }
        keep.setOnClickListener(choose)
        show.setOnClickListener(choose)
        dialog.setOnCancelListener { choose.onClick(keep) }
        dialog.setOnDismissListener {
            if (cardPrompt === dialog) cardPrompt = null
        }
        // Left and Right move between the two buttons; Up and Down stay put; the ends don't wrap.
        val keys = View.OnKeyListener { v, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                true
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                v.id == (if (rtl) R.id.btnShow else R.id.btnKeep)
            } else {
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                        && v.id == (if (rtl) R.id.btnKeep else R.id.btnShow)
            }
        }
        keep.setOnKeyListener(keys)
        show.setOnKeyListener(keys)
        cardPrompt = dialog
        dialog.show()
        keep.requestFocus()
    }

    /** Writes the owner's decision and rebuilds the tiles; focus is left where it is. */
    private fun decideCard(card: Cards.Card, show: Boolean) {
        val saved = CardFeature.run({ Cards.decide(Cards.storeFile(host), card, show) }, false)
        if (!saved) {
            Toast.makeText(host, R.string.card_not_saved, Toast.LENGTH_SHORT).show()
            promptsPaused = true
        }
        reloadTiles()
    }

    /** Dropdown under the Settings pill (hold OK): every discovered card, its package and key; OK toggles Shown and Hidden. */
    fun showList() {
        if (cards.isEmpty() || !CardFeature.working()) {
            Toast.makeText(host, R.string.no_module_cards, Toast.LENGTH_SHORT).show()
            return
        }
        CardFeature.run<Unit?>({
            showModuleCardsNow()
            null
        }, null)
    }

    private fun showModuleCardsNow() {
        val anchor = host.findViewById<View>(R.id.btnSettings)
        val panel = host.layoutInflater.inflate(R.layout.popup_cards, root(), false)
        if (host.resources.getBoolean(R.bool.rtl_menu_bar))
            panel.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val rows = panel.findViewById<LinearLayout>(R.id.rows)
        val popup = glassPopup(panel, (400 * density()).toInt())
        val listed = ArrayList(cards)
        Collections.sort(listed, Cards::byLabel)
        for (card in listed) {
            val row = host.layoutInflater.inflate(R.layout.item_card_row, rows, false)
            row.tag = card.pkg
            row.findViewById<TextView>(R.id.title).text = card.label
            if (host.resources.getBoolean(R.bool.rtl_menu_bar)) {
                val heading = row.findViewById<View>(R.id.title).parent as LinearLayout
                val status = row.findViewById<View>(R.id.state)
                heading.removeView(status)
                heading.addView(status, 0)
            }
            val app = row.findViewById<TextView>(R.id.app)
            if (Cards.fold(card.label) == Cards.fold(card.appLabel)) app.visibility = View.GONE
            else app.text = host.getString(R.string.card_app_name, card.appLabel)
            row.findViewById<TextView>(R.id.pkg).text = card.pkg
            row.findViewById<TextView>(R.id.digest).text = Cards.longDigest(card.digest)
            val pkg = card.pkg
            row.setOnClickListener {
                CardFeature.run<Unit?>({
                    // The list stays open; the decision is for the card as it is now, not as it was listed.
                    val now = cardFor(pkg)
                    if (now != null) {
                        decideCard(now, now.state != Cards.State.SHOWN)
                        // Every row, not only this one: a toggle can move another card across the limit of 8.
                        updateCardStates(rows)
                    }
                    null
                }, null)
            }
            rows.addView(row)
        }
        updateCardStates(rows)
        // Scroll inside the panel instead of running off the bottom of a 720p screen.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val at = IntArray(2)
        anchor.getLocationInWindow(at)
        val room = root().height - (at[1] + anchor.height) - (34 * density()).toInt()
        popup.height = Math.min(panel.measuredHeight, Math.max(room, (120 * density()).toInt()))
        // A mirrored header places Settings on the left: align the outer panel inward, like its parent menu.
        dropIn(popup, panel, anchor, if (host.resources.getBoolean(R.bool.rtl_menu_bar))
                Gravity.START else Gravity.END)
        rows.getChildAt(0).requestFocus()
    }

    private fun cardFor(pkg: String): Cards.Card? {
        for (c in cards) if (c.pkg == pkg) return c
        return null
    }

    /** Writes the state of every row in the Module cards list from the current card list. */
    private fun updateCardStates(rows: LinearLayout) {
        val drawn = Cards.shown(cards)
        for (i in 0 until rows.childCount) {
            val row = rows.getChildAt(i)
            val tag = row.tag
            val card = if (tag is String) cardFor(tag) else null
            row.findViewById<TextView>(R.id.state).text = if (card != null) stateText(card, drawn.contains(card)) else ""
        }
    }

    /** The row's state: a card the owner set to Shown but which is beyond the eight drawn says so. */
    private fun stateText(card: Cards.Card, drawn: Boolean): String {
        return when (card.state) {
            Cards.State.SHOWN -> host.getString(if (drawn) R.string.card_state_shown else R.string.card_state_over_limit)
            Cards.State.HIDDEN -> host.getString(R.string.card_state_hidden)
            Cards.State.RESIGNED -> host.getString(R.string.card_state_new_key)
            Cards.State.RELABELLED -> host.getString(R.string.card_state_new_label)
            else -> host.getString(R.string.card_state_new)
        }
    }
}
