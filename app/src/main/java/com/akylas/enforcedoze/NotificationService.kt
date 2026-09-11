package com.akylas.enforcedoze

import android.app.Notification
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.lang.ref.WeakReference

class NotificationService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = WeakReference(this)
    }

    fun getPlayingPackageName(callback: (String?) -> Unit?) {
        val notifications = try { activeNotifications.orEmpty() } catch (_: RuntimeException) { emptyArray() }
        callback(findPlayingPackage(this, notifications))
    }

    override fun onListenerDisconnected() {
        if (instance?.get() === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (instance?.get() === this) instance = null
        super.onDestroy()
    }

    companion object {
        private var instance: WeakReference<NotificationService>? = null
        @JvmStatic fun getInstance(): NotificationService? = instance?.get()

        @JvmStatic fun findPlayingPackage(context: Context, notifications: Array<out StatusBarNotification>): String? {
            for (notification in notifications) {
                try {
                    @Suppress("DEPRECATION")
                    val token = notification.notification.extras.getParcelable<Parcelable>(Notification.EXTRA_MEDIA_SESSION)
                            as? MediaSession.Token ?: continue
                    val state = MediaController(context, token).playbackState?.state
                    if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
                            || state == PlaybackState.STATE_CONNECTING) return notification.packageName
                } catch (_: RuntimeException) {
                    // A malformed notification or a session that just died must not hide
                    // the next app's valid playing session and break its network exemption.
                }
            }
            return null
        }
    }
}
