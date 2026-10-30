# Personal Launcher

**Personal Launcher by Sadique Sadiq** — a clean, fast home-screen launcher for Android TV / Google TV.

- Package: `com.sadique.launcher`
- Version: 4.0
- Plain Java, AndroidX RecyclerView, `minSdk` 26, no `INTERNET` permission — light on RAM, fast on low-end TVs.
- Day/night wallpaper (mountain lake, aurora at night), glass dock, quick settings, profile.

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
