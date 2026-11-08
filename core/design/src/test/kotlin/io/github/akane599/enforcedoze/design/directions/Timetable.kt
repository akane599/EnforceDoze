// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design.directions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Direction A, "the night timetable": a plan is a service with departure times, and a night is a run read
// against its schedule. Rail timetable rhythm: condensed grotesk, tabular columns, departure-poster yellow.

internal val TimetablePalette = StudyPalette(
    ground = oklch(0.155, 0.020, 285.0),
    raised = oklch(0.200, 0.024, 285.0),
    hairline = oklch(0.315, 0.026, 285.0),
    ink = oklch(0.955, 0.008, 285.0),
    quiet = oklch(0.760, 0.020, 285.0),
    accent = oklch(0.880, 0.150, 98.0),
    onAccent = oklch(0.155, 0.020, 285.0),
    good = oklch(0.820, 0.120, 165.0),
    warning = oklch(0.760, 0.140, 52.0),
    critical = oklch(0.680, 0.190, 22.0),
    strip = StripStyle(
        active = oklch(0.300, 0.024, 285.0),
        light = oklch(0.520, 0.090, 98.0),
        deep = oklch(0.880, 0.150, 98.0),
        pattern = oklch(0.155, 0.020, 285.0, alpha = 0.55f),
        tick = oklch(0.955, 0.008, 285.0),
        notch = oklch(0.760, 0.140, 52.0),
        missing = oklch(0.760, 0.020, 285.0),
        ground = oklch(0.155, 0.020, 285.0),
    ),
)

private val TimetableType = studyTypography(StudyFonts.barlow, display = 52.sp, tracking = 0.005)

@Composable
internal fun TimetableHome() {
    val p = TimetablePalette
    StudyScreen(p, TimetableType, "EnforceDoze") {
        SectionHead("Plan", action = "Change")
        DepartureBoard(p)
        Spacer(Modifier.height(28.dp))
        SectionHead("Last night", detail = "Sat 7 – Sun 8 Nov")
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Held", style = MaterialTheme.typography.displayMedium, color = p.good)
            Spacer(Modifier.width(12.dp))
            Text(
                "6 h 16 min in deep idle",
                style = MaterialTheme.typography.titleMedium,
                color = p.ink,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        StudyStrip(
            p.strip,
            StudyNight.spans,
            StudyNight.screenOff,
            StudyNight.screenOn,
            Modifier.fillMaxWidth().height(30.dp),
            StudyNight.maintenance,
            StudyNight.breaks.map { it.at },
        )
        HourScale(p, listOf("23:41", "02:00", "04:00", "06:58"))
        Spacer(Modifier.height(16.dp))
        FigureRow(p, listOf("+1 min" to "to deep idle", "86 %" to "in deep idle", "0.40 %/h" to "drain", "0" to "own wakeups"))
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {}) { Text("Open report") }
            OutlinedButton(onClick = {}) { Text("Quick proof") }
        }
        Spacer(Modifier.height(24.dp))
        Notice(p, "Tasker wrote Doze settings at 22:14", "EnforceDoze put its own back. Two Doze managers fight each other.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun TimetableReport() {
    val p = TimetablePalette
    StudyScreen(p, TimetableType, "Night of 7 November", back = true) {
        Text("Doze held", style = MaterialTheme.typography.displayMedium, color = p.good)
        Text(
            "6 h 16 min in deep idle, 5 maintenance windows, 0.40 %/h.",
            style = MaterialTheme.typography.titleMedium,
            color = p.ink,
        )
        Spacer(Modifier.height(18.dp))
        StudyStrip(
            p.strip,
            StudyNight.spans,
            StudyNight.screenOff,
            StudyNight.screenOn,
            Modifier.fillMaxWidth().height(40.dp),
            StudyNight.maintenance,
            StudyNight.breaks.map { it.at },
        )
        HourScale(p, listOf("23:41", "01:00", "03:00", "05:00", "06:58"))
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            ColumnHead(p, "Time", Modifier.width(56.dp))
            Spacer(Modifier.width(28.dp))
            ColumnHead(p, "Event", Modifier.weight(1f))
            ColumnHead(p, "Due", Modifier.width(52.dp), TextAlign.End)
            ColumnHead(p, "Late", Modifier.width(56.dp), TextAlign.End)
        }
        HorizontalDivider(color = p.hairline)
        val stops = listOf(
            Stop("23:41", "Screen off", null, null, StopKind.STATION),
            Stop("00:05", "Light idle", null, null, StopKind.STATION),
            Stop("00:24", "Deep idle", "00:23", "+1 min", StopKind.MAJOR),
            Stop("01:12", "Maintenance window", null, null, StopKind.TICK),
            Stop("02:21", "Maintenance window", null, null, StopKind.TICK),
            Stop("03:40", "Break: motion, 4 min awake", null, null, StopKind.BREAK),
            Stop("03:58", "Deep idle again", null, null, StopKind.MAJOR),
            Stop("04:51", "Maintenance window", null, null, StopKind.TICK),
            Stop("06:58", "Screen on", null, null, StopKind.STATION),
        )
        stops.forEachIndexed { i, stop -> StopRow(p, stop, first = i == 0, last = i == stops.lastIndex) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHead(title: String, detail: String? = null, action: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        action?.let { TextButton(onClick = {}) { Text(it) } }
    }
}

/** The plan as a departure board: one row per slot, the next switch time in the first column. */
@Composable
private fun DepartureBoard(p: StudyPalette) {
    Column(Modifier.fillMaxWidth().background(p.raised, RoundedCornerShape(6.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            ColumnHead(p, "From", Modifier.width(64.dp))
            ColumnHead(p, "Profile", Modifier.weight(1f))
            ColumnHead(p, "Until", Modifier.width(120.dp), TextAlign.End)
        }
        HorizontalDivider(color = p.hairline)
        BoardRow(p, from = "now", profile = "Everyday", until = "23:00", current = true)
        HorizontalDivider(color = p.hairline)
        BoardRow(p, from = "23:00", profile = "Night", until = "unlock after 07:30", current = false)
    }
}

@Composable
private fun BoardRow(p: StudyPalette, from: String, profile: String, until: String, current: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(64.dp)) {
            if (current) {
                Text(
                    from,
                    style = MaterialTheme.typography.labelLarge,
                    color = p.onAccent,
                    modifier = Modifier.background(p.accent, RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 1.dp),
                )
            } else {
                Text(from, style = MaterialTheme.typography.titleLarge, color = p.ink)
            }
        }
        Text(profile, style = MaterialTheme.typography.titleLarge, color = p.ink, modifier = Modifier.weight(1f))
        Text(
            until,
            style = MaterialTheme.typography.bodyLarge,
            color = p.quiet,
            textAlign = TextAlign.End,
            modifier = Modifier.width(120.dp),
        )
    }
}

@Composable
private fun ColumnHead(p: StudyPalette, text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = p.quiet, textAlign = align, modifier = modifier)
}

