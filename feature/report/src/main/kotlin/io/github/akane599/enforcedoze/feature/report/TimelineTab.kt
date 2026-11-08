// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akane599.enforcedoze.design.ColumnHead
import io.github.akane599.enforcedoze.design.DozeFill
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Palette
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.StripLegend
import java.util.concurrent.TimeUnit

/**
 * Timeline: the strip with its key, then the night as a timetable (ADR 0028). The route line runs in the
 * deep-idle fill while the phone is in deep idle and as a hairline otherwise; a stretch with no record is
 * dashed. Deep idle shows when it was due and how late it came.
 */
@Composable
internal fun ColumnScope.TimelineTab(state: ReportState, formats: Formats) {
    ReportStrip(state.summary, formats)
    Spacer(Modifier.height(Space.m))
    StripLegend(
        listOf(
            DozeFill.ACTIVE to stringResource(R.string.report_legend_active),
            DozeFill.LIGHT to stringResource(R.string.report_legend_light),
            DozeFill.DEEP to stringResource(R.string.report_legend_deep),
            DozeFill.MISSING to stringResource(R.string.report_legend_missing),
        ),
    )
    Spacer(Modifier.height(Space.xl))
    BoxWithConstraints {
        val scale = LocalDensity.current.fontScale
        val wide = maxWidth >= 330.dp * scale
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(bottom = Space.s)) {
                ColumnHead(stringResource(R.string.report_col_time), Modifier.width(TIME_WIDTH * scale + ROUTE_WIDTH))
                ColumnHead(stringResource(R.string.report_col_event), Modifier.weight(1f))
                if (wide) {
                    ColumnHead(stringResource(R.string.report_col_due), Modifier.width(DUE_WIDTH * scale), TextAlign.End)
                    ColumnHead(stringResource(R.string.report_col_late), Modifier.width(LATE_WIDTH * scale), TextAlign.End)
                }
            }
            Rule()
            Spacer(Modifier.height(Space.s))
            val legs = legs(state.timetable)
            state.timetable.forEachIndexed { i, stop ->
                StopRow(stop, formats, legs[i] to legs[i + 1], wide)
            }
        }
    }
}

private val TIME_WIDTH = 56.dp
private val ROUTE_WIDTH = 28.dp
private val DUE_WIDTH = 52.dp
private val LATE_WIDTH = 60.dp
private val ROW_MIN = 44.dp
private val TEXT_TOP = 10.dp

/** What the route line shows between two stops. */
private enum class Leg { NONE, AWAKE, DEEP, NO_RECORD }

/** The leg above each stop and below the last one: [stops].size + 1 entries. */
private fun legs(stops: List<Stop>): List<Leg> {
    val out = ArrayList<Leg>(stops.size + 1)
    out += Leg.NONE
    var current = Leg.AWAKE
    stops.forEachIndexed { i, stop ->
        current = when (stop.event) {
            is StopEvent.Deep -> Leg.DEEP
            is StopEvent.Maintenance -> if (current == Leg.NO_RECORD) Leg.AWAKE else current
            is StopEvent.Gap -> Leg.NO_RECORD
            StopEvent.ScreenOn -> Leg.NONE
            else -> Leg.AWAKE
        }
        out += if (i == stops.lastIndex) Leg.NONE else current
    }
    return out
}

@Composable
private fun StopRow(stop: Stop, formats: Formats, legs: Pair<Leg, Leg>, wide: Boolean) {
    val palette = Ed.palette
    val scale = LocalDensity.current.fontScale
    val (above, below) = legs
    val event = eventText(stop.event, formats)
    val lateLate = stop.late != null && stop.late > ALLOWANCE
    val due = stop.due?.let(formats::clock)
    val late = stop.late?.let(formats::lateText)
    val spoken = spokenStop(formats.clock(stop.time), event, due, late)
    val firstLine = with(LocalDensity.current) { MaterialTheme.typography.titleMedium.lineHeight.toDp() }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = ROW_MIN).semantics(mergeDescendants = true) {
            contentDescription = spoken
        },
    ) {
        Text(
            formats.clock(stop.time),
            style = MaterialTheme.typography.titleMedium,
            color = palette.ink,
            maxLines = 1,
            modifier = Modifier.width(TIME_WIDTH * scale).padding(top = TEXT_TOP).clearAndSetSemantics { },
        )
        Canvas(Modifier.width(ROUTE_WIDTH).fillMaxHeight().clearAndSetSemantics { }) {
            val cy = (TEXT_TOP + firstLine / 2).toPx()
            drawLeg(above, palette, 0f, cy)
            drawLeg(below, palette, cy, size.height)
            drawMarker(stop.event, palette, Offset(size.width / 2, cy))
        }
        Column(Modifier.weight(1f).padding(top = TEXT_TOP, bottom = Space.s).clearAndSetSemantics { }) {
            Text(event, style = MaterialTheme.typography.bodyLarge, color = eventColor(stop.event, palette))
            if (!wide && (due != null || late != null)) {
                Text(
                    listOfNotNull(due?.let { stringResource(R.string.report_spoken_due, it) }, late).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (lateLate) palette.warning else palette.quiet,
                )
            }
        }
        if (wide) {
            Text(
                due.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.quiet,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(DUE_WIDTH * scale).padding(top = TEXT_TOP).clearAndSetSemantics { },
            )
            Text(
                late.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (lateLate) palette.warning else palette.ink,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(LATE_WIDTH * scale).padding(top = TEXT_TOP).clearAndSetSemantics { },
            )
        }
    }
}

