// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze.android

import android.app.AlarmManager
import android.content.Context
import android.os.Handler
import io.github.akane599.enforcedoze.profile.NightSlot
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The plan's window-start alarm (SPEC 6.5, ADR 0024). It is one `RTC` alarm, which never wakes the phone and
 * needs no exact-alarm permission: "This alarm does not wake the device up; if it goes off while the device is
 * asleep, it will not be delivered until the next time the device wakes up" (`AlarmManager.RTC`,
 * `android-17.0.0_r1`). It uses `setWindow` with the 10-minute window AOSP 17 stretches any shorter one to,
 * because plain `set()` may be late by most of the time to its trigger. It is a listener alarm, so it lives only
 * as long as the process; the engine's start covers a restart. It is never set while the screen is off
 * (SPEC 1.3): a request then waits for [screenOn], and meanwhile a maintenance window still starts the night.
 * Main thread only.
 */
internal class NightAlarm(
    context: Context,
    private val handler: Handler,
    private val interactive: () -> Boolean,
    private val onAlarm: () -> Unit,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private var night: NightSlot? = null
    private var waiting = false

    /** Cancelling a listener that is not set only makes AlarmManager log "Unrecognized alarm listener". */
    private var set = false
    private val listener = AlarmManager.OnAlarmListener {
        set = false
        onAlarm()
        request(night)
    }

    /** Sets the alarm for [night]'s next start, or cancels it when there is no night slot. */
    fun request(night: NightSlot?) {
        this.night = night
        unset()
        waiting = night != null && !interactive()
        if (night == null || waiting) return
        val start = night.nextStart(ZonedDateTime.now(ZoneId.systemDefault()))
        alarms.setWindow(AlarmManager.RTC, start.toInstant().toEpochMilli(), WINDOW_MILLIS, TAG, listener, handler)
        set = true
    }

    /** The screen came on: a request that fell while it was off is set now. */
    fun screenOn() {
        if (waiting) request(night)
    }

    /** The wall clock or the time zone changed: the next start moves with it. */
    fun clockChanged() {
        request(night)
    }

    fun cancel() {
        night = null
        waiting = false
        unset()
    }

    private fun unset() {
        if (set) alarms.cancel(listener)
        set = false
    }

    private companion object {
        const val TAG = "enforcedoze:night"
        const val WINDOW_MILLIS = 10 * 60_000L
    }
}
