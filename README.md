# Personal Launcher

**Personal Launcher by Sadique Sadiq** — a clean, fast home-screen launcher for Android TV / Google TV.

- Package: `com.sadique.launcher`
- Version: 4.8
- Plain Java, classic Android Views, `minSdk` 26. Internet permission is used for online screensaver photos, weather and city lookup.
- Day/night Home wallpaper, compact toolbar, configurable app grid, quick settings and editable name and photo.

## Home screen

- TV: profile at the top left with greeting underneath; weather, network status, Quick actions drawer and Settings at the top right. Search and the notification bell are in the toolbar. Play Store and Screensaver are at the end of Settings; voice uses the remote Assistant button.
- Clock with date (digital or analog, 3 fonts, seconds, glow, shadow, 12/24-hour, left/center/right alignment, hide option). Large bold style like the demo. Apps appear directly below the clock; the clock scrolls away with content.
- One app drawer. Long-press an app → Move app, or use Launcher settings → Rearrange apps; saved order survives restarts and new apps append. Small 16:9 rounded cards show original app icons without wordmark banners. Clean card backgrounds and proportional logos avoid stretched edge pixels; a subtle highlight and shadow give tiles a raised appearance. No labels underneath.
- Focus uses a matching icon-colour glow/border with zoom, press dip and parallax wallpaper effects.
- Tiles: size, spacing, corner radius, logo size, colour glow, focus border thickness, staggered slide-in entrance animation, NEW-app badge.
- Themes: Midnight Cyan, Sunset Orange, Deep Purple, or a custom accent colour — themes tint the glass panels and setting icons.
- Wallpaper: mountain-lake built-in, day/night auto, online photos, slideshow, crossfade, or a saved favourite. Bottom shade, smooth day/night transition, header blur (on/off).
- Bottom shade gradient behind tiles (on/off). Header frosted blur (on/off).
- Launcher-wide font: Default, Serif, Mono or Light.
- Greeting text (time-based, can be hidden). Home weather card (Open-Meteo). Cinematic top/bottom shade and grid backdrop panel.
- No favorites, duplicate dock/quick-access rows or Gemini button.
- Phones keep a portrait/touch layout with adaptive columns. Profile photos are chosen locally through Android's picker.
- Wi-Fi is green only with Android-validated internet access; missing internet is red. Ethernet/mobile data use their own indicators.

Search filters installed, non-hidden apps **inside the launcher** as you type. The explicit Play Store
button opens Google's normal search/install screen for new apps. The mic button uses the TV's configured
Assistant; supported speech-to-text activities can also return a phrase into local app search. The launcher
does not record audio or request microphone permission. Availability depends on the installed system service.

Quick settings contains an inline media-volume slider. Brightness controls, their special permission,
and the Home-window brightness override have been removed; brightness follows the device setting.
Settings pages share one panel with Back navigation, restoring the previous row focus and scroll position, and stay open when a local option changes.
The panel position is configured before showing it, without a slide/reposition animation.

## Launcher settings

Settings are grouped into 6 categories, each with its own icon:

- **Tiles** — columns, tile size/spacing/corners, logo size, focused-app title size, colour glow, focus border, app entrance animation
- **Clock** — size, style (digital/analog), font, 12/24-hour, alignment, seconds, glow, shadow, hide clock, hide greeting
- **Theme** — Midnight Cyan, Sunset Orange, Deep Purple, custom accent
- **Wallpaper** — source (day/night auto, online, favourite), slideshow interval, bottom shade, smooth day/night, header blur, save favourite
- **Apps** — hide/unhide, Netflix & YouTube banner rows (on/off, order, poster size, logos, auto-scroll), row spacing
- **System** — screensaver & weather, profile name/photo, avatar shape, header background, launcher font, performance (Auto/Light/Balanced/Full), export/import settings, default Home, layout reset

Hiding never disables or uninstalls an app. All launcher dialogs share the same rounded, translucent dark-glass styling with adjustable intensity and blur behind dialogs (API 31+).

## Continue watching

Resume entries appear in a separate row **after the app grid**, never among default app icons.
Thumbnails/posters shared by the publishing apps load over HTTPS or Android content providers, with an app-icon fallback when artwork is unavailable.
The row uses Android TV's shared Watch Next provider and asks for TV-listings access when available.
It filters finished items and hidden apps, and opens the publishing app's resume link. Some TVs restrict
provider access and some streaming apps never publish entries; private Netflix/other app histories cannot
be read. Empty or unsupported devices show a clear message instead of invented resume items.

