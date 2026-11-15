package com.sadique.launcher

import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

internal class PersonalApp(
    val name: String,
    val info: ResolveInfo,
    var icon: Drawable?,
    var accent: Int,
    var iconReady: Boolean
) {
    val iconRequest = ArtworkRequestState()
}