/** A break is the warning colour; what the phone did on its own is quiet; the rest is ink. */
private fun eventColor(event: StopEvent, palette: Palette): Color = when (event) {
    is StopEvent.Break -> palette.warning
    is StopEvent.Maintenance, is StopEvent.Filled, is StopEvent.Gap -> palette.quiet
    else -> palette.ink
}

/** What TalkBack reads for a row: time, event, then when deep idle was due and how late it came. */
@Composable
private fun spokenStop(time: String, event: String, due: String?, late: String?): String = listOfNotNull(
    time,
    event,
    due?.let { stringResource(R.string.report_spoken_due, it) },
    late?.let { stringResource(R.string.report_spoken_late, it) },
).joinToString(", ")

/** Deep idle may come up to two minutes after it was due and still count as on time (SPEC 8.4). */
private val ALLOWANCE = TimeUnit.MINUTES.toMillis(2)

private fun DrawScope.drawLeg(leg: Leg, palette: Palette, from: Float, to: Float) {
    val x = size.width / 2
    when (leg) {
        Leg.NONE -> Unit

        Leg.AWAKE -> drawLine(palette.hairline, Offset(x, from), Offset(x, to), strokeWidth = 3.dp.toPx())

        Leg.DEEP -> drawLine(palette.deep, Offset(x, from), Offset(x, to), strokeWidth = 3.dp.toPx())

        Leg.NO_RECORD -> drawLine(
            palette.missing,
            Offset(x, from),
            Offset(x, to),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
    }
}

private fun DrawScope.drawMarker(event: StopEvent, palette: Palette, at: Offset) {
    when (event) {
        StopEvent.ScreenOff, StopEvent.ScreenOn, StopEvent.Light -> drawCircle(palette.ink, 5.dp.toPx(), at)

        is StopEvent.Deep -> {
            drawCircle(palette.deep, 7.dp.toPx(), at)
            drawCircle(palette.ground, 3.dp.toPx(), at)
        }

        is StopEvent.Maintenance -> {
            val half = 6.dp.toPx()
            drawLine(palette.ink, Offset(at.x - half, at.y), Offset(at.x + half, at.y), strokeWidth = 1.5.dp.toPx())
        }

        is StopEvent.Break -> {
            val half = 5.dp.toPx()
            drawRect(palette.warning, Offset(at.x - half, at.y - half), Size(half * 2, half * 2))
        }

        is StopEvent.Gap, is StopEvent.Filled -> {
            val half = 5.dp.toPx()
            drawRect(palette.ground, Offset(at.x - half, at.y - half), Size(half * 2, half * 2))
            drawRect(
                markerOutline(event, palette),
                Offset(at.x - half, at.y - half),
                Size(half * 2, half * 2),
                style = Stroke(1.5.dp.toPx()),
            )
        }
    }
}

private fun markerOutline(event: StopEvent, palette: Palette): Color = if (event is StopEvent.Gap) palette.missing else palette.quiet

@Composable
private fun eventText(event: StopEvent, formats: Formats): String = when (event) {
    StopEvent.ScreenOff -> stringResource(R.string.report_stop_screen_off)

    StopEvent.ScreenOn -> stringResource(R.string.report_stop_screen_on)

    StopEvent.Light -> stringResource(R.string.report_stop_light)

    is StopEvent.Deep -> stringResource(if (event.again) R.string.report_stop_deep_again else R.string.report_stop_deep)

    is StopEvent.Maintenance -> event.lasted?.let { stringResource(R.string.report_stop_maintenance_for, formats.duration(it)) }
        ?: stringResource(R.string.report_stop_maintenance)

    is StopEvent.Break -> breakText(event, formats)

    is StopEvent.Gap -> stringResource(R.string.report_stop_gap, formats.clock(event.until))

    is StopEvent.Filled -> stringResource(R.string.report_stop_filled, formats.clock(event.until))
}

@Composable
private fun breakText(event: StopEvent.Break, formats: Formats): String {
    val subject = event.subject ?: stringResource(R.string.report_someone)
    val what = when (event.reason) {
        BreakReason.MOTION -> stringResource(R.string.report_break_motion)
        BreakReason.HOLD_OFF -> stringResource(R.string.report_break_hold_off, subject)
        BreakReason.WAKER -> stringResource(R.string.report_break_waker, subject)
        BreakReason.ANOTHER_MANAGER -> stringResource(R.string.report_break_another, subject)
        BreakReason.UNKNOWN -> stringResource(R.string.report_break_unknown)
    }
    return event.awake?.let { stringResource(R.string.report_break_awake, what, formats.duration(it)) } ?: what
}
