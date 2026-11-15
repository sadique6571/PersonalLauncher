package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import java.text.SimpleDateFormat
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

internal class HomeClockDisplay(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val dp: (Int) -> Int,
) {
    fun greetingText(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if ((ThemeStyle.id(prefs) == ThemeStyle.GLASS || ThemeStyle.harmony(prefs))) return if (hour >= 5 && hour < 12) "Good morning" else if (hour < 17 && hour >= 12) "Good afternoon" else if (hour >= 17 && hour < 22) "Good evening" else "Good night"
        if (hour >= 5 && hour < 12) return "Subah Bakhair"
        if (hour >= 12 && hour < 17) return "Dopahar Bakhair"
        if (hour >= 17 && hour < 22) return "Shaam Bakhair"
        return "Shab Bakhair"
    }

    fun update(
        fitClockText: (CharSequence) -> Unit, ampm: TextView?, date: TextView?, harmonyDateValue: TextView?,
        harmonyGreetingValue: TextView?, harmonyQuoteValue: TextView?, islamicDate: TextView?,
        sky: HomeWallpaperView?, greeting: TextView?,
    ) {
        val now = Calendar.getInstance()
        val seconds = prefs.getBoolean("clock_seconds", false)
        val is24 = prefs.getBoolean("time_24h", false)
        var value: String
        val ampmText: String
        if (is24) {
            value = String.format(Locale.getDefault(), "%02d:%02d",
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            if (seconds) value += String.format(Locale.getDefault(), ":%02d", now.get(Calendar.SECOND))
            ampmText = ""
        } else {
            val h12 = if (now.get(Calendar.HOUR) == 0) 12 else now.get(Calendar.HOUR)
            value = String.format(Locale.getDefault(), "%02d:%02d", h12, now.get(Calendar.MINUTE))
            if (seconds) value += String.format(Locale.getDefault(), ":%02d", now.get(Calendar.SECOND))
            ampmText = if (now.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        }
        fitClockText(value)
        val ampmView = ampm
        if (ampmView != null) {
            ampmView.text = ampmText
            val analog = prefs.getInt("clock_style", 0) == 1
            ampmView.visibility = if (analog) View.INVISIBLE else if (ampmText.isEmpty()) View.GONE else View.VISIBLE
        }
        date?.text = SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH).format(now.time)
        harmonyDateValue?.text = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(now.time)
        harmonyGreetingValue?.text = greetingText()
        val quoteView = harmonyQuoteValue
        if (quoteView != null && prefs.getBoolean("harmony_daily_quote", true)) {
            val today = HarmonyDailyWords.forCalendar(now)
            if (!today.contentEquals(quoteView.text)) quoteView.text = today
        }
        val islamicView = islamicDate
        if (islamicView != null) try {
            val today = LocalDate.of(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
            islamicView.text = HijriCalendar.format(today, prefs.getInt("hijri_adjustment", 0))
        } catch (unavailable: DateTimeException) {
            islamicView.text = "Islamic date unavailable"
        }
        val skyView = sky
        if (skyView != null) {
            skyView.minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            skyView.clockStyle = 0 // The independent SweepAnalogClock draws analog mode in every theme.
            skyView.invalidate()
        }
        greeting?.text = greetingText()
    }

    /**
     * Sizes the clock text so the whole string always fits. AM/PM lives in its
     * own view now, so there is no span left to mismeasure. Measured precisely
     * with a StaticLayout, then scaled down only if needed; never below 26sp.
     */
    fun fit(
        timeView: TextView, text: CharSequence, pageWidth: Int, clockTextSize: Int,
        capToParent: Boolean, clockDatesInline: Boolean,
    ) {
        val density = host.resources.displayMetrics.density
        val screenW = host.resources.displayMetrics.widthPixels
        var maxWidth = (if (pageWidth > 0) pageWidth else screenW) - dp(24)
        if (maxWidth <= 0) maxWidth = screenW - dp(24)
        val parent = timeView.parent
        if (capToParent && parent is View && parent.width > 0)
            maxWidth = Math.max(dp(70), parent.width - dp(if (clockDatesInline) 260 else 80))
        val paint = TextPaint()
        paint.typeface = timeView.typeface
        var sizePx = clockTextSize * density
        paint.textSize = sizePx
        val layout = StaticLayout.Builder.obtain(
                text, 0, text.length, paint, Int.MAX_VALUE)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
        var w = 0f
        for (i in 0 until layout.lineCount) w = Math.max(w, layout.getLineWidth(i))
        if (w > maxWidth && w > 0) sizePx = Math.max(sizePx * maxWidth / w, 26 * density)
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
        timeView.text = text
    }
}
