package com.akylas.enforcedoze

import android.app.Notification
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import java.lang.ref.WeakReference

/** Reads platform playback states without creating a player, callback thread or permanent controller. */
class NotificationService : NotificationListenerService() {
    override fun onListenerConnected() { instance = WeakReference(this) }
    override fun onListenerDisconnected() { instance = null }
    override fun onDestroy() { instance = null; super.onDestroy() }
    fun playingPackages(): Set<String>? {
        return try {
            activeNotifications.mapNotNull { item ->
                @Suppress("DEPRECATION")
                val token = item.notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
                val state = token?.let { MediaController(this, it).playbackState?.state }
                if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING) item.packageName else null
            }.toSet()
        } catch (_: Exception) { null }
    }
    fun getPlayingPackageName(callback: (String?) -> Unit?) { callback(playingPackages()?.firstOrNull()) }
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        if (sbn == null || !ForceDozeService.restrictNotifications) return
        val protected = sbn.notification.category in setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM, Notification.CATEGORY_TRANSPORT)
        if (protected || sbn.isOngoing || playingPackages()?.contains(sbn.packageName) == true) return
        val blocked = android.preference.PreferenceManager.getDefaultSharedPreferences(this).getStringSet("notificationBlockList", emptySet()) ?: emptySet()
        if (blocked.contains(sbn.packageName)) cancelNotification(sbn.key)
    }
    companion object {
        private var instance: WeakReference<NotificationService>? = null
        @JvmStatic fun getInstance(): NotificationService? = instance?.get()
    }
}
