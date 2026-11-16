// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import android.content.Context
import io.github.akane599.enforcedoze.app.EngineGraph
import com.akylas.enforcedoze.R
import io.github.akane599.enforcedoze.data.EventRow
import io.github.akane599.enforcedoze.doze.EngineStatus
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.doze.LeverState
import io.github.akane599.enforcedoze.privilege.PrivilegeState
import io.github.akane599.enforcedoze.privilege.Tier
import io.github.akane599.enforcedoze.ui.NoticeKind
import io.github.akane599.enforcedoze.ui.StateNotice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.akane599.enforcedoze.ui.R as UiR

/**
 * The warnings of SPEC 9.3, decided once for every screen (ADR 0029: two screens must not judge "privilege lost"
 * differently). Each notice has a stable id, so "seen" holds until the situation changes.
 */
internal class AppNotices(
    private val context: Context,
    graph: EngineGraph,
    access: EngineAccess,
    private val prefs: AppPrefs,
    private val labels: Labels,
) {
    val notices: Flow<List<StateNotice>> = combine(
        graph.controller.state,
        access.status,
        graph.journal.latestEvents(RECENT_EVENTS),
        prefs.changes,
    ) { privilege, status, events, _ -> build(privilege, status, events, prefs.seenNotices) }.distinctUntilChanged()

    /** T2 and up run the worker that writes every lever (SPEC 4.1); below it the app only reads. */
    fun canWrite(privilege: PrivilegeState): Boolean = privilege.tier >= Tier.T2

    private fun build(privilege: PrivilegeState, status: EngineStatus?, events: List<EventRow>, seen: Set<String>): List<StateNotice> =
        buildList {
            val levers = status?.levers.orEmpty()
            val lost = levers.filterValues { it.state == LeverState.NOT_WRITABLE }.keys +
                (if (status?.forcedWithoutWorker == true) setOf(Lever.FORCE_IDLE) else emptySet()) +
                status?.extrasStuck.orEmpty()
            when {
                // The plan wants levers the tier can no longer write: the rest stays applied (SPEC 9.3, ADR 0015).
                lost.isNotEmpty() -> add(StateNotice("lost-${lost.sorted().joinToString()}", NoticeKind.PRIVILEGE_LOST, names(lost)))
                !canWrite(privilege) -> add(StateNotice("read-only", NoticeKind.READ_ONLY))
            }
            val partly = levers.filterValues { it.state == LeverState.PARTLY }
            if (partly.isNotEmpty()) {
                val raw = partly.entries.joinToString("\n") { (lever, it) ->
                    "$lever: ${it.reason ?: "seen ${it.seen}, wanted ${it.wanted}"}"
                }
                add(StateNotice("partly-${partly.keys.sorted().joinToString()}", NoticeKind.PARTLY_APPLIED, names(partly.keys), raw = raw))
            }
            // Another app wrote the global string: the latest drift with a known writer that is not this app (ADR 0014).
            events.firstOrNull { it.kind == DRIFT && it.writer != null && it.writer != context.packageName && it.time >= recentFrom() }
                ?.let { drift ->
                    val writer = drift.writer.orEmpty()
                    add(StateNotice("another-${drift.time}", NoticeKind.ANOTHER_MANAGER, labels.label(writer) ?: writer, time = drift.time))
                }
            // SPEC 11.5: another Doze manager installed, whether or not it has written yet.
            OtherManagers.installed(labels).takeIf { it.isNotEmpty() }?.let { managers ->
                val id = "two-${managers.joinToString(",") { it.packageName }}"
                add(StateNotice(id, NoticeKind.TWO_MANAGERS, managers.joinToString { it.label }))
            }
            status?.storeError?.let { raw ->
                add(StateNotice("store-error", NoticeKind.ERROR, context.getString(UiR.string.ui_error_store), raw = raw))
            }
        }.map { it.copy(seen = it.id in seen) }

    /** A foreign write older than a day is history in the night reports, not a warning. */
    private fun recentFrom(): Long = System.currentTimeMillis() - DAY_MILLIS

    private fun names(levers: Collection<Lever>): String = levers.sorted().joinToString { context.getString(it.label()) }

    private companion object {
        const val DRIFT = "DRIFT"
        const val RECENT_EVENTS = 200
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
    }
}

/** The lever's name as `:core:ui` words it. */
internal fun Lever.label(): Int = when (this) {
    Lever.CONSTANTS -> UiR.string.ui_lever_constants
    Lever.FORCE_IDLE -> UiR.string.ui_lever_force_idle
    Lever.SENSORS -> UiR.string.ui_lever_sensors
    Lever.SENSOR_PRIVACY -> UiR.string.ui_lever_sensor_privacy
    Lever.ALLOWLIST -> UiR.string.ui_lever_allowlist
    Lever.WIFI -> UiR.string.ui_lever_wifi
    Lever.MOBILE_DATA -> UiR.string.ui_lever_mobile_data
    Lever.APP_SUSPENSION -> UiR.string.ui_lever_app_suspension
}
