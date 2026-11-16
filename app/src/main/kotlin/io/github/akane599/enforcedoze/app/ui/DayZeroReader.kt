// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.util.Log
import io.github.akane599.enforcedoze.evidence.ExpectedWalk
import io.github.akane599.enforcedoze.parse.BatteryHistory
import io.github.akane599.enforcedoze.parse.DeviceIdleDump
import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.parse.SensorMode
import io.github.akane599.enforcedoze.parse.SensorServiceDump
import io.github.akane599.enforcedoze.privilege.WorkerResult
import io.github.akane599.enforcedoze.recommend.DayZero
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * The phone's own history for the first recommendation (SPEC 7.1), read once per process and again after the
 * grants change: the battery history through the worker, and UsageStats screen events with usage access.
 * Reading the whole battery history is the heaviest read the app makes, so it happens only while the app's own
 * records do not yet cover the 30 days the signals look back over.
 */
internal class DayZeroReader(private val services: AppServices) {
    private val lock = Mutex()
    private var loaded = false
    private var result: DayZero.Result? = null

    suspend fun result(): DayZero.Result? = lock.withLock {
        if (!loaded) {
            result = read()
            loaded = true
        }
        result
    }

    /** After a grant or a worker appears, the next request reads again. */
    suspend fun forget() = lock.withLock { loaded = false }

    private suspend fun read(): DayZero.Result? = withContext(Dispatchers.IO) {
        val readAt = Instant.now()
        val events = screenEvents(readAt)
        val reads = (services.graph.controller.withWorker { api ->
            Reads(
                history = attempt { api.readBatteryHistory(0) },
                deviceIdle = attempt { api.readDeviceIdle() },
                constants = attempt { api.deviceIdleSettings() }?.takeIf { it.isSuccess }?.stdout,
                sensors = attempt { api.readSensorService() },
            )
        } as? WorkerResult.Ok)?.value
        val history = reads?.history?.let { BatteryHistory.parse(it, ZoneId.systemDefault(), readAt) }
        val dump = reads?.deviceIdle?.let(DeviceIdleDump::parse)
        val timers = dump?.settings?.mapValues { it.value.raw }.orEmpty()
        val constants = reads?.constants?.let { DeviceIdleSettings.parse(it.trim()) }
        val stockNow = constants != null && constants.valid && constants.values.isEmpty() &&
            dump?.forceIdle == false &&
            reads?.sensors?.let(SensorServiceDump::parse)?.mode == SensorMode.NORMAL
        DayZero.read(
            DayZero.Sources(
                history = history,
                screenEvents = events,
                stockNow = stockNow,
                expectedToDeep = ExpectedWalk.of(false, timers),
                fastestStockDeep = ExpectedWalk.of(false, timers.filterKeys { it in FIXED_TIMERS } + ZERO_SENSING),
            ),
        )
    }

    /** `SCREEN_INTERACTIVE` and `SCREEN_NON_INTERACTIVE` (API 28) over the last 30 days; none without usage access. */
    private fun screenEvents(now: Instant): List<DayZero.ScreenEvent> = try {
        val usage = services.graph.app.getSystemService(UsageStatsManager::class.java)
        val from = now.minus(Recommendations.HISTORY).toEpochMilli()
        val events = usage.queryEvents(from, now.toEpochMilli())
        val event = UsageEvents.Event()
        buildList {
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> add(DayZero.ScreenEvent(Instant.ofEpochMilli(event.timeStamp), true))
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> add(DayZero.ScreenEvent(Instant.ofEpochMilli(event.timeStamp), false))
                }
            }
        }
    } catch (denied: SecurityException) {
        Log.i(TAG, "no usage access: day zero reads the battery history alone", denied)
        emptyList()
    }

    private data class Reads(val history: String?, val deviceIdle: String?, val constants: String?, val sensors: String?)

    /** One read inside the worker call: a failed one is left out and the others are kept. */
    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (
        @Suppress("TooGenericExceptionCaught") failed: Exception, // Each read is separate evidence.
    ) {
        Log.w(TAG, "a day-zero read failed", failed)
        null
    }

    private companion object {
        const val TAG = "DayZero"

        /** The two timers no stock walk skips (`stepIdleStateLocked`, android-17.0.0_r1); sensing can end early. */
        val FIXED_TIMERS = setOf("inactive_to", "idle_after_inactive_to")

        /** [ExpectedWalk] sums four timers; the fastest walk spends nothing in sensing or locating. */
        val ZERO_SENSING = mapOf("sensing_to" to "0", "locating_to" to "0")
    }
}
