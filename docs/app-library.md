# App library

Open **Apps** in the Home toolbar on the Glass Home layout, or **All apps** on Harmony. Harmony opens a grid. The Glass library is a horizontal list.

- Press **OK** to open an app, including apps hidden from Home.
- Hold **OK** for **Open**, **Add to Home** or **Remove from Home**, and **App info**. Adding or removing an app changes its Home visibility. The app stays installed and remains available in the library.
- Press **Back** to close the action menu or return to Home.

To edit Home visibility directly, open **Launcher settings → Apps → Hide / unhide apps**. Select an app to toggle its visibility. This uses the same saved visibility choices as the app library.

The library lists installed apps with an Android TV or standard Android launch activity. When both are available, it prefers the TV entry. Apps hidden from Home appear first, with recent installations prioritized within that group. Apps visible on Home show a checkmark. Some standard Android apps may require a touch input or a mouse.

The current Home saves visibility in its `hidden_apps` preference. The `app_library_selection`, `library_ok_opens`, and `show_library_pill` overlay options belong to the older Home implementation, as do the separate `library_apps.txt` and `library_home_removed.txt` shortcut files.

Implementation: [Home](../launcher/app/src/main/kotlin/com/sadique/launcher/PersonalHomeActivity.kt), [app library](../launcher/app/src/main/kotlin/com/sadique/launcher/AppLibraryDialog.kt), and [Apps settings](../launcher/app/src/main/kotlin/com/sadique/launcher/HomeSettingsMenus.kt).