@Composable
private fun HourScale(p: StudyPalette, labels: List<String>) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = p.quiet) }
    }
}

/** The four key numbers as one timetable row: figure above, label below, columns ruled by hairlines. */
@Composable
private fun FigureRow(p: StudyPalette, figures: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth().border(1.dp, p.hairline, RoundedCornerShape(6.dp))) {
        figures.forEachIndexed { i, (figure, label) ->
            if (i > 0) Box(Modifier.width(1.dp).height(62.dp).background(p.hairline))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp)) {
                Text(figure, style = MaterialTheme.typography.titleLarge, color = p.ink, maxLines = 1)
                Text(label, style = MaterialTheme.typography.labelSmall, color = p.quiet, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Notice(p: StudyPalette, title: String, body: String) {
    Row(Modifier.fillMaxWidth().background(p.raised, RoundedCornerShape(6.dp)).padding(14.dp)) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = p.warning, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = p.ink)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = p.quiet)
        }
    }
}

private enum class StopKind { STATION, MAJOR, TICK, BREAK }

private data class Stop(val time: String, val event: String, val due: String?, val late: String?, val kind: StopKind)

/** One row of the night's timetable, with the route line drawn down its left side. */
@Composable
private fun StopRow(p: StudyPalette, stop: Stop, first: Boolean, last: Boolean) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stop.time, style = MaterialTheme.typography.titleMedium, color = p.ink, modifier = Modifier.width(56.dp))
        Canvas(Modifier.width(28.dp).height(44.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val line = if (stop.kind == StopKind.TICK || stop.kind == StopKind.MAJOR) p.accent else p.hairline
            drawLine(line, Offset(cx, if (first) cy else 0f), Offset(cx, if (last) cy else size.height), strokeWidth = 3.dp.toPx())
            when (stop.kind) {
                StopKind.STATION -> drawCircle(p.ink, 5.dp.toPx(), Offset(cx, cy))

                StopKind.MAJOR -> {
                    drawCircle(p.accent, 7.dp.toPx(), Offset(cx, cy))
                    drawCircle(p.ground, 3.dp.toPx(), Offset(cx, cy))
                }

                StopKind.TICK -> drawLine(p.ink, Offset(cx - 6.dp.toPx(), cy), Offset(cx + 6.dp.toPx(), cy), strokeWidth = 1.5.dp.toPx())

                StopKind.BREAK -> drawRect(
                    p.warning,
                    Offset(cx - 5.dp.toPx(), cy - 5.dp.toPx()),
                    Size(10.dp.toPx(), 10.dp.toPx()),
                )
            }
        }
        val eventColor = if (stop.kind == StopKind.BREAK) {
            p.warning
        } else if (stop.kind == StopKind.TICK) {
            p.quiet
        } else {
            p.ink
        }
        Text(stop.event, style = MaterialTheme.typography.bodyLarge, color = eventColor, modifier = Modifier.weight(1f), maxLines = 1)
        Text(
            stop.due.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = p.quiet,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
        Text(
            stop.late.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = p.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
    }
}
