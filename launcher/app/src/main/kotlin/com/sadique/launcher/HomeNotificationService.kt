package com.sadique.launcher

import android.app.Notification
import android.app.PendingIntent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.ArrayList

/** Optional notification alerts. Bounded memory only; ongoing service status is excluded. */
class HomeNotificationService : NotificationListenerService() {
    class Entry(sbn: StatusBarNotification, @JvmField val title: String, @JvmField val text: String) {
        @JvmField val key: String = sbn.key
        @JvmField val pkg: String = sbn.packageName
        @JvmField val open: PendingIntent? = sbn.notification?.contentIntent
    }

    private fun put(sbn: StatusBarNotification?) {
        if (sbn == null) return
        synchronized(HomeNotificationService::class.java) {
            // Remove first: a formerly visible notification can become ongoing/empty on update.
            ENTRIES.remove(sbn.key)
            val n = sbn.notification
            if (n != null && packageName != sbn.packageName) {
                try {
                    val extras = n.extras ?: Bundle.EMPTY
                    val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)
                    var rawText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    if (rawText == null) rawText = extras.getCharSequence(Notification.EXTRA_TEXT)
                    val title = HomeLibraryModel.title(rawTitle?.toString() ?: "")
                    val text = HomeLibraryModel.title(rawText?.toString() ?: "")
                    val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
                    val progress = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) ||
                            (max > 0 && extras.getInt(Notification.EXTRA_PROGRESS, 0) < max)
                    if (NotificationPolicy.show(n.flags, n.category, title.isNotEmpty() || text.isNotEmpty(), progress)) {
                        ENTRIES[sbn.key] = Entry(sbn, if (title.isEmpty()) sbn.packageName else title, text)
                    }
                } catch (_: RuntimeException) {
                    /* Bad extras cannot crash the listener. */
                }
            }
            while (ENTRIES.size > 20) {
                val firstKey = ENTRIES.keys.iterator().next()
                ENTRIES.remove(firstKey)
            }
        }
        changed()
    }

    override fun onListenerConnected() {
        synchronized(HomeNotificationService::class.java) {
            connected = this
            ENTRIES.clear()
        }
        try {
            val items = activeNotifications
            if (items != null) {
                for (item in items) {
                    put(item)
                }
            }
        } catch (_: RuntimeException) {
            /* Access can be revoked while connecting. */
        }
        changed()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        put(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        synchronized(HomeNotificationService::class.java) {
            if (sbn != null) ENTRIES.remove(sbn.key)
        }
        changed()
    }

    override fun onListenerDisconnected() {
        clear()
    }

    override fun onDestroy() {
        clear()
        super.onDestroy()
    }

    private fun clear() {
        synchronized(HomeNotificationService::class.java) {
            if (connected == this) {
                connected = null
                ENTRIES.clear()
            }
        }
        changed()
    }

    companion object {
        private val ENTRIES = LinkedHashMap<String, Entry>()
        private val OBSERVERS = HashSet<Runnable>()
        private var connected: HomeNotificationService? = null

        @JvmStatic
        @Synchronized
        fun snapshot(): List<Entry> {
            return ArrayList(ENTRIES.values)
        }

        @JvmStatic
        @Synchronized
        fun observe(observer: Runnable) {
            OBSERVERS.add(observer)
        }

        @JvmStatic
        @Synchronized
        fun unobserve(observer: Runnable) {
            OBSERVERS.remove(observer)
        }

        private fun changed() {
            val listeners: List<Runnable>
            synchronized(HomeNotificationService::class.java) {
                listeners = ArrayList(OBSERVERS)
            }
            val main = Handler(Looper.getMainLooper())
            for (listener in listeners) {
                main.post {
                    synchronized(HomeNotificationService::class.java) {
                        if (!OBSERVERS.contains(listener)) return@post
                    }
                    listener.run()
                }
            }
        }
    }
}
