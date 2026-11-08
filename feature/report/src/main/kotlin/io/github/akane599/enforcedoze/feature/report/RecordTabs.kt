// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akane599.enforcedoze.design.ColumnHead
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.RawLine
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.SectionHeader
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.ui.title

/** Wakers (SPEC 8.5 item 4): apps by wakeups with the time they held the phone awake, then kernel reasons. */
@Composable
internal fun ColumnScope.WakersTab(state: ReportState, formats: Formats) {
    SectionHeader(stringResource(R.string.report_apps), first = true)
    val apps = state.apps.sortedWith(
        compareByDescending<AppWaker> {
            it.self
        }.thenByDescending { it.wakeups }.thenByDescending { it.wakelock },
    )
    if (apps.isEmpty()) {
        Empty(stringResource(R.string.report_no_wakers))
    } else {
        BoxWithConstraints {
            val scale = LocalDensity.current.fontScale
            val wide = maxWidth >= 340.dp * scale
            Column {
                if (wide) {
                    Row(Modifier.fillMaxWidth().padding(bottom = Space.s)) {
                        ColumnHead(stringResource(R.string.report_col_app), Modifier.weight(1f))
                        ColumnHead(stringResource(R.string.report_col_wakeups), Modifier.width(NUMBER_WIDTH * scale), TextAlign.End)
                        ColumnHead(stringResource(R.string.report_col_awake), Modifier.width(NUMBER_WIDTH * scale), TextAlign.End)
                    }
                }
                Rule()
                apps.forEach { app ->
                    AppRow(app, formats, wide)
                    Rule()
                }
            }
        }
    }
    SectionHeader(stringResource(R.string.report_kernel))
    if (state.kernel.isEmpty()) {
        Empty(stringResource(R.string.report_no_kernel))
    } else {
        Rule()
        state.kernel.sortedByDescending { it.count }.forEach { waker ->
            Row(Modifier.fillMaxWidth().padding(vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text(waker.reason, style = RawLine, color = Ed.palette.ink, modifier = Modifier.weight(1f))
                Text(
                    waker.count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ed.palette.ink,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = Space.m),
                )
            }
            Rule()
        }
    }
}

private val NUMBER_WIDTH = 76.dp

@Composable
private fun AppRow(app: AppWaker, formats: Formats, wide: Boolean) {
    val palette = Ed.palette
    // The app's own wakeups are expected to be 0 (SPEC 8.2); any other count is flagged.
    val selfFlag = app.self && app.wakeups > 0
    val name = if (app.self) stringResource(R.string.report_self, app.label) else app.label
    val wakeups = pluralStringResource(R.plurals.report_wakeups, app.wakeups, app.wakeups)
    val awake = stringResource(R.string.report_awake_for, formats.duration(app.wakelock))
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.m).semantics(mergeDescendants = true) {
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            Text(
                app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = palette.quiet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!wide) {
                Text(
                    "$wakeups · $awake",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selfFlag) palette.warning else palette.quiet,
                )
            }
            if (app.self) {
                Text(stringResource(R.string.report_self_expected), style = MaterialTheme.typography.labelMedium, color = palette.quiet)
            }
        }
        if (wide) {
            val scale = LocalDensity.current.fontScale
            Text(
                app.wakeups.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (selfFlag) palette.warning else palette.ink,
                fontWeight = if (selfFlag) FontWeight.SemiBold else null,
                textAlign = TextAlign.End,
                modifier = Modifier.width(NUMBER_WIDTH * scale),
            )
            Text(
                formats.duration(app.wakelock),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.quiet,
                textAlign = TextAlign.End,
                modifier = Modifier.width(NUMBER_WIDTH * scale),
            )
        }
    }
}

/** Sensors: apps that held a sensor open, its rate, and when the snapshots saw it (SPEC 8.5 item 4). */
@Composable
internal fun ColumnScope.SensorsTab(state: ReportState) {
    SectionHeader(stringResource(R.string.report_sensors), first = true, detail = stringResource(R.string.report_sensors_detail))
    if (state.sensors.isEmpty()) {
        Empty(stringResource(R.string.report_no_sensors))
        return
    }
    Rule()
    state.sensors.forEach { hold ->
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m).semantics(mergeDescendants = true) { }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hold.label, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink, modifier = Modifier.weight(1f))
                hold.rateHz?.let {
                    Text(
                        stringResource(R.string.report_rate, it.toInt()),
                        style = MaterialTheme.typography.titleMedium,
                        color = Ed.palette.ink,
                    )
                }
            }
            Text(hold.sensor, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
            if (hold.moments.isNotEmpty()) {
                Text(
                    stringResource(R.string.report_seen_at, hold.moments.map { stringResource(it.label) }.joinToString(", ")),
                    style = MaterialTheme.typography.labelMedium,
                    color = Ed.palette.quiet,
                )
            }
        }
        Rule()
    }
}

