// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akane599.enforcedoze.design.ColumnHead
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Figure
import io.github.akane599.enforcedoze.design.FigureRow
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Notice
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.ScreenColumn
import io.github.akane599.enforcedoze.design.SectionHeader
import io.github.akane599.enforcedoze.design.Sizes
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.StripAxis
import io.github.akane599.enforcedoze.design.Tag
import io.github.akane599.enforcedoze.design.TimelineStrip
import io.github.akane599.enforcedoze.design.Tone
import io.github.akane599.enforcedoze.design.Verdict
import io.github.akane599.enforcedoze.design.hourLabels
import io.github.akane599.enforcedoze.design.rememberFormats
import io.github.akane599.enforcedoze.evidence.Cause
import io.github.akane599.enforcedoze.ui.StateNoticeCard
import io.github.akane599.enforcedoze.ui.VerdictWord
import io.github.akane599.enforcedoze.ui.sentence
import io.github.akane599.enforcedoze.ui.tone
import io.github.akane599.enforcedoze.ui.word
import io.github.akane599.enforcedoze.ui.R as UiR

/** Home (SPEC 9.1): the plan board, last night, the two actions, then the warnings. */
@Composable
public fun HomeScreen(state: HomeState, actions: HomeActions, modifier: Modifier = Modifier) {
    val formats = rememberFormats()
    Scaffold(
        modifier = modifier,
        containerColor = Ed.palette.ground,
        topBar = { EdTopBar(stringResource(R.string.home_title)) },
    ) { padding ->
        ScreenColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(
                stringResource(R.string.home_plan),
                first = true,
                action = stringResource(R.string.home_plan_change).takeIf { state.plan.canWrite },
                onAction = actions::changePlan,
            )
            DepartureBoard(state.plan, formats)
            if (state.plan.canWrite) PauseRow(state.plan.paused, actions)
            val night = state.night
            if (night == null) EmptyNight() else LastNight(night, formats)
            Spacer(Modifier.height(Space.xl))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                night?.let {
                    FilledTonalButton(onClick = { actions.openReport(it.sessionId) }) { Text(stringResource(R.string.home_open_report)) }
                }
                OutlinedButton(onClick = actions::quickProof, enabled = state.plan.canWrite && !state.proofRunning) {
                    Text(stringResource(if (state.proofRunning) R.string.home_proof_running else R.string.home_quick_proof))
                }
            }
            if (state.notices.isNotEmpty()) Spacer(Modifier.height(Space.xl))
            state.notices.forEach { notice ->
                StateNoticeCard(notice, formats, onAction = { actions.notice(notice) }, onSeen = { actions.seen(notice) })
                Spacer(Modifier.height(Space.m))
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
}

/** The plan as a departure board: From, Profile, Until; the running slot marked "now" in the accent. */
@Composable
private fun DepartureBoard(plan: PlanBoard, formats: Formats) {
    val palette = Ed.palette
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.raised, MaterialTheme.shapes.small)
            .padding(horizontal = Space.l, vertical = Space.m),
    ) {
        BoxWithConstraints {
            val wide = maxWidth >= 300.dp * LocalDensity.current.fontScale
            Column {
                if (wide) {
                    Row(Modifier.fillMaxWidth().padding(bottom = Space.s)) {
                        ColumnHead(stringResource(R.string.home_col_from), Modifier.width(FROM_WIDTH))
                        ColumnHead(stringResource(R.string.home_col_profile), Modifier.weight(1f))
                        ColumnHead(stringResource(R.string.home_col_until), align = TextAlign.End)
                    }
                }
                plan.rows.forEachIndexed { i, row ->
                    if (wide || i > 0) Rule()
                    BoardLine(row, formats, wide)
                }
            }
        }
        if (plan.paused) {
            Text(
                stringResource(R.string.home_paused_note),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.quiet,
                modifier = Modifier.padding(top = Space.s),
            )
        }
    }
}

private val FROM_WIDTH = 72.dp

