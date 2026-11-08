// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze.android

import android.app.UiModeManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.BatteryManager
import android.os.Handler
import io.github.akane599.enforcedoze.doze.HoldOff

/** The rules behind the hold-off signals of SPEC 5.5, each with where it comes from. */
internal object HoldOffRules {
    /**
     * Audio modes Telecom's `CallAudioModeStateMachine` sets for a ringing call, a call, VoIP and their
     * redirects (android-17.0.0_r1). Watching the mode needs no phone permission.
     */
    private val CALL_MODES = setOf(
        AudioManager.MODE_RINGTONE,
        AudioManager.MODE_IN_CALL,
        AudioManager.MODE_IN_COMMUNICATION,
        AudioManager.MODE_CALL_SCREENING,
        AudioManager.MODE_CALL_REDIRECT,
        AudioManager.MODE_COMMUNICATION_REDIRECT,
    )

    /**
     * `WifiManager.WIFI_AP_STATE_CHANGED_ACTION` and its `wifi_state` extra are system APIs, so they are
     * spelled out. Wi-Fi sends it from `SoftApManager.updateApState` to holders of `ACCESS_WIFI_STATE`,
     * not sticky (android-17.0.0_r1).
     */
    const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    const val EXTRA_WIFI_AP_STATE = "wifi_state"

    /** `WIFI_AP_STATE_ENABLING` (12) and `WIFI_AP_STATE_ENABLED` (13); 10, 11 and 14 are disabling, disabled, failed. */
    private val HOTSPOT_ON = setOf(12, 13)

    fun isCall(mode: Int): Boolean = mode in CALL_MODES

    fun isHotspotOn(state: Int): Boolean = state in HOTSPOT_ON

    /** `BatteryManager.EXTRA_PLUGGED` is 0 on battery and a plug type otherwise; Doze also treats plugged as charging. */
    fun isPlugged(plugged: Int): Boolean = plugged != 0

    /** The app on screen when the screen went off: the package of the last `ACTIVITY_RESUMED` event. */
    fun lastResumed(events: Sequence<Pair<Int, String>>): String? =
        events.lastOrNull { it.first == UsageEvents.Event.ACTIVITY_RESUMED }?.second
}

/** One hold-off signal. Callbacks arrive on the main thread; [start] returns the state now, or false when unknown. */
internal interface HoldOffSource {
    val holdOff: HoldOff

    fun start(report: (Boolean) -> Unit): Boolean

    fun stop()
}

/** An active or ringing call, from the audio mode (SPEC 5.5). */
internal class CallSource(private val context: Context) : HoldOffSource {
    override val holdOff: HoldOff = HoldOff.CALL
    private val audio = context.getSystemService(AudioManager::class.java)
    private var listener: AudioManager.OnModeChangedListener? = null

    override fun start(report: (Boolean) -> Unit): Boolean {
        val watch = AudioManager.OnModeChangedListener { mode -> report(HoldOffRules.isCall(mode)) }
        audio.addOnModeChangedListener(context.mainExecutor, watch)
        listener = watch
        return HoldOffRules.isCall(audio.mode)
    }

    override fun stop() {
        listener?.let { audio.removeOnModeChangedListener(it) }
        listener = null
    }
}

/**
 * Audio playing (SPEC 5.5). A playback configuration change is the trigger; whether the music stream
 * is active is the state, since a configuration's own active flag is not public. The stream can turn
 * active a moment after the change, so it is checked again twice, then nothing runs until the next change.
 */
internal class AudioSource(context: Context, private val handler: Handler) : HoldOffSource {
    override val holdOff: HoldOff = HoldOff.AUDIO
    private val audio = context.getSystemService(AudioManager::class.java)
    private var report: ((Boolean) -> Unit)? = null
    private val check = Runnable { report?.invoke(audio.isMusicActive) }
    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            handler.removeCallbacks(check)
            check.run()
            RECHECK_MILLIS.forEach { handler.postDelayed(check, it) }
        }
    }

    override fun start(report: (Boolean) -> Unit): Boolean {
        this.report = report
        audio.registerAudioPlaybackCallback(callback, handler)
        return audio.isMusicActive
    }

    override fun stop() {
        audio.unregisterAudioPlaybackCallback(callback)
        handler.removeCallbacks(check)
        report = null
    }

    private companion object {
        val RECHECK_MILLIS = listOf(1_000L, 5_000L)
    }
}

