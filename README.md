# Personal Launcher

**Personal Launcher by Sadique Sadiq** — a clean, fast home-screen launcher for Android TV / Google TV.

- Package: `com.sadique.launcher`
- Version: 4.8
- Plain Java, classic Android Views, `minSdk` 26. Internet permission is used for online screensaver photos, weather and city lookup.
- Day/night Home wallpaper, compact toolbar, configurable app grid, quick settings and local profile.

## Home screen

- TV: profile at the top left with greeting underneath; weather, network status, Quick actions drawer and Settings at the top right. Search, Play Store and Screensaver are inside Quick actions; Inputs stay in Settings and voice uses the remote Assistant button.
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

## Home dashboard and local library (5.0)

The active Home app grid now uses RecyclerView with stable package identities, incremental
list updates and visible-tile artwork loading. It is the only vertical scrolling viewport:
Continue Watching is a full-width footer and its cards scroll horizontally with D-pad focus.
The category strip also follows horizontal focus. Returning from an app restores the selected
tile; layout rebuilds retain grid state. Light mode keeps wallpaper parallax and dialog blur off.
There is no Favorites or Recently Opened section.

The existing Settings remote action opens the new Home dashboard, grouped into Your Home,
Media & TV, and Device controls. Long-press an app to assign or create a category; use the
category strip to filter without changing hidden apps or the saved app order. Rearrangement
operates on the complete visible library and preserves hidden slots.

Select the avatar or **Local profiles** in the dashboard to switch between up to six local
profiles. Each has its own app order, hidden apps, categories, theme, wallpaper/avatar files,
and saved watchlist. The original/default profile keeps existing files and preferences.
Long-press a non-default profile to delete it. These profiles do not switch Android users,
Google accounts or accounts inside streaming apps. Performance/accessibility and remote
configuration stay global. Settings export/import currently covers the active profile's
preferences, not the separate registry or image files of every profile.

**Watchlist** stores up to 50 titles locally. Add an HTTP(S) link in the dashboard, or hold a
Continue Watching card to save the publishing app's resume link. Select a saved title to open
it; hold to remove it. It is not Google Watchlist sync and cannot retrieve private app histories.

**Now playing** and **Notifications** are optional. Their access page explains the data read
before opening Android's notification-access settings. Until the user explicitly enables
Personal Launcher there, neither page can read other apps' sessions or notifications. Only
supported media actions are enabled. Notifications are capped at 20 in memory, cleared on
listener disconnect, and never stored or uploaded. Use Refresh to update an open page.
No notification access is automatically granted, and the existing GuardRevival helper still
reads no notifications. Disable the new listener in Android settings to revoke this feature.

**Live TV guide** queries at most 24 browsable channels and two now/next programmes per channel
from the TV provider. It requires the existing TV listings access; firmware may refuse it or
publish no data. In that case use Inputs. This is not Google's regional Freeplay catalogue.

Install an update over the existing package; do not uninstall or clear data. The package,
persistent release signing setup, Home alias, Home key service and Settings key mappings are
preserved. Physical Haier D-pad/scrolling, return-from-app, profile switching and media/provider
access checks are still required; CI cannot establish those device behaviours.

### Optional launcher tools (5.1)

The Home dashboard includes **Remote buttons**, **Apps-only mode**, **Startup diagnostics**,
**Launcher PIN**, and **Sleep timer**. They are local tools; no Google code/assets were copied.

- Remote buttons learns Android keycodes, not shared Linux scan codes. It saves local overrides
  separately from theme JSON, preserves Home/navigation/power/digits, and supports restoring each
  original mapping. Outside Home it needs an enabled accessibility key service with key filtering.
  Keys consumed by firmware cannot be learned. The settings key (83) and dot key (76) remain distinct.
- Apps-only hides the clock and the Continue Watching shelf. Categories, the app grid, and the top
  controls remain available. It is off by default and does not delete saved titles or profiles.
- Diagnostics displays the current HOME resolver, redirect/service state, uptime and up to 32 timing
  events retained by this process. It does not read logcat, send data, or collect device identifiers.
- The optional 4–8 digit PIN gates the dashboard and profile chooser, including their settings/profile
  editing actions. It is a launcher UI gate, not Android security or a streaming-app parental lock.
  A salted PBKDF2 hash is computed on a worker; five failed attempts impose a 30-second cooldown.
  Clearing launcher data removes the PIN. It is not included in profile/theme exports.
- Sleep timer requests standby through the already-bound Home key accessibility service. Android may
  delay its inexact alarm; unsupported firmware can refuse standby. Cancel is available in the timer
  panel. No extra permission, polling or permanent service is added, and reboot clears the alarm.
- App tiles expose collection row/column information to accessibility services. The section heading
  reads “Your apps”; “All apps” appears only in the category filter.

Physical TV testing remains required for key capture, TalkBack, focus movement and standby.
