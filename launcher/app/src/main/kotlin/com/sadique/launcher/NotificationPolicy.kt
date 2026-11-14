package com.sadique.launcher

import android.app.Notification

/** Notification alerts, excluding persistent background-work and playback status. */
object NotificationPolicy {

    @JvmStatic
    fun show(flags: Int, category: String?, hasContent: Boolean, activeProgress: Boolean): Boolean {
        val hidden = Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_ONGOING_EVENT or
                Notification.FLAG_NO_CLEAR or Notification.FLAG_GROUP_SUMMARY
        return hasContent && (flags and hidden) == 0 && !activeProgress &&
                Notification.CATEGORY_SERVICE != category && Notification.CATEGORY_TRANSPORT != category
    }
}
