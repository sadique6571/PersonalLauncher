# Personal Launcher for Android TV

Personal Launcher gives an Android TV or Google TV a remote-friendly Home screen with app shortcuts, adjustable themes and a control center. The launcher package is `com.sadique.launcher` and requires Android 8.0 (API 26) or later.

[Download the latest signed APK](https://github.com/sadique6571/PersonalLauncher/releases/latest/download/PersonalLauncher.apk) · [Release history](https://github.com/sadique6571/PersonalLauncher/releases)

## Start here

1. Install the signed APK on the TV and open **Personal Launcher**.
2. To use it as Home, follow the launcher's setup screen. The options available depend on the TV firmware.
3. Open **Launcher settings** to choose a theme, change the clock and weather, or set up the landscape screensaver.

Keep the existing app installed when updating so Android can retain your preferences. On the launcher Home screen, use **Launcher settings → System → Updates → Check now** to check GitHub immediately. Automatic checks and installation depend on the updater settings and TV setup. A verified APK may still need confirmation in Android's installer.

## Features and setup

| Feature | Guide |
| --- | --- |
| Home layout, dock and configuration | [Launcher guide](docs/launcher.md) |
| App drawer and Home shortcuts | [App library](docs/app-library.md) |
| Resume cards from supported TV apps | [Watch Next](docs/watch-next.md) |
| Remote buttons and optional volume helper | [Remote controls](docs/REMOTE.md) · [Volume helper](docs/volume-hold-bridge.md) |
| Device-specific quirks and recovery | [Troubleshooting](docs/TROUBLESHOOTING.md) · [Device notes](docs/devices/) |

Online wallpaper, landscape photos and weather use an internet connection. The screensaver also has bundled photos for offline playback.

## Build from source

Use JDK 17, Android SDK Platform 34 and Build Tools 34.0.0. Run the Gradle wrapper from the Android project directory.

```sh
cd launcher
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

Use the signed GitHub APK to update an existing release installation. A local build ordinarily uses a different signing key.

Report feature requests and ordinary bugs through [GitHub Issues](https://github.com/sadique6571/PersonalLauncher/issues). For suspected vulnerabilities, follow the [private reporting instructions](SECURITY.md).
