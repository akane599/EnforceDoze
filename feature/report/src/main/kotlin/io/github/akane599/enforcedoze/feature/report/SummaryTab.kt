// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import io.github.akane599.enforcedoze.design.Figure
import io.github.akane599.enforcedoze.design.FigureRow
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Notice
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.SectionHeader
import io.github.akane599.enforcedoze.design.Sizes
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.StackedRow
import io.github.akane599.enforcedoze.design.StackedStrips
import io.github.akane599.enforcedoze.design.StripAxis
import io.github.akane599.enforcedoze.design.TimelineStrip
import io.github.akane599.enforcedoze.design.Tone
import io.github.akane599.enforcedoze.design.Verdict
import io.github.akane599.enforcedoze.design.hourLabels
import io.github.akane599.enforcedoze.evidence.Cause
import io.github.akane599.enforcedoze.ui.StateNoticeCard
import io.github.akane599.enforcedoze.ui.VerdictWord
import io.github.akane599.enforcedoze.ui.sentence
import io.github.akane599.enforcedoze.ui.tone
import io.github.akane599.enforcedoze.ui.word
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import io.github.akane599.enforcedoze.ui.R as UiR

/** Summary: the verdict line, the strip, the SPEC 8.4 numbers, the comparison, the top wakers and the actions. */
@Composable
internal fun ColumnScope.SummaryTab(state: ReportState, formats: Formats, actions: ReportActions, showWakers: () -> Unit) {
    val summary = state.summary
    Verdict(summary.verdict.word(), verdictLine(summary, formats), summary.verdict.tone)
    Spacer(Modifier.height(Space.xl))
    ReportStrip(summary, formats)
    Spacer(Modifier.height(Space.l))
    FigureRow(figures(summary, formats))
    state.compared?.let { ComparedSection(it, summary, formats) }
    TopWakers(state, showWakers)
    Spacer(Modifier.height(Space.xl))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        OutlinedButton(onClick = actions::late, enabled = !state.lateReported) {
            Text(stringResource(if (state.lateReported) R.string.report_late_done else R.string.report_late))
        }
        TextButton(onClick = actions::allowlist) { Text(stringResource(R.string.report_allowlist)) }
    }
    if (state.notices.isNotEmpty()) Spacer(Modifier.height(Space.xl))
    state.notices.forEach { notice ->
        StateNoticeCard(notice, formats, onAction = { actions.notice(notice) }, onSeen = { actions.seen(notice) })
        Spacer(Modifier.height(Space.m))
    }
}

/** SPEC 8.5 item 1: "6 h 40 min in deep idle, 7 maintenance windows, 0.21 %/h." or the cause. */
@Composable
private fun verdictLine(summary: ReportSummary, formats: Formats): String {
    val deep = formats.duration(summary.timeInDeep)
    val windows = pluralStringResource(UiR.plurals.ui_maintenance_count, summary.maintenanceWindows, summary.maintenanceWindows)
    val drain = summary.drainPerHour?.let(formats::drainText)
    return when (summary.verdict) {
        VerdictWord.HELD, VerdictWord.PARTLY -> {
            val late = summary.lateBy?.let { stringResource(R.string.report_line_late, formats.duration(it)) }
            val line = if (drain == null) {
                stringResource(R.string.report_line_no_drain, deep, windows)
            } else {
                stringResource(R.string.report_line, deep, windows, drain)
            }
            listOfNotNull(late, line).joinToString(" ")
        }

        VerdictWord.DID_NOT_DOZE -> stringResource((summary.cause ?: Cause.UNKNOWN).sentence())

        VerdictWord.NOT_JUDGED -> summary.note ?: stringResource(UiR.string.ui_not_judged)
    }
}

