// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design.directions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

// Direction C, "the seismograph drum": every night is one line on a helicorder sheet, stacked on clock time,
// so a bad night stands out from the week the way a tremor stands out from the noise. Graphite paper, ink
// green trace, rose for anything that broke the line.

internal val HelicorderPalette = StudyPalette(
    ground = oklch(0.165, 0.004, 140.0),
    raised = oklch(0.205, 0.006, 140.0),
    hairline = oklch(0.310, 0.008, 140.0),
    ink = oklch(0.940, 0.006, 140.0),
    quiet = oklch(0.720, 0.012, 140.0),
    accent = oklch(0.780, 0.130, 150.0),
    onAccent = oklch(0.165, 0.004, 140.0),
    good = oklch(0.800, 0.120, 150.0),
    warning = oklch(0.780, 0.130, 70.0),
    critical = oklch(0.720, 0.140, 15.0),
    strip = StripStyle(
        active = oklch(0.320, 0.010, 140.0),
        light = oklch(0.540, 0.070, 150.0),
        deep = oklch(0.780, 0.130, 150.0),
        pattern = oklch(0.165, 0.004, 140.0, alpha = 0.5f),
        tick = oklch(0.940, 0.006, 140.0),
        notch = oklch(0.720, 0.140, 15.0),
        missing = oklch(0.720, 0.012, 140.0),
        ground = oklch(0.165, 0.004, 140.0),
    ),
)

private val HelicorderType = studyTypography(StudyFonts.overpass, display = 44.sp)

private val sheetFrom = LocalTime.of(22, 0)
private val sheetTo = LocalTime.of(8, 0)

@Composable
internal fun HelicorderHome() {
    val p = HelicorderPalette
    StudyScreen(p, HelicorderType, "EnforceDoze") {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Seven nights", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("deep idle", style = MaterialTheme.typography.labelMedium, color = p.quiet)
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(44.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("22", "00", "02", "04", "06", "08").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = p.quiet)
                }
            }
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.height(4.dp))
        val last = SheetNight("Sun", StudyNight.spans, StudyNight.DEEP_SHARE, StudyNight.maintenance, StudyNight.breaks.map { it.at })
        SheetRow(p, last, latest = true)
        StudyNight.week.forEach { night -> SheetRow(p, SheetNight(night.label, night.spans, night.deepShare), latest = false) }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Held", style = MaterialTheme.typography.displayMedium, color = p.good)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.padding(bottom = 6.dp)) {
                Text("last night, 6 h 16 min in deep idle", style = MaterialTheme.typography.titleMedium)
                Text("0.40 %/h, stock was 0.92 %/h", style = MaterialTheme.typography.bodyMedium, color = p.quiet)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {}) { Text("Open report") }
            OutlinedButton(onClick = {}) { Text("Quick proof") }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Plan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = {}) { Text("Change") }
        }
        PlanLine(p, "Everyday", "now, until 23:00")
        HorizontalDivider(color = p.hairline)
        PlanLine(p, "Night", "23:00 until the first unlock after 07:30")
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().background(p.raised, RoundedCornerShape(8.dp)).padding(14.dp)) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = p.warning, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Tasker wrote Doze settings at 22:14", style = MaterialTheme.typography.titleSmall)
                Text(
                    "EnforceDoze put its own back. Two Doze managers fight each other.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.quiet,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun HelicorderReport() {
    val p = HelicorderPalette
    StudyScreen(p, HelicorderType, "Night of 7 November", back = true) {
        Text("Doze held", style = MaterialTheme.typography.displayMedium, color = p.good)
        Text("6 h 16 min in deep idle, 5 maintenance windows, 0.40 %/h.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        val notes = mapOf(
            23 to "23:41 screen off",
            0 to "00:24 deep, 1 min late",
            1 to "01:12 maintenance",
            2 to "02:21 maintenance",
            3 to "03:40 motion, 4 min",
            4 to "04:51 maintenance",
            5 to "05:47 maintenance",
            6 to "06:58 screen on",
        )
        listOf(23, 0, 1, 2, 3, 4, 5, 6).forEach { hour ->
            val from = LocalTime.of(hour, 0)
            val to = from.plusHours(1)
            val note = notes.getValue(hour)
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("%02d".format(hour), style = MaterialTheme.typography.titleMedium, color = p.quiet, modifier = Modifier.width(36.dp))
                Box(Modifier.width(176.dp).height(22.dp)) {
                    Canvas(Modifier.matchParentSize()) {
                        drawLine(p.hairline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
                    }
                    StudyStrip(
                        p.strip,
                        clip(StudyNight.spans, from, to),
                        from,
                        to,
                        Modifier.matchParentSize(),
                        StudyNight.maintenance.filter { it.hour == hour },
                        StudyNight.breaks.map { it.at }.filter { it.hour == hour },
                        tickOverhang = 2.dp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                val color = if (hour == 3) p.critical else p.ink
                Text(note, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Woke the phone", style = MaterialTheme.typography.titleLarge)
        StudyNight.wakers.forEach { w ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(w.app, style = MaterialTheme.typography.bodyLarge)
                    Text(w.detail, style = MaterialTheme.typography.bodyMedium, color = p.quiet)
                }
                Text("${w.wakeups}", style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private data class SheetNight(
    val label: String,
    val spans: List<Span>,
    val deepShare: Double,
    val maintenance: List<LocalTime> = emptyList(),
    val breaks: List<LocalTime> = emptyList(),
)

/** One night as one line of the drum sheet, on the shared 22:00–08:00 axis, with its deep share at the end. */
@Composable
private fun SheetRow(p: StudyPalette, night: SheetNight, latest: Boolean) {
    val height: Dp = if (latest) 30.dp else 18.dp
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            night.label,
            style = if (latest) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            color = if (latest) p.ink else p.quiet,
            modifier = Modifier.width(44.dp),
        )
        Box(Modifier.weight(1f).height(height)) {
            Canvas(Modifier.matchParentSize()) {
                for (i in 0..5) {
                    val x = size.width * i / 5
                    drawLine(p.hairline, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                }
                drawLine(p.hairline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
            }
            StudyStrip(
                p.strip,
                clip(night.spans, sheetFrom, sheetTo),
                sheetFrom,
                sheetTo,
                Modifier.matchParentSize(),
                night.maintenance,
                night.breaks,
                tickOverhang = 2.dp,
            )
        }
        val shareColor = if (night.deepShare < 0.7) {
            p.critical
        } else if (latest) {
            p.ink
        } else {
            p.quiet
        }
        Text(
            "${(night.deepShare * 100).toInt()} %",
            style = if (latest) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            color = shareColor,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun PlanLine(p: StudyPalette, profile: String, span: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(profile, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(110.dp))
        Text(span, style = MaterialTheme.typography.bodyLarge, color = p.quiet)
    }
}
