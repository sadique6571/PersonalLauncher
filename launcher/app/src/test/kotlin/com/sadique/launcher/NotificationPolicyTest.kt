package com.sadique.launcher

import android.app.Notification
import org.junit.Test
import org.junit.Assert.*

class NotificationPolicyTest {
    @Test fun ordinaryAlertsAndFinishedDownloadsRemainVisible() {
        assertTrue(NotificationPolicy.show(Notification.FLAG_AUTO_CANCEL, Notification.CATEGORY_MESSAGE, true, false))
        assertTrue(NotificationPolicy.show(0, null, true, false))
        assertTrue(NotificationPolicy.show(0, "progress", true, false))
    }
    @Test fun filtersBackgroundServicesPlaybackAndActiveProgress() {
        for (flag in intArrayOf(Notification.FLAG_FOREGROUND_SERVICE, Notification.FLAG_ONGOING_EVENT,
                Notification.FLAG_NO_CLEAR, Notification.FLAG_GROUP_SUMMARY))
            assertFalse(NotificationPolicy.show(flag, null, true, false))
        assertFalse(NotificationPolicy.show(0, Notification.CATEGORY_SERVICE, true, false))
        assertFalse(NotificationPolicy.show(0, Notification.CATEGORY_TRANSPORT, true, false))
        assertFalse(NotificationPolicy.show(0, null, true, true))
        assertFalse(NotificationPolicy.show(0, null, false, false))
    }
}
