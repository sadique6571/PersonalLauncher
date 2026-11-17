# Continue watching (Watch Next)

On supported TVs, Personal Launcher can show resume cards shared by installed apps through Android TV's Watch Next provider. The row appears below the app grid when enabled. Harmony and the Apps-only Home layout omit the row.

Open **Launcher settings → Apps → Continue watching** to choose **Show below apps**, **Hide row**, or **Allow TV Watch Next access**. **Show below apps** also switches off Apps-only mode. You can also request access from the row when the device exposes the provider. The launcher requests `android.permission.READ_TV_LISTINGS`. Availability depends on the TV firmware and the entries apps choose to share.

The launcher queries Watch Next in descending order of last engagement and displays up to 16 eligible items. It scans at most 200 rows and skips non-browsable rows, apps hidden from Home, missing apps, and entries without a title or resume intent, or with an intent over 8,192 characters. It shows **Continue** items only and skips completed items when their duration is known. It does not read private app histories or populate the row from preview channels.

Select a card to open its resume link in the app that published it. The app may open a profile chooser or reject a stale link. If the row is empty after permission is granted, the TV may have no eligible shared items. Artwork can require an internet connection.

Implementation: [Continue watching view](../launcher/app/src/main/kotlin/com/sadique/launcher/ContinueWatchingView.kt) and [progress filtering](../launcher/app/src/main/kotlin/com/sadique/launcher/WatchNextModel.kt).
