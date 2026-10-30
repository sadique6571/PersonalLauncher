# Personal Launcher

**Personal Launcher by Sadique Sadiq** — a clean, fast home-screen launcher for Android TV / Google TV.

- Package: `com.sadique.launcher`
- Version: 4.0
- Plain Java, AndroidX RecyclerView, `minSdk` 26, no `INTERNET` permission — light on RAM, fast on low-end TVs.
- Day/night wallpaper (mountain lake, aurora at night), glass dock, quick settings, profile.

## Midnight Cyan home

- TV: profile at the top left; internet status, Search apps, Gemini and Settings at the top right.
- A centered 12-hour clock with small AM/PM and date; the sun/moon glow stays behind the clock as the layout changes.
- A cyan-focus app dock prioritises installed YouTube, Netflix, Prime Video and VLC, with an **All apps** shortcut.
- Quick access sits below the dock. The full app grid remains available further down the screen.
- Phones keep a portrait/touch layout with adaptive columns. Profile photos are chosen locally through Android's picker.
- Wi-Fi is green only with Android-validated internet access; missing internet is red. Ethernet/mobile data use their own indicators.

Search and Gemini open their installed apps or Play Store pages. Availability depends on the device.
Quick settings includes launcher options, network, Bluetooth, display settings and TV inputs. Its brightness
slider changes the launcher window; the display shortcut opens system brightness settings.

The Haier Home-button and reboot flow still needs testing on the real TV. Keep Google Home enabled while testing.

## Build

A push to `main` touching `launcher/**` builds the release APK on GitHub Actions
(JDK 17, Gradle 8.9, Android SDK 34) and attaches `PersonalLauncher.apk` to a
release. The APK is debug-signed for sideloading over ADB.

## Install on the TV

```bash
adb install PersonalLauncher.apk
adb shell cmd package set-home-activity --user 0 com.sadique.launcher/.MainActivity
```

Press Home, pick **Personal Launcher** as the home app. Confirm it runs well
before disabling the stock launcher.

## License

MIT License — see [LICENSE](LICENSE).
