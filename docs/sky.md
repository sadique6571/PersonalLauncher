# Sky backdrop

The released Home screen draws its wallpaper and ambient effects through [HomeWallpaperView](../launcher/app/src/main/kotlin/com/sadique/launcher/HomeWallpaperView.kt). The `sky_intensity`, `latitude`, and `longitude` values described here belong to the older `SkyView` backdrop. They do not change the current Home background or the weather location.

## Retained source behavior

In the older Home implementation, `sky_intensity` controls backdrop opacity from 0 to 100 and defaults to 0 in [config.xml](../launcher/app/src/main/res/values/config.xml). A `config.json` overlay can override that intensity. The sunrise and sunset calculation in [SkyView](../launcher/app/src/main/kotlin/com/sadique/launcher/SkyView.kt) reads latitude and longitude from build-time string resources. It uses the TV's date and time zone.

If the coordinates are absent or the calculation cannot supply suitable sunrise and sunset times, the palette uses its fixed-day schedule. The view reuses a 480 × 270 bitmap and redraws it at most once per minute during normal clock updates.