@Composable
private fun BoardLine(row: BoardRow, formats: Formats, wide: Boolean) {
    val palette = Ed.palette
    val from: @Composable () -> Unit = {
        if (row.from == null) {
            Tag(stringResource(R.string.home_now))
        } else {
            Text(formats.clock(row.from), style = MaterialTheme.typography.titleLarge, color = palette.ink)
        }
    }
    val until = untilText(row.until, formats)
    if (wide) {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(FROM_WIDTH)) { from() }
            Text(row.profile, style = MaterialTheme.typography.titleLarge, color = palette.ink, modifier = Modifier.weight(1f))
            Text(
                until,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.quiet,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                from()
                Spacer(Modifier.width(Space.m))
                Text(row.profile, style = MaterialTheme.typography.titleLarge, color = palette.ink)
            }
            Text("${stringResource(R.string.home_col_until)}: $until", style = MaterialTheme.typography.bodyLarge, color = palette.quiet)
        }
    }
}

@Composable
private fun untilText(until: Until, formats: Formats): String = when (until) {
    is Until.At -> formats.clock(until.time)
    is Until.UnlockAfter -> stringResource(R.string.home_until_unlock, formats.clock(until.time))
    Until.Resumed -> stringResource(R.string.home_until_resumed)
    Until.Always -> stringResource(R.string.home_until_always)
}

@Composable
private fun PauseRow(paused: Boolean, actions: HomeActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (paused) {
            TextButton(onClick = actions::resume) { Text(stringResource(R.string.home_plan_resume)) }
        } else {
            TextButton(onClick = actions::pause) { Text(stringResource(R.string.home_plan_pause)) }
        }
    }
}

@Composable
private fun LastNight(night: NightSummary, formats: Formats) {
    SectionHeader(
        stringResource(if (night.night) R.string.home_last_night else R.string.home_last_session),
        detail = stringResource(UiR.string.ui_span, formats.clock(night.start), formats.clock(night.end)),
    )
    Verdict(night.verdict.word(), reason(night, formats), night.verdict.tone, settle = night.fresh)
    Spacer(Modifier.height(Space.l))
    val summary = stringResource(
        R.string.home_strip_summary,
        formats.clock(night.start),
        formats.clock(night.end),
        formats.duration(night.timeInDeep),
        pluralStringResource(UiR.plurals.ui_maintenance_count, night.maintenanceWindows, night.maintenanceWindows),
        pluralStringResource(UiR.plurals.ui_break_count, night.strip.breaks.size, night.strip.breaks.size),
    )
    TimelineStrip(night.strip, summary, Modifier.fillMaxWidth().height(Sizes.stripSmall + 8.dp))
    StripAxis(hourLabels(night.start, night.end, formats), night.start, night.end)
    Spacer(Modifier.height(Space.l))
    val unknown = stringResource(UiR.string.ui_unknown)
    FigureRow(
        listOf(
            night.timeToDeep?.let { Figure(formats.duration(it), stringResource(R.string.home_fig_to_deep)) }
                ?: Figure(unknown, stringResource(R.string.home_fig_to_deep)),
            night.deepShare?.let { formats.percent(it, stringResource(R.string.home_fig_deep_share)) }
                ?: Figure(unknown, stringResource(R.string.home_fig_deep_share)),
            night.drainPerHour?.let { formats.drain(it, stringResource(R.string.home_fig_drain)) }
                ?: Figure(unknown, stringResource(R.string.home_fig_drain)),
            Figure(night.ownWakeups?.toString() ?: unknown, stringResource(R.string.home_fig_own)),
        ),
    )
}

@Composable
private fun reason(night: NightSummary, formats: Formats): String {
    val deep = formats.duration(night.timeInDeep)
    return when (night.verdict) {
        VerdictWord.HELD -> stringResource(R.string.home_reason_held, deep)

        VerdictWord.PARTLY -> if (night.breaks == 0 && night.lateBy != null) {
            stringResource(R.string.home_reason_late, formats.duration(night.lateBy), deep)
        } else {
            pluralStringResource(R.plurals.home_reason_partly, night.breaks, night.breaks, deep)
        }

        VerdictWord.DID_NOT_DOZE -> stringResource((night.cause ?: Cause.UNKNOWN).sentence())

        VerdictWord.NOT_JUDGED -> stringResource(UiR.string.ui_not_judged)
    }
}

@Composable
private fun EmptyNight() {
    SectionHeader(stringResource(R.string.home_last_night))
    Notice(stringResource(R.string.home_empty_title), stringResource(R.string.home_empty_body), tone = Tone.NEUTRAL)
}
