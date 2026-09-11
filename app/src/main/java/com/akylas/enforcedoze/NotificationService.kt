package com.akylas.enforcedoze

import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import java.lang.ref.WeakReference

class NotificationService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = WeakReference(this)
    }
    fun getPlayingPackageName(callback: (String?) -> Unit?) {
        var playing: String? = null
        try {
            for (notification in activeNotifications.orEmpty()) {
                @Suppress("DEPRECATION")
                val token = notification.notification.extras.getParcelable<MediaSession.Token>("android.mediaSession") ?: continue
                val state = MediaController(this, token).playbackState?.state
                if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING) {
                    playing = notification.packageName
                    break
                }
            }
        } catch (_: RuntimeException) { }
        callback(playing)
    }
    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }
    companion object {
        private var instance: WeakReference<NotificationService>? = null
        @JvmStatic fun getInstance(): NotificationService? = instance?.get()
    }
}