## Netflix & YouTube rows

Optional banner rows directly below the clock showing posters from the TV Watch Next feed — Netflix (`com.netflix.ninja`) and YouTube (`com.google.android.youtube.tv`). Off by default; enable in **Apps** settings. Rows stay hidden if the app published nothing.

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

## Performance

The launcher auto-detects device RAM and selects a performance tier:
- **Light** (≤2 GB, e.g. Haier H40K800FX): static wallpaper, no crossfade/parallax/entrance, minimal effects
- **Balanced** (2–4 GB): bounded effects and caches
- **Full** (>4 GB): all effects

Override via **Settings → System → Performance**. Weather is cached for 15 minutes with stale indication. Bitmaps are decoded to the exact display size. Memory is trimmed automatically under pressure.

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

## APK updates and release signing

Older releases used a fresh runner-generated debug key, so Android reports a package conflict when
installing a later build. Version 4.3 publishing requires one persistent release keystore, stored in
GitHub repository Actions secrets `LAUNCHER_KEYSTORE_BASE64` and `LAUNCHER_KEYSTORE_PASSWORD`.
Use PKCS12, alias `personal-launcher`, with the same store/key password; keep a private backup.
Never commit or publish the keystore. The release workflow refuses to publish when signing secrets
are missing. CI still builds and tests independently with a temporary debug key.

An installed APK signed by a lost temporary key cannot accept the new release key. A **one-time
uninstall/reinstall** is necessary, which removes local launcher preferences. Keep another working
Home enabled before doing this. After migration, APKs with this same package/key and increasing
version codes install as updates. Do not uninstall anything automatically.

Automatic system screensavers require selecting **Personal Launcher Landscapes** in Android settings. Use Screensaver → Set automatic screensaver for selection instructions and status. The DreamService declares the system binding permission and settings metadata; there is no ordinary runtime permission that can silently replace the Android-selected screensaver. A 5-minute Home-only idle fallback can be enabled from setup when firmware restricts the picker.

### Simplified TV Home (5.2)

Home has one editable name/photo and a notification bell next to Search. Local profile switching,
watchlists, Now Playing, Live Guide and remote-button learning have been removed. Existing name,
layout and current avatar/wallpaper paths are preserved on update. Saved legacy profile data is not
used to switch identities and is not deleted. Continue Watching still opens shared streaming entries,
with no watchlist-save long press. The category bar has no “All apps” button; select a category again
to reset it, or use Reset filter in Categories. The strip is hidden when no categories exist.

The bell lists notification alerts only and updates while open. Foreground-service, ongoing,
non-clearable, group-summary, playback and active-progress status entries are excluded. Finished,
clearable download alerts can still appear. Access consent is requested separately; no refresh or
permission controls are mixed into the alert list. Notification content stays in bounded memory,
is never stored or sent, and the existing listener component/consent remains valid.

Settings starts with a two-column TV-style button grid for Network, Bluetooth, Inputs, Display,
Sound and TV settings. Bluetooth uses the standard firmware Bluetooth settings action, not the
pair-new-accessory screen. Volume and launcher preferences follow; Search, Store and Screensaver
are at the bottom. Startup diagnostics is hidden from the normal panel; boot log instrumentation
is retained for troubleshooting. The optional launcher PIN, apps-only mode and inexact sleep timer
remain available; PIN is a UI gate, not Android security. Sleep needs the Home accessibility service,
may be delayed by Android, and clears on reboot. Home/Settings theme mappings and accessibility
permissions are not modified by this update. No Favorites or Recently Opened feature is added.

Validation includes notification filtering and legacy asset-path/category tests. Physical TV checks
are still needed for button focus, notification delivery and the firmware Bluetooth screen.

When accessibility key filtering is active, held volume uses a fallback repeat only when the firmware
provides no native repeats. The original tap still goes to Android. Shared press identity prevents
both key services from adding/resetting repeat loops; release cancels it and a 30-second safety limit
covers a lost release. The explicit consume_volume_keys compatibility mode remains available.

Horizontal app navigation follows visible app order across row boundaries: Right enters the next row,
Left enters the preceding row, and the first/last item stops in place. Pending off-screen focus is
tracked during held-key repeats so geometric focus search cannot jump into the header. Up/Down and
Continue Watching retain their own navigation.
