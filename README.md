# Personal Launcher

**Personal Launcher by Sadique Sadiq** — a clean, fast home-screen launcher for Android TV / Google TV.

- Package: `com.sadique.launcher`
- Version: 4.2
- Plain Java, AndroidX RecyclerView, `minSdk` 26. Internet permission is used for online screensaver photos, weather and city lookup.
- Day/night Home wallpaper, compact toolbar, configurable app grid, quick settings and local profile.

## Midnight Cyan home

- TV: profile at the top left; small network, Search, native Assistant/mic, Inputs, Play Store, Screensaver and Settings icons at the top right.
- A centered 12-hour clock with small AM/PM and date; the sun/moon glow stays behind the clock as the layout changes.
- Apps appear directly below the clock. The left heading shows the focused app's name in large text.
- There is one app drawer. Small 16:9 rounded cards use each app’s native TV banner where supplied, or its original icon without zooming or distortion. No labels underneath.
- Focus uses a matching icon-colour glow/border. The clock and focused-title area stay visible while apps scroll.
- Dark Midnight mode is the default, with the mountain ridge behind the clock. Dark/automatic/light modes and a custom wallpaper picker are available.
- No favorites, duplicate dock/quick-access rows or Gemini button.
- Phones keep a portrait/touch layout with adaptive columns. Profile photos are chosen locally through Android's picker.
- Wi-Fi is green only with Android-validated internet access; missing internet is red. Ethernet/mobile data use their own indicators.

Search filters installed, non-hidden apps **inside the launcher** as you type. The explicit Play Store
button opens Google's normal search/install screen for new apps. The mic button uses the TV's configured
Assistant; supported speech-to-text activities can also return a phrase into local app search. The launcher
does not record audio or request microphone permission. Availability depends on the installed system service.

Quick settings contains inline brightness and media-volume sliders. Optional Android write-settings access
enables standard device brightness where supported; otherwise the slider is clearly labelled Home brightness.
Some TVs keep hardware brightness/CEC volume fixed outside the public Android controls; fixed volume is
shown as unavailable rather than pretending to change it. System access is granted only through Android's
own permission screen. Media volume updates when the remote changes it while the panel is open.

Launcher settings offers hide/unhide apps, columns, tile/focused-title sizes, clock size, dark mode,
wallpaper picker/darkness, colour glow, profile, default Home and layout reset. Hiding never disables
or uninstalls an app. All launcher dialogs share the same rounded, translucent dark-glass styling;
there is no expensive live blur.

## Continue watching

Resume entries appear in a separate row **after the app grid**, never among default app icons.
Thumbnails/posters shared by the publishing apps load over HTTPS or Android content providers, with an app-icon fallback when artwork is unavailable.
The row uses Android TV's shared Watch Next provider and asks for TV-listings access when available.
It filters finished items and hidden apps, and opens the publishing app's resume link. Some TVs restrict
provider access and some streaming apps never publish entries; private Netflix/other app histories cannot
be read. Empty or unsupported devices show a clear message instead of invented resume items.

## Online landscape screensaver

Open the top Screensaver icon for a preview. It streams a rotating selection of landscape photos from
Unsplash (30-second default slideshow, alternate-source retries), with a large center clock/date, network status at the top right, and weather at the bottom left.
Photos stay in memory only; with no validated internet connection the screen shows the clock/date and
an offline message instead of a saved landscape. Image requests are bounded, time out, and stop being
scheduled when the screensaver closes. TLS verification remains enabled.

Weather defaults to **Okara, Pakistan** and is provided by Open-Meteo. Change the city, Celsius/Fahrenheit,
photo interval and optional Home-idle timeout in **Settings → Launcher settings → Screensaver & weather**.
City search lets you choose a matching country/region; it does not request GPS or a Google login.

For Android's system-managed screensaver, open **Android screensaver settings** from that menu and
select **Online Landscapes · Personal Launcher**. Any remote key exits the system dream. The launcher's
own idle timeout defaults to Off and runs only while Home is in front, so it does not cover a playing app.

Online sources: `images.unsplash.com`, `api.open-meteo.com`, `geocoding-api.open-meteo.com`.

The Haier Home-button and reboot flow still needs testing on the real TV. Keep Google Home enabled while testing.

## Build

A push to `main` touching `launcher/**` builds the release APK on GitHub Actions
(JDK 17, Gradle 8.9, Android SDK 34) and attaches `PersonalLauncher.apk` to a
release. The APK is debug-signed for sideloading over ADB.
Builds and launcher checks for this update run on GitHub Actions.

## Install on the TV

```bash
adb install PersonalLauncher.apk
adb shell cmd package set-home-activity --user 0 com.sadique.launcher/.MainActivity
```

Press Home, pick **Personal Launcher** as the home app. Confirm it runs well
before disabling the stock launcher.

## License

MIT License — see [LICENSE](LICENSE).