/** Actions: what changed Doze in the night (SPEC 8.5 item 5), then every write EnforceDoze made, with its read-back. */
@Composable
internal fun ColumnScope.ActionsTab(state: ReportState, formats: Formats) {
    Changes(state.changes, formats)
    EngineActions(state.actions, formats)
}

@Composable
private fun ColumnScope.Changes(changes: List<DozeChange>, formats: Formats) {
    SectionHeader(stringResource(R.string.report_changes), first = true)
    if (changes.isEmpty()) {
        Empty(stringResource(R.string.report_no_changes))
    } else {
        Rule()
        changes.sortedBy { it.time }.forEach { change ->
            TimedRow(formats.clock(change.time)) {
                when (change) {
                    is DozeChange.HoldOff -> Text(
                        change.until?.let { stringResource(R.string.report_change_hold_off_until, change.name, formats.clock(it)) }
                            ?: stringResource(R.string.report_change_hold_off, change.name),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ed.palette.ink,
                    )

                    is DozeChange.Drift -> {
                        Text(
                            stringResource(R.string.report_change_drift, change.writer ?: stringResource(R.string.report_someone)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ed.palette.warning,
                        )
                        Text("${change.key}: ${change.found ?: "∅"} → ${change.expected ?: "∅"}", style = RawLine, color = Ed.palette.quiet)
                    }
                }
            }
            Rule()
        }
    }
}

@Composable
private fun ColumnScope.EngineActions(actions: List<EngineAction>, formats: Formats) {
    SectionHeader(stringResource(R.string.report_engine_actions))
    if (actions.isEmpty()) {
        Empty(stringResource(R.string.report_no_actions))
        return
    }
    Rule()
    actions.sortedBy { it.time }.forEach { action ->
        TimedRow(formats.clock(action.time)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    action.lever.title(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ed.palette.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(if (action.ok) R.string.report_applied else R.string.report_not_applied),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (action.ok) Ed.palette.good else Ed.palette.critical,
                )
            }
            Text(action.command, style = RawLine, color = Ed.palette.quiet)
            action.readBack?.let { Text(stringResource(R.string.report_read_back, it), style = RawLine, color = Ed.palette.quiet) }
            action.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet) }
        }
        Rule()
    }
}

@Composable
private fun TimedRow(time: String, content: @Composable ColumnScope.() -> Unit) {
    val scale = LocalDensity.current.fontScale
    Row(Modifier.fillMaxWidth().padding(vertical = Space.m).semantics(mergeDescendants = true) { }) {
        Text(time, style = MaterialTheme.typography.titleMedium, color = Ed.palette.ink, modifier = Modifier.width(56.dp * scale))
        Column(Modifier.weight(1f), content = content)
    }
}

/** Raw: the records the verdict was judged on, exactly as stored, selectable for copying. */
@Composable
internal fun ColumnScope.RawTab(state: ReportState) {
    if (state.raw.isEmpty()) {
        SectionHeader(stringResource(R.string.report_tab_raw), first = true)
        Empty(stringResource(R.string.report_no_raw))
        return
    }
    state.raw.forEachIndexed { i, group ->
        SectionHeader(stringResource(rawTitle(group.title)), first = i == 0)
        SelectionContainer {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Ed.palette.raised, MaterialTheme.shapes.small)
                    .padding(Space.m),
            ) {
                group.lines.forEach { Text(it, style = RawLine, color = Ed.palette.ink) }
            }
        }
    }
}

private val SensorMoment.label: Int
    get() = when (this) {
        SensorMoment.SCREEN_OFF -> R.string.report_moment_screen_off
        SensorMoment.MAINTENANCE -> R.string.report_moment_maintenance
        SensorMoment.SCREEN_ON -> R.string.report_moment_screen_on
    }

private fun rawTitle(title: RawTitle): Int = when (title) {
    RawTitle.TIMELINE -> R.string.report_raw_timeline
    RawTitle.TRANSITIONS -> R.string.report_raw_transitions
    RawTitle.BREAKS -> R.string.report_raw_breaks
    RawTitle.COVERAGE -> R.string.report_raw_coverage
    RawTitle.NOTES -> R.string.report_raw_notes
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet, modifier = Modifier.padding(vertical = Space.s))
}