/** A broadcast-driven signal; [initial] reads the state at start where the platform offers it. */
internal abstract class BroadcastSource(private val context: Context, private val actions: List<String>) : HoldOffSource {
    private var receiver: BroadcastReceiver? = null

    abstract fun active(intent: Intent): Boolean?

    abstract fun initial(): Boolean

    override fun start(report: (Boolean) -> Unit): Boolean {
        val listen = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                active(intent)?.let(report)
            }
        }
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        // System broadcasts reach a receiver that other apps cannot call.
        context.registerReceiver(listen, filter, Context.RECEIVER_NOT_EXPORTED)
        receiver = listen
        return initial()
    }

    override fun stop() {
        receiver?.let(context::unregisterReceiver)
        receiver = null
    }
}

/** Car mode, which Android Auto turns on while connected (SPEC 5.5). */
internal class CarModeSource(private val context: Context) :
    BroadcastSource(context, listOf(UiModeManager.ACTION_ENTER_CAR_MODE, UiModeManager.ACTION_EXIT_CAR_MODE)) {
    override val holdOff: HoldOff = HoldOff.CAR_MODE

    override fun active(intent: Intent): Boolean? = when (intent.action) {
        UiModeManager.ACTION_ENTER_CAR_MODE -> true
        UiModeManager.ACTION_EXIT_CAR_MODE -> false
        else -> null
    }

    override fun initial(): Boolean = context.getSystemService(UiModeManager::class.java).currentModeType == Configuration.UI_MODE_TYPE_CAR
}

/** Hotspot on (SPEC 5.5). The broadcast is not sticky and the state getter is a system API, so it starts as off. */
internal class HotspotSource(context: Context) : BroadcastSource(context, listOf(HoldOffRules.ACTION_WIFI_AP_STATE_CHANGED)) {
    override val holdOff: HoldOff = HoldOff.HOTSPOT

    override fun active(intent: Intent): Boolean? =
        intent.getIntExtra(HoldOffRules.EXTRA_WIFI_AP_STATE, -1).takeIf { it >= 0 }?.let(HoldOffRules::isHotspotOn)

    override fun initial(): Boolean = false
}

/** Charging, as plugged in (SPEC 5.5); the sticky battery broadcast gives the state at start. */
internal class ChargingSource(private val context: Context) :
    BroadcastSource(context, listOf(Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED)) {
    override val holdOff: HoldOff = HoldOff.CHARGING

    override fun active(intent: Intent): Boolean? = when (intent.action) {
        Intent.ACTION_POWER_CONNECTED -> true
        Intent.ACTION_POWER_DISCONNECTED -> false
        else -> null
    }

    override fun initial(): Boolean {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        return HoldOffRules.isPlugged(battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0)
    }
}

/**
 * A keep-awake app on screen when the screen went off (SPEC 5.5), from usage events (usage access, T1).
 * With no app chosen, nothing is queried.
 */
internal class KeepAwakeCheck(context: Context, private val chosen: () -> Set<String>) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)

    fun foregroundIsKept(nowMillis: Long): Boolean {
        val keep = chosen()
        if (keep.isEmpty()) return false
        val events = usage.queryEvents(nowMillis - LOOKBACK_MILLIS, nowMillis) ?: return false
        val event = UsageEvents.Event()
        val sequence = generateSequence { if (events.getNextEvent(event)) event.eventType to event.packageName else null }
        return HoldOffRules.lastResumed(sequence) in keep
    }

    private companion object {
        const val LOOKBACK_MILLIS = 30 * 60 * 1_000L
    }
}
