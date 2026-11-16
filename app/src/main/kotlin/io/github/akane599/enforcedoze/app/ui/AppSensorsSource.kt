// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.data.NightReports
import io.github.akane599.enforcedoze.feature.sensors.SensorHolder
import io.github.akane599.enforcedoze.feature.sensors.SensorModeNow
import io.github.akane599.enforcedoze.feature.sensors.SensorSnapshot
import io.github.akane599.enforcedoze.feature.sensors.SensorsSource
import io.github.akane599.enforcedoze.feature.sensors.SensorsState
import io.github.akane599.enforcedoze.parse.SensorMode
import io.github.akane599.enforcedoze.parse.SensorServiceDump
import io.github.akane599.enforcedoze.privilege.WorkerResult
import io.github.akane599.enforcedoze.privilege.probe.LocalReader
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

/**
 * Sensors (SPEC 9.1): the mode and sensor privacy from `dumpsys sensorservice` (the worker at T2, the app itself
 * with `DUMP` at T1), and the apps that held sensors in the last session's snapshots (ADR 0020).
 */
internal class AppSensorsSource(private val services: AppServices) : SensorsSource {
    private val reads = MutableStateFlow(0)
    private val local = LocalReader()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<SensorsState> = combine(
        reads,
        services.evidence,
        services.canWrite,
        services.notices.notices,
    ) { _, _, canWrite, notices -> canWrite to notices }.mapLatest { (canWrite, notices) ->
        val dump = read()?.let(SensorServiceDump::parse)
        SensorsState(
            mode = when (dump?.mode) {
                SensorMode.NORMAL -> SensorModeNow.Normal
                // ADR 0004: the app's own package stands in for "no app exempt".
                SensorMode.RESTRICTED -> SensorModeNow.Restricted(dump.modePackage?.takeIf { it != services.graph.app.packageName })
                SensorMode.OTHER -> SensorModeNow.Other(dump.modeLine.orEmpty())
                null -> SensorModeNow.Unknown
            },
            allSensorsOff = dump?.sensorPrivacy,
            snapshot = snapshot(),
            canWrite = canWrite,
            notices = notices,
        )
    }

    override suspend fun allSensorsOff(on: Boolean): ActionOutcome {
        val result = services.graph.controller.withWorker { it.setAllSensorsOff(on) }
        reads.value++
        return AppAllowlistSource.outcome(result)
    }

    override fun seen(noticeId: String) = services.prefs.seen(noticeId)

    /** The worker when there is one, else the app's own read (needs `DUMP`); null when neither can read. */
    private suspend fun read(): String? {
        (services.graph.controller.withWorker { it.readSensorService() } as? WorkerResult.Ok)?.let { return it.value }
        return local.sensorService().takeIf { it.isSuccess }?.stdout
    }

    /** The last session's snapshots: who held which sensor, at what rate. */
    private suspend fun snapshot(): SensorSnapshot? {
        val session = services.graph.sessions.latestSessions(1).firstOrNull() ?: return null
        val rows = services.graph.sessions.snapshots(session.id).takeIf { it.isNotEmpty() } ?: return null
        val holders = NightReports.sensorHolders(rows).map { holder ->
            SensorHolder(
                label = holder.uid?.let(services.labels::forUid)?.first ?: holder.listener,
                sensor = holder.sensor,
                rateHz = holder.samplingPeriodUs?.takeIf { it > 0 }?.let { MICROS_PER_SECOND / it },
            )
        }
        return SensorSnapshot(session.id, rows.first().time, holders)
    }

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000.0
    }
}
