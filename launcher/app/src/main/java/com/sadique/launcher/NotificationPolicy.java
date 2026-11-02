package com.sadique.launcher;

import android.app.Notification;

/** Notification alerts, excluding persistent background-work and playback status. */
final class NotificationPolicy {
    private NotificationPolicy() { }
    static boolean show(int flags, String category, boolean hasContent, boolean activeProgress) {
        int hidden = Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_ONGOING_EVENT
                | Notification.FLAG_NO_CLEAR | Notification.FLAG_GROUP_SUMMARY;
        return hasContent && (flags & hidden) == 0 && !activeProgress
                && !Notification.CATEGORY_SERVICE.equals(category) && !Notification.CATEGORY_TRANSPORT.equals(category);
    }
}
