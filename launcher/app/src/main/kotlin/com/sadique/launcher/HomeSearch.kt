package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

internal class HomeSearch(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val tv: Boolean,
    private val muted: Int,
    private val apps: () -> List<PersonalApp>?,
    private val dp: (Int) -> Int,
    private val text: (String, Int, Int) -> TextView,
    private val applyAppFont: (TextView) -> Unit,
    private val toolbarSurface: (Boolean) -> GradientDrawable,
    private val tryLaunch: (Intent) -> Boolean,
    private val launch: (Intent) -> Unit,
) {
    private var searchDialog: AlertDialog? = null
    private var searchInput: EditText? = null
    private var searchRefresh: Runnable? = null
    val isShowing: Boolean get() = searchDialog?.isShowing == true

    fun dismiss() { searchDialog?.dismiss() }
    fun refresh() { searchRefresh?.run() }

    fun onSpeechResult(result: Int, data: Intent?) {
        if (result == Activity.RESULT_OK && data != null) {
            val words = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (words != null && words.isNotEmpty()) {
                val query = words[0]
                // Google Home behavior: if an installed app matches, show it;
                // otherwise fall back to YouTube search.
                if (appMatches(query)) {
                    if (searchDialog == null) show()
                    val input = searchInput
                    if (input != null) { input.setText(query); input.setSelection(input.length()) }
                } else {
                    youtubeSearch(query)
                }
            }
        }
    }

    fun show() {
        val existing = searchDialog
        if (existing != null && existing.isShowing) return
        val form = LinearLayout(host)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(16), dp(8), dp(16), dp(8))
        val input = EditText(host)
        input.isSingleLine = true
        input.hint = "Search installed apps"
        searchInput = input
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.setTextColor(ThemeStyle.foreground(prefs))
        input.setHintTextColor(ThemeStyle.muted(prefs))
        applyAppFont(input)
        input.setPadding(dp(16), dp(10), dp(16), dp(10))
        input.background = toolbarSurface(false)
        form.addView(input)
        val results = LinearLayout(host)
        results.orientation = LinearLayout.VERTICAL
        val matches = ScrollView(host)
        matches.addView(results)
        form.addView(matches, LinearLayout.LayoutParams(-1, dp(if (tv) 250 else 300)))
        val dialog = ThemeDialogBuilder(host).setTitle("Search apps").setView(form)
                .setPositiveButton("Find on Play Store") { _, _ -> openStore(input.text.toString().trim()) }
                .setNeutralButton("Voice search") { _, _ -> recognizeIntoSearch() }
                .setNegativeButton("Close", null).create()
        searchDialog = dialog
        searchRefresh = Runnable {
            results.removeAllViews()
            val query = input.text.toString().trim().lowercase(Locale.ROOT)
            val hidden = prefs.getStringSet("hidden_apps", emptySet())
            var count = 0
            val apps = apps()
            if (apps != null) for (app in apps) {
                if (hidden?.contains(app.info.activityInfo.packageName) == true || !(app.name + " " + app.info.activityInfo.packageName).lowercase(Locale.ROOT).contains(query)) continue
                val result = GlassSettings.row(host, app.name, null, R.drawable.ic_apps, GlassSettings.themeAccent(host)) {
                    searchDialog?.dismiss()
                    launch(Intent(Intent.ACTION_MAIN).setClassName(app.info.activityInfo.packageName, app.info.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                val position = LinearLayout.LayoutParams(-1, -2)
                position.setMargins(0, dp(4), 0, dp(4))
                val appIcon = app.icon
                if (result is LinearLayout && appIcon != null) {
                    val resultIcon = result.getChildAt(0) as ImageView
                    val state = appIcon.constantState
                    resultIcon.setImageDrawable(if (state == null) appIcon else state.newDrawable(host.resources))
                    resultIcon.clearColorFilter()
                    resultIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                }
                results.addView(result, position)
                if (++count == 16) break
            }
            if (count == 0) results.addView(text(if (apps() == null) "Loading apps…" else "No installed matches. Find new apps on Play Store.", 15, muted))
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) { searchRefresh?.run() }
            override fun afterTextChanged(value: Editable?) { }
        })
        dialog.setOnDismissListener { searchRefresh = null; searchDialog = null; searchInput = null }
        dialog.show()
        GlassSettings.styleDialog(host, dialog)
        searchRefresh?.run()
    }

    private fun launchAssistant(): Boolean {
        return tryLaunch(Intent(Intent.ACTION_ASSIST)) || tryLaunch(Intent(Intent.ACTION_VOICE_COMMAND))
                || tryLaunch(Intent(RecognizerIntent.ACTION_WEB_SEARCH))
    }

    private fun recognizeIntoSearch() {
        try {
            host.startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search launcher apps"), 9)
        } catch (unavailable: ActivityNotFoundException) {
            if (!launchAssistant()) Toast.makeText(host, "Enable your TV's Assistant or speech service in system settings", Toast.LENGTH_LONG).show()
        } catch (unavailable: SecurityException) {
            if (!launchAssistant()) Toast.makeText(host, "Enable your TV's Assistant or speech service in system settings", Toast.LENGTH_LONG).show()
        }
    }

    /** YouTube search: tries the YouTube TV app first, falls back to browser. */
    private fun youtubeSearch(query: String) {
        val encoded = Uri.encode(query)
        // Try YouTube TV app.
        val appSearch = Intent(Intent.ACTION_SEARCH)
                .setPackage("com.google.android.youtube.tv")
                .putExtra("query", query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryLaunch(appSearch)) return
        // Try regular YouTube app.
        val mobileSearch = Intent(Intent.ACTION_SEARCH)
                .setPackage("com.google.android.youtube")
                .putExtra("query", query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryLaunch(mobileSearch)) return
        // Fall back to browser.
        launch(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + encoded))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Returns true if any installed (non-hidden) app matches the query. */
    private fun appMatches(query: String): Boolean {
        val apps = apps() ?: return true // Apps not loaded yet; show search.
        val q = query.trim().lowercase(Locale.ROOT)
        val hidden = prefs.getStringSet("hidden_apps", emptySet())
        for (app in apps) {
            if (hidden?.contains(app.info.activityInfo.packageName) == true) continue
            if ((app.name + " " + app.info.activityInfo.packageName).lowercase(Locale.ROOT).contains(q)) return true
        }
        return false
    }

    fun openStore(query: String) {
        if (query.isEmpty()) {
            val store = host.packageManager.getLaunchIntentForPackage("com.android.vending")
            if (store != null && tryLaunch(store)) return
        }
        val suffix = if (query.isEmpty()) "" else "search?q=" + Uri.encode(query) + "&c=apps"
        val market = Intent(Intent.ACTION_VIEW, Uri.parse(if (query.isEmpty()) "market://apps" else "market://" + suffix))
                .setPackage("com.android.vending")
        if (!tryLaunch(market)) launch(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/" + if (suffix.isEmpty()) "apps" else suffix)))
    }
}
