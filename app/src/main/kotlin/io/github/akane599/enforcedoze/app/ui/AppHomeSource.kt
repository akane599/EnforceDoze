// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.doze.PlanSlot
import io.github.akane599.enforcedoze.feature.home.BoardRow
import io.github.akane599.enforcedoze.feature.home.HomeSource
import io.github.akane599.enforcedoze.feature.home.HomeState
import io.github.akane599.enforcedoze.feature.home.NightSummary
import io.github.akane599.enforcedoze.feature.home.PlanBoard
import io.github.akane599.enforcedoze.feature.home.Until
import io.github.akane599.enforcedoze.feature.report.StopEvent
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.StateNotice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalTime
import java.time.ZonedDateTime

/** Home (SPEC 9.1) from the engine, the evidence log and the shared warnings. */
internal class AppHomeSource(private val services: AppServices, private val reports: Reports) : HomeSource {
    private data class Inputs(
        val view: EngineView,
        val names: Map<String, String>,
        val notices: List<StateNotice>,
        val canWrite: Boolean,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<HomeState> = combine(
        services.engine,
        services.profileNames,
        services.notices.notices,
        services.canWrite,
        services.evidence,
    ) { view, names, notices, canWrite, _ -> Inputs(view, names, notices, canWrite) }
        .combine(services.proof) { inputs, proof -> inputs to proof }
        .mapLatest { (inputs, proof) ->
            HomeState(
                plan = board(inputs.view, inputs.names, inputs.canWrite, ZonedDateTime.now()),
                night = lastSession(),
                notices = inputs.notices,
                proof = proof,
            )
        }

    override suspend fun pause() {
        services.access.send { it.engine.pause().let { ActionOutcome.Done } }
    }

    override suspend fun resume() {
        services.access.send(turnOn = true) { it.engine.resume().let { ActionOutcome.Done } }
    }

    override suspend fun quickProof() = services.runProof()

    override fun seen(noticeId: String) = services.prefs.seen(noticeId)

    /** The latest session's verdict and four numbers, or its night's when it was part of one. */
    private suspend fun lastSession(): NightSummary? {
        val session = services.graph.sessions.latestSessions(1).firstOrNull() ?: return null
        val report = reports.report(session.id) ?: return null
        val summary = report.summary
        return NightSummary(
            sessionId = summary.sessionId,
            start = summary.start,
            end = summary.end,
            night = summary.night,
            verdict = summary.verdict,
            cause = summary.cause,
            breaks = report.timetable.count { it.event is StopEvent.Break },
            lateBy = summary.lateBy,
            timeInDeep = summary.timeInDeep,
            maintenanceWindows = summary.maintenanceWindows,
            strip = summary.strip,
            timeToDeep = summary.timeToDeep,
            deepShare = summary.deepShare,
            drainPerHour = summary.drainPerHour,
            ownWakeups = summary.ownWakeups,
        )
    }

    companion object {
        /**
         * The plan as a departure board (SPEC 9.1): the slot running now, then the next one. The night ends at the
         * first unlock after its end (SPEC 6.5), so its row says so rather than naming a fixed time.
         */
        fun board(view: EngineView, names: Map<String, String>, canWrite: Boolean, now: ZonedDateTime): PlanBoard {
            fun name(id: String) = names[id] ?: id
            val stock = name(BuiltInProfiles.STOCK.id)
            if (view.paused) return PlanBoard(listOf(BoardRow(null, stock, Until.Resumed)), paused = true, canWrite)
            val state = view.plan
            val single = state?.override ?: state?.plan?.takeIf { it.night == null }?.dayProfileId ?: view.status?.profileId
            val night = state?.plan?.night
            if (state == null || state.override != null || night == null) {
                return PlanBoard(listOf(BoardRow(null, name(single ?: BuiltInProfiles.STOCK.id), Until.Always)), canWrite = canWrite)
            }
            val day = state.plan.dayProfileId
            return if (state.slot == PlanSlot.NIGHT) {
                val end = next(now, night.end)
                PlanBoard(
                    listOf(
                        BoardRow(null, name(night.profileId), Until.UnlockAfter(end.millis())),
                        BoardRow(end.millis(), name(day), Until.At(next(end, night.start).millis())),
                    ),
                    canWrite = canWrite,
                )
            } else {
                val start = next(now, night.start)
                PlanBoard(
                    listOf(
                        BoardRow(null, name(day), Until.At(start.millis())),
                        BoardRow(start.millis(), name(night.profileId), Until.UnlockAfter(next(start, night.end).millis())),
                    ),
                    canWrite = canWrite,
                )
            }
        }

        /** The next time the clock reads [time] after [from], in [from]'s zone. */
        private fun next(from: ZonedDateTime, time: LocalTime): ZonedDateTime {
            val today = from.with(time).withSecond(0).withNano(0)
            return if (today.isAfter(from)) today else today.plusDays(1)
        }

        private fun ZonedDateTime.millis(): Long = toInstant().toEpochMilli()
    }
}
