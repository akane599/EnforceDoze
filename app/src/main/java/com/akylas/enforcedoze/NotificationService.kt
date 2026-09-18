package com.akylas.enforcedoze

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.lang.ref.WeakReference

/** Native session callbacks; no polling, player instances or per-query executor threads. */
class NotificationService : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var controllers: List<MediaController> = emptyList()
    @Volatile private var sessionsReadable = false
    private var lastPlaying: Set<String>? = null
    private var manager: MediaSessionManager? = null
    private var audioCallback: AudioManager.AudioPlaybackCallback? = null
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { mediaChanged() }
        override fun onSessionDestroyed() { refreshSessions() }
    }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { replaceSessions(it ?: emptyList()) }
    override fun onListenerConnected() {
        instance = WeakReference(this)
        manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        if (android.os.Build.VERSION.SDK_INT >= 26 && audioCallback == null) {
            audioCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>?) { mediaChanged() }
            }
            (getSystemService(AUDIO_SERVICE) as AudioManager).registerAudioPlaybackCallback(audioCallback!!,main)
        }
        try {
            manager?.addOnActiveSessionsChangedListener(sessionsChanged, ComponentName(this, NotificationService::class.java), main)
            refreshSessions()
        } catch (_: SecurityException) { sessionsReadable = false; mediaChanged() }
    }
    private fun refreshSessions() {
        try {
            replaceSessions(manager?.getActiveSessions(ComponentName(this, NotificationService::class.java)) ?: emptyList())
        } catch (_: SecurityException) { sessionsReadable = false; mediaChanged() }
    }
    private fun replaceSessions(next: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = next.toList()
        controllers.forEach { it.registerCallback(callback, main) }
        sessionsReadable = true
        mediaChanged()
    }
    private fun disconnect() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            audioCallback?.let { (getSystemService(AUDIO_SERVICE) as AudioManager).unregisterAudioPlaybackCallback(it) }
            audioCallback = null
        }
        controllers.forEach { it.unregisterCallback(callback) }; controllers = emptyList()
        manager?.removeOnActiveSessionsChangedListener(sessionsChanged)
        sessionsReadable = false; instance = null
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("media-changed"))
    }
    override fun onListenerDisconnected() { disconnect() }
    override fun onDestroy() { disconnect(); super.onDestroy() }
    fun playingPackages(): Set<String>? {
        if (!sessionsReadable) return null
        return try {
            var unknown = false
            val playing = controllers.mapNotNull { controller ->
                val state = controller.playbackState?.state
                if (state == null) unknown = true
                if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING) controller.packageName else null
            }.toSet()
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            if (unknown || (playing.isEmpty() && audio.isMusicActive)) null else playing
        } catch (_: Exception) { null }
    }
    private fun mediaChanged() {
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("whitelistMusicAppNetwork",false)) return
        val now = playingPackages()
        if (now != lastPlaying) {
            lastPlaying = now
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("media-changed"))
        }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) mediaChanged()
        if (!ForceDozeService.restrictNotifications) return
        val blocked = PreferenceManager.getDefaultSharedPreferences(this).getStringSet("notificationBlockList", emptySet()) ?: emptySet()
        if (!blocked.contains(sbn.packageName) || sbn.packageName == packageName || sbn.packageName == "moe.shizuku.privileged.api") return
        val protected = sbn.notification.category in setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM, Notification.CATEGORY_TRANSPORT)
        if (protected || sbn.isOngoing || sbn.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return
        cancelNotification(sbn.key)
    }
    companion object {
        @Volatile private var instance: WeakReference<NotificationService>? = null
        @JvmStatic fun getInstance(): NotificationService? = instance?.get()
    }
}