@Composable
internal fun ReportStrip(summary: ReportSummary, formats: Formats) {
    val description = stringResource(
        R.string.report_strip_summary,
        formats.clock(summary.start),
        formats.clock(summary.end),
        formats.duration(summary.timeInDeep),
        pluralStringResource(UiR.plurals.ui_maintenance_count, summary.maintenanceWindows, summary.maintenanceWindows),
        pluralStringResource(UiR.plurals.ui_break_count, summary.strip.breaks.size, summary.strip.breaks.size),
    )
    TimelineStrip(summary.strip, description, Modifier.fillMaxWidth().height(Sizes.stripLarge + 8.dp))
    StripAxis(hourLabels(summary.start, summary.end, formats), summary.start, summary.end)
}

@Composable
private fun figures(summary: ReportSummary, formats: Formats): List<Figure> {
    val dash = unknown()
    val mah = stringResource(io.github.akane599.enforcedoze.design.R.string.ed_unit_mah)
    fun share(value: Double?, label: String) = value?.let { formats.percent(it, label) } ?: Figure(dash, label)
    val drain = stringResource(R.string.report_fig_drain)
    return listOf(
        Figure(summary.timeToDeep?.let(formats::duration) ?: dash, stringResource(R.string.report_fig_to_deep)),
        share(summary.deepShare, stringResource(R.string.report_fig_deep)),
        share(summary.lightShare, stringResource(R.string.report_fig_light)),
        share(summary.activeShare, stringResource(R.string.report_fig_active)),
        summary.drainPerHour?.let { formats.drain(it, drain) } ?: Figure(dash, drain),
        Figure(
            summary.drainMah?.roundToInt()?.toString() ?: dash,
            stringResource(R.string.report_fig_used),
            after = mah.takeIf {
                summary.drainMah !=
                    null
            },
        ),
        Figure(summary.motionResets.toString(), stringResource(R.string.report_fig_motion)),
        Figure(summary.ownWakeups?.toString() ?: dash, stringResource(R.string.report_fig_own)),
    )
}

/** SPEC 8.5 item 3: this night stacked over the nights before it, then the baselines side by side. */
@Composable
private fun ComparedSection(compared: Compared, summary: ReportSummary, formats: Formats) {
    SectionHeader(stringResource(R.string.report_compared), detail = stringResource(R.string.report_compared_detail))
    val day = TimeUnit.DAYS.toMillis(1)
    // Earlier nights move onto this night's date, so every strip shares one clock axis.
    val aligned = compared.nights.map { night ->
        val shift = ((summary.start - night.start).toDouble() / day).roundToInt() * day
        night to night.strip.shiftedBy(shift)
    }
    val axisStart = aligned.minOf { it.second.start }
    val axisEnd = aligned.maxOf { it.second.end }
    val hour = TimeUnit.HOURS.toMillis(1)
    val grid = generateSequence((axisStart / hour + 1) * hour) { it + hour }.takeWhile { it < axisEnd }.toList()
    val dash = unknown()
    val rows = aligned.mapIndexed { i, (night, strip) ->
        StackedRow(
            label = if (i == 0) stringResource(R.string.report_this_night) else formats.date(night.start, "EEEd"),
            model = strip,
            value = night.drainPerHour?.let { formats.decimal(it, 2) } ?: dash,
            description = stringResource(
                R.string.report_compared_row,
                formats.date(night.start),
                night.verdict.word(),
                night.drainPerHour?.let(formats::drainText) ?: dash,
            ),
            emphasis = i == 0,
            valueTone = if (night.verdict == VerdictWord.HELD ||
                night.verdict == VerdictWord.NOT_JUDGED
            ) {
                Tone.NEUTRAL
            } else {
                night.verdict.tone
            },
        )
    }
    StackedStrips(
        rows,
        axisStart,
        axisEnd,
        grid,
        hourLabels(axisStart, axisEnd, formats, stepHours = 3).drop(1).dropLast(1),
        labelWidth = 64.dp * LocalDensity.current.fontScale.coerceAtMost(MAX_LABEL_SCALE),
    )
    Spacer(Modifier.height(Space.l))
    BaselineTable(compared, summary, formats)
    gainLine(compared.stock, summary, formats)?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.ink, modifier = Modifier.padding(top = Space.m))
    }
    if (compared.stock.nights == 0) {
        Spacer(Modifier.height(Space.m))
        Notice(stringResource(R.string.report_no_stock), stringResource(R.string.report_no_stock_body), tone = Tone.NEUTRAL)
    }
}

