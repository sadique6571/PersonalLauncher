package com.sadique.launcher

import android.content.SharedPreferences
import android.graphics.Color

internal class HomeClockContrast {
    private var contrastKey: ClockSampleKey? = null
    var ink = Color.WHITE
        private set

    /** Nine cached photo samples choose clock ink; the photo itself is never tinted for text contrast. */
    fun update(skyView: HomeWallpaperView?, prefs: SharedPreferences, tv: Boolean, defaultY: Float): Boolean {
        if (skyView == null) { contrastKey = null; return false }
        val photo = skyView.currentPicture()
        if (photo == null || photo.isRecycled) {
            contrastKey = null
            val changed = ink != 0xffffffff.toInt()
            ink = 0xffffffff.toInt()
            return changed
        }
        if (skyView.width <= 0 || skyView.height <= 0) return false
        val y = if (skyView.clockY >= 0) skyView.clockY else defaultY
        val x = if (skyView.clockX >= 0) skyView.clockX else skyView.width / 2f
        val frame = skyView.wallpaperFrame(photo, y, photo === skyView.backdrop)
        if (tv && (x < frame[0] || x >= frame[2] || y < frame[1] || y >= frame[3])) {
            contrastKey = null
            val changed = ink != Color.WHITE
            ink = Color.WHITE
            return changed
        }
        val px = Math.max(0, Math.min(photo.width - 1, Math.round((x - frame[0]) / (frame[2] - frame[0]) * photo.width)))
        val py = Math.max(0, Math.min(photo.height - 1, Math.round((y - frame[1]) / (frame[3] - frame[1]) * photo.height)))
        val shade = HomeAppearance.shade(prefs.getString("theme_mode", "light"), skyView.minute, prefs.getInt("background_dim", 0))
        val darkInk = 0xff11191c.toInt()
        val key = contrastKey
        if (key != null && key.matches(photo, px, py, shade, darkInk)) return false
        contrastKey = ClockSampleKey(photo, px, py, shade, darkInk)
        var red = 0
        var green = 0
        var blue = 0
        val dx = Math.max(1, photo.width / 30)
        val dy = Math.max(1, photo.height / 30)
        for (row in -1..1) for (col in -1..1) {
            val pixel = photo.getPixel(Math.max(0, Math.min(photo.width - 1, px + col * dx)), Math.max(0, Math.min(photo.height - 1, py + row * dy)))
            red += (pixel shr 16) and 255
            green += (pixel shr 8) and 255
            blue += pixel and 255
        }
        val background = MaterialAppearance.mix(Color.rgb(red / 9, green / 9, blue / 9), 0xff051327.toInt(), shade / 255f)
        // A white cloud or bright patch must not turn an otherwise colourful photo's clock black.
        var whiteWallpaper = true
        for (row in 0 until 3) {
            if (!whiteWallpaper) break
            for (col in 0 until 3) {
                val pixel = photo.getPixel(Math.min(photo.width - 1, Math.round(photo.width * (0.15f + 0.35f * col))),
                        Math.min(photo.height - 1, Math.round(photo.height * (0.15f + 0.35f * row))))
                if (ThemeStyle.wallpaperClockInk(pixel, darkInk) == 0xffffffff.toInt()) { whiteWallpaper = false; break }
            }
        }
        val ink = if (whiteWallpaper) ThemeStyle.wallpaperClockInk(background, darkInk) else 0xffffffff.toInt()
        val changed = ink != ink
        ink = ink
        return changed
    }
}
