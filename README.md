# Personal Launcher

**Personal Launcher by Sadique Sadiq** — a customizable Home launcher for Android TV / Google TV.

- **Version:** 7.0 · **Android version code:** 52 · Release build numbers increase independently.
- **Package:** `com.sadique.launcher` · **Minimum Android:** 8.0 / API 26
- Plain Java and Android Views, with performance profiles for low-memory TVs.
- Internet access is used for daily Bing wallpaper, Picsum screensaver photos, weather and city lookup.

## Download

[**Download PersonalLauncher.apk — latest signed release**](https://github.com/sadique6571/PersonalLauncher/releases/latest/download/PersonalLauncher.apk)

[Release page](https://github.com/sadique6571/PersonalLauncher/releases/latest)

Release tags retain the older `v4.7-build-N` naming format; the APK's displayed version remains **7.0**.

## Automatic GitHub updates

After this updater-enabled APK is installed once, Home checks this repository's latest stable GitHub release.
Checks are limited to once every six hours while Home is open, with a delayed retry after network errors.
APK downloads are bounded and checked against GitHub's SHA-256 digest, package identity, signing certificate
and a strictly newer Android version code. Drafts, prereleases, foreign packages/signers and downgrades are rejected.

When Home is idle and media audio is inactive, saved local ADB pairing can install the verified APK and reopen
Home. Startup recovery is allowed to finish first. No background polling service is added. If pairing is unavailable,
the Android installer is the fallback; one-time unknown-source approval and installation confirmation may be required.

Open **Launcher settings → System → Updates** to toggle automatic updates, check now, inspect status or install
a verified download. Installation attempts are bounded, and an update can remain ready until Home is idle.
Manual **Check now** shows one progress capsule above Home's toolbar. Repeated checks join the current
download. The percentage stops at 99 while SHA-256, package and signing checks run; after verification,
100% removes the capsule and opens Android's APK installer. If Android requests unknown-source approval,
granting it and returning to Home continues that explicit install request. Leaving Home does not launch
an installer over another app; the verified APK remains ready in Updates. Automatic checks remain quiet.
Disabling auto-update prevents automatic installation. The old APK needs one manual update to receive this feature.

## Home screen

The default **TV Glass** style is inspired by Apple TV's visual layout:

- A top shelf shows the selected installed app's artwork and name beside the existing bold clock.
  This is app artwork, not a streaming recommendation feed.
- The app icon and Android TV banner use the selected silver **PL Monogram**. Home toolbar actions are larger, standalone white symbols; remote focus uses colour and scale without a surrounding box.
- Glass surfaces use translucent neutral tint, a fine reflected rim and rounded corners. Volume uses one bottom-filled white capsule with the output icon inside; Picture/Sound preview sliders use matching broad horizontal capsules.
- Apple-style controls share one rounded line-symbol family, including network, Bluetooth and audio output icons. Switching to Classic refreshes icons on open and saved settings pages. Classic panels have consistently rounded outer corners and an inset from the screen edge.
- Floating 16:9 app cards use original app icons, rounded corners, a white focus outline and subtle focus zoom. Header, drawer, search results, profile editor and volume capsule share the selected glass appearance.
  Light performance mode applies focus changes without the zoom animation.
- The profile photo and name sit at the top left. Weather, network status, Search, the notification bell and
  Settings and a direct Screensaver shortcut are in the top toolbar.
- Daily Bing wallpaper stays behind the Home interface. Wallpaper and profile snapshots avoid reloading
  the default background on each Home return.
- The digital clock supports a separate AM/PM label, date, seconds, font, size, glow and shadow options.
  An analog clock and hide options are also available. TV Glass puts the clock beside the top shelf;
  Classic retains the previous Home arrangement and clock alignment options.
- App columns, tile size, spacing, corners, logo size and focus-border thickness remain configurable.
- Long-press an app to move it, hide it, assign a category or open Android app information.
  Saved app order survives updates; new apps append to the grid. Hiding does not disable or uninstall apps.
- No duplicate app dock, Favorites row or Gemini button is added. Phones retain the portrait/touch layout.

To restore the previous Home arrangement, open
**Launcher settings → Theme → Visual style → Classic**.

## Glass appearance

Open **Launcher settings → Theme**:

| Option | Choices | Effect |
| --- | --- | --- |
| Visual style | TV Glass / Classic | Switch the Home arrangement and shared panel style |
| Glass finish | Soft / Clear / Frosted | Change TV Glass surface appearance |
| Glass intensity | Airy / Normal / Solid | Adjust surface opacity |
| Glass edge | Off / Subtle / Bright | Adjust the unfocused glass outline; focused controls remain visible |
| Theme | Midnight Cyan / Sunset Orange / Deep Purple / Custom | Choose accent colours for controls and icons, including Black, Graphite, Silver, Blue, Teal, Gold, Rose and Indigo |
| Launcher font | Default / Serif / Mono / Light | Choose the interface font |

For a more transparent appearance, try **TV Glass + Frosted + Airy + Subtle**.

Frosted panel blur is optional. It needs Android 12 / API 31 or newer, runtime cross-window blur support,
and a performance profile above Light. If blur is unsupported or disabled, panels use a more opaque
surface and stronger dimming. Availability changes refresh visible panels. Selecting Frosted does not
guarantee real blur on every TV. Soft and Clear use lightweight gradients without backdrop blur.

## Control Center and settings

The Settings button opens **Control Center** in TV Glass, with Network, Bluetooth, Inputs, Display and
Sound cards, with Screensaver beside Sound for direct access. The toolbar also has a direct Screensaver button.
The trailing Categories card is removed. Its header gear opens the original TV settings. Bluetooth opens the firmware's Bluetooth /
remote-accessory settings when available.

Settings pages use a shared panel with Back navigation, preserving the previous row focus and scroll
position. Local appearance choices update the panel while it stays open.

Launcher settings are grouped into:

- **Tiles** — columns, sizes, spacing, corners, logo size, focused-app title size and focus effects.
- **Clock** — digital/analog style, font, size, time format, alignment, seconds, glow and shadow.
- **Theme** — visual style, accents, interface font and glass appearance.
- **Wallpaper** — source, custom image, slideshow interval, darkness and bottom shade.
- **Apps** — visibility and available content-row preferences.
- **System** — screensaver/weather, profile, performance, Updates, settings export/import and layout reset.
  Routine volume/Home automation shortcuts are hidden; first-run authorization and recovery remain available.

Search filters installed, non-hidden apps inside the launcher. Play Store search opens Google's normal
search/install screen. Voice search uses the TV's configured Assistant or speech activity; the launcher
does not request microphone permission or record audio.

### Picture and Sound sliders: preview only

Display and Sound open custom slider pages:

| Picture preview | Sound preview |
| --- | --- |
| Backlight, Brightness, Contrast, Colour, Sharpness, Hue | Bass, Treble, Balance |

Use **Left / Right** to change a sample value and **Up / Down** to move between rows. Reset restores
the page's sample values. Closing the page discards them.

**These sliders do not change the TV's picture or sound.** Hardware integration is pending model-specific
ADB inspection and testing. Each page includes an **Open TV picture/sound settings** shortcut to the working
system controls. The existing remote volume control is separate and remains functional.

## Remote volume and navigation

The launcher provides its own volume indicator and an optional fast-volume helper for firmware with
unreliable held-key repeat. With configured local pairing, recovery is requested at boot and after app updates;
the routine Volume auto-start menu entry is hidden.

The working helper and launcher key services handle volume without requiring a manual start command
after every normal reboot. Recovery still depends on this TV's saved authorization, permissions and
wireless-debugging availability.

Horizontal app navigation follows visible app order across row boundaries. First/last items stop in
place; Up/Down follow columns. Up from the first row goes to Search. Only Search, the bell and Settings
are in the toolbar's remote focus path. Continue Watching is entered with Down from the bottom app row.

## Automatic Home and remote setup

Initial Home setup handles one-time authorization and saved local pairing. Routine Home/remote and volume
setup entries are hidden from the normal System menu once using automatic recovery. Existing pairing,
permissions and bounded recovery continue to work; hiding the shortcuts does not remove their backend.

Saved local ADB pairing can set this package as the real Home app and restore only its Home/Settings
helpers while preserving other enabled accessibility services. The system Home resolver is checked
after setting it; failure is reported separately from volume-helper status. Setup is requested at boot
and after an app update, with bounded repair attempts when Home is opened. It does not disable the stock
launcher or run a permanent polling service.

For the requested upgrade, existing installations with paired and enabled volume recovery start with
Home automation enabled. Fresh installs remain off until enabled from the guide. One-time Android
permission/pairing approval is still necessary on a new TV. Choosing the Android Home picker disables
automatic repair so it will not overwrite that choice. Disabling repair leaves the selected Home in place.

The protected shell diagnostic is:
`adb shell content call --uri content://com.sadique.launcher.theme --method home_auto_status_v1`.

## Online landscape screensaver

Every 30 seconds the screensaver requests a fresh seed from the original Picsum image endpoint.
All photo subjects and original orientations are accepted; the provider returns a screen-filling
1920×1080 crop. Downloaded image hashes are recorded privately to reject exact repeats across
sessions/reboots. The provider has a finite collection, so a fresh seed cannot guarantee a never-seen
photo on every request; up to three attempts are made before retaining the current image and retrying.
Screensaver images are not stored on disk.

Photographer, resolution and provider labels are hidden. Picsum does not supply scene descriptions,
so the app does not invent them. Weather sits at bottom-left and shows “Unavailable” on failure or
offline, without refresh timestamps. The screensaver clock is bold; clock/date placement is configurable.

Home uses Bing's daily image and retains one private, atomic snapshot for the first frame after an
activity/process restart. The cached image is restored before drawing; an online Home never draws the
built-in wallpaper as an interim fallback. The small local profile photo is also decoded before Home
is built. Bing metadata checks refresh the daily photo without forcing another download on each Home return.

For Android's system-managed screensaver, open **Settings → System → Screensaver & weather →
Set automatic screensaver** and select **Online Landscapes · Personal Launcher**. City, temperature
units and Home idle startup are in the same menu. Weather defaults to **Okara, Pakistan**.
The Home idle timeout defaults to Off and does not cover another app's playback.

Online sources: `picsum.photos`, `fastly.picsum.photos`, `www.bing.com`,
`api.open-meteo.com`, `geocoding-api.open-meteo.com`, `wttr.in`.

## Continue Watching and notifications

Continue Watching appears after the app grid. It reads Android TV's shared Watch Next provider, filters
finished entries and hidden apps, and opens the publishing app's resume link. Artwork uses the provider's
image or an app-icon fallback. Access and available entries depend on the TV and publishing apps;
private Netflix/other app histories cannot be read, and no resume items are invented.

The notification bell shows eligible clearable alerts. Ongoing, playback, foreground-service and active
progress notifications are filtered out. Notification access is requested once on the first idle Home after installation/upgrade and still requires Android approval.
The bell contains alerts only, without an access-settings shortcut. Skipping approval does not trigger repeated prompts.
Alert content stays
in bounded memory and is not stored or uploaded.

## Performance

The launcher chooses a performance tier from device RAM:

- **Light** — minimal effects, including on low-memory TVs such as the Haier H40K800FX; no Frosted window blur.
- **Balanced** — bounded effects and caches.
- **Full** — additional effects where supported.

Override it in **Launcher settings → System → Performance**. Image decoding and caches are bounded,
and memory is trimmed under pressure. Weather failures/offline status show “Unavailable” without an
update timestamp. No performance benchmark improvement is claimed for the glass restyle.

## Install or update

For an existing installation with working ADB:

```sh
curl -fL https://github.com/sadique6571/PersonalLauncher/releases/latest/download/PersonalLauncher.apk -o launcher.apk &&
adb shell 'if [ -f /data/local/tmp/fast-volume-bridge.sh ]; then sh /data/local/tmp/fast-volume-bridge.sh stop; fi' &&
adb install -r launcher.apk &&
adb shell am start -n com.sadique.launcher/.MainActivity
```

Keep the app installed when updating so its preferences and saved pairing remain available. Use the same
package and signing key with an increasing Android version code. The displayed version can remain
**7.0** while release build numbers increase.

An existing paired setup can restore Home and volume automatically after installation; allow startup
recovery to finish. A new TV still needs one-time Android permission/pairing approval through the setup
guide. Keep another working Home available during initial setup.

## Build and release signing

Launcher checks use JDK 17, Gradle 8.9, Android SDK 34 and:

```sh
cd launcher
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

The release workflow runs tests, lint and the release build, then verifies the APK signature before
publishing. Published APKs use the persistent release keystore. CI can use a temporary debug key for
validation; that is different from the published APK.

A push to `main` touching `launcher/**` triggers APK publication. README-only changes do not trigger
a new APK release. The download above follows the latest signed release.

## Volume troubleshooting

The [volume-hold bridge guide](docs/volume-hold-bridge.md) documents the optional shell helper and
diagnostic commands for firmware with slow or missing held-volume repeat.