/** Drain and deep-idle share: this night, the last nights, Stock. */
@Composable
private fun BaselineTable(compared: Compared, summary: ReportSummary, formats: Formats) {
    val dash = unknown()
    val columns = listOf(
        stringResource(R.string.report_this_night) to Baseline(1, summary.drainPerHour, summary.deepShare),
        pluralStringResource(R.plurals.report_recent, compared.recent.nights, compared.recent.nights) to compared.recent,
        stringResource(R.string.report_stock) to compared.stock,
    )
    BoxWithConstraints {
        val labelWidth = if (maxWidth < 340.dp * LocalDensity.current.fontScale) 0.dp else 88.dp
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(bottom = Space.s)) {
                if (labelWidth > 0.dp) Spacer(Modifier.width(labelWidth))
                columns.forEach { (title, _) -> ColumnHead(title, Modifier.weight(1f), TextAlign.End) }
            }
            Rule()
            // Units sit in the row label, so the cells stay bare numbers that line up.
            val drains = columns.map { (_, base) -> base.drainPerHour?.let { formats.decimal(it, 2) } ?: dash }
            BaselineRow(stringResource(R.string.report_row_drain), labelWidth, drains)
            Rule()
            val shares = columns.map { (_, base) -> base.deepShare?.let { (it * PERCENT).roundToInt().toString() } ?: dash }
            BaselineRow(stringResource(R.string.report_row_deep), labelWidth, shares)
            Rule()
        }
    }
}

@Composable
private fun BaselineRow(label: String, labelWidth: androidx.compose.ui.unit.Dp, values: List<String>) {
    val palette = Ed.palette
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        if (labelWidth == 0.dp) Text(label, style = MaterialTheme.typography.labelMedium, color = palette.quiet)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (labelWidth > 0.dp) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = palette.quiet, modifier = Modifier.width(labelWidth))
            }
            values.forEachIndexed { i, value ->
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (i == 0) palette.ink else palette.quiet,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** "48% less drain than on Stock." once there is a Stock figure to compare with (SPEC 7.5). */
@Composable
private fun gainLine(stock: Baseline, summary: ReportSummary, formats: Formats): String? {
    val base = stock.drainPerHour?.takeIf { it > 0 } ?: return null
    val now = summary.drainPerHour ?: return null
    val change = (base - now) / base
    return if (change >= 0) {
        stringResource(R.string.report_gain_less, formats.percentText(change))
    } else {
        stringResource(R.string.report_gain_more, formats.percentText(-change))
    }
}

/** SPEC 8.4 "top 3 wakers", with the way to the full list. */
@Composable
private fun TopWakers(state: ReportState, showWakers: () -> Unit) {
    val top = state.apps.filterNot { it.self }.sortedByDescending { it.wakeups }.take(TOP_WAKERS)
    SectionHeader(
        stringResource(R.string.report_top_wakers),
        action = stringResource(R.string.report_all).takeIf { state.apps.size > TOP_WAKERS || state.kernel.isNotEmpty() },
        onAction = showWakers,
    )
    if (top.isEmpty()) {
        Text(stringResource(R.string.report_no_wakers), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
        return
    }
    Rule()
    top.forEach { waker ->
        Row(Modifier.fillMaxWidth().padding(vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Text(
                waker.label,
                style = MaterialTheme.typography.bodyLarge,
                color = Ed.palette.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                pluralStringResource(R.plurals.report_wakeups, waker.wakeups, waker.wakeups),
                style = MaterialTheme.typography.bodyMedium,
                color = Ed.palette.quiet,
            )
        }
        Rule()
    }
}

private const val TOP_WAKERS = 3

/** Night labels in the comparison grow with the font up to 1.5×, then the strips keep the width. */
private const val MAX_LABEL_SCALE = 1.5f

private const val PERCENT = 100
