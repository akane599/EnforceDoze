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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

// Direction B, "the sleep lab": the night is a hypnogram, the stepped trace a sleep technician scores. Calm
// clinical teal, a hyperlegible face, the trace above the band as the one signature move.

internal val HypnogramPalette = StudyPalette(
    ground = oklch(0.160, 0.012, 180.0),
    raised = oklch(0.205, 0.015, 180.0),
    hairline = oklch(0.310, 0.020, 180.0),
    ink = oklch(0.950, 0.008, 180.0),
    quiet = oklch(0.740, 0.020, 180.0),
    accent = oklch(0.820, 0.100, 200.0),
    onAccent = oklch(0.160, 0.012, 180.0),
    good = oklch(0.820, 0.120, 160.0),
    warning = oklch(0.780, 0.130, 70.0),
    critical = oklch(0.680, 0.180, 25.0),
    strip = StripStyle(
        active = oklch(0.330, 0.020, 180.0),
        light = oklch(0.560, 0.070, 200.0),
        deep = oklch(0.700, 0.110, 205.0),
        pattern = oklch(0.160, 0.012, 180.0, alpha = 0.5f),
        tick = oklch(0.950, 0.008, 180.0),
        notch = oklch(0.780, 0.130, 70.0),
        missing = oklch(0.740, 0.020, 180.0),
        ground = oklch(0.160, 0.012, 180.0),
    ),
)

private val HypnogramType = studyTypography(StudyFonts.atkinson, display = 44.sp)

@Composable
internal fun HypnogramHome() {
    val p = HypnogramPalette
    StudyScreen(p, HypnogramType, "EnforceDoze") {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Last night", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("23:41 – 06:58", style = MaterialTheme.typography.bodyMedium, color = p.quiet)
        }
        Spacer(Modifier.height(6.dp))
        Text("Held", style = MaterialTheme.typography.displayMedium, color = p.good)
        Text("6 h 16 min in deep idle, 0.40 % an hour", style = MaterialTheme.typography.titleMedium, color = p.ink)
        Spacer(Modifier.height(18.dp))
        Hypnogram(p, height = 104.dp)
        HourScale(p, listOf("23:41", "02:00", "04:00", "06:58"), start = 52.dp)
        Spacer(Modifier.height(18.dp))
        Facts(
            p,
            listOf(
                "Deep idle began" to "00:24, 1 min after due",
                "Maintenance windows" to "5",
                "Woken by apps" to "0 times",
            ),
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = {}) { Text("Open report") }
            OutlinedButton(onClick = {}) { Text("Quick proof") }
        }
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tonight", style = MaterialTheme.typography.titleLarge)
                Text("Night from 23:00 until the first unlock after 07:30", style = MaterialTheme.typography.bodyMedium, color = p.quiet)
            }
            TextButton(onClick = {}) { Text("Change") }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().background(p.raised, RoundedCornerShape(12.dp)).padding(16.dp)) {
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
internal fun HypnogramReport() {
    val p = HypnogramPalette
    StudyScreen(p, HypnogramType, "Night of 7 November", back = true) {
        Text("Doze held", style = MaterialTheme.typography.displayMedium, color = p.good)
        Text("6 h 16 min in deep idle, 5 maintenance windows, 0.40 % an hour.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        Hypnogram(p, height = 150.dp)
        HourScale(p, listOf("23:41", "01:00", "03:00", "05:00", "06:58"), start = 52.dp)
        Spacer(Modifier.height(24.dp))
        Text("What happened", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        listOf(
            "23:41" to "Screen off, unplugged at 64 %",
            "00:05" to "Light idle",
            "00:24" to "Deep idle, due 00:23",
            "03:40" to "Motion woke the phone for 4 min",
            "03:58" to "Deep idle again",
            "06:58" to "Screen on at 61 %",
        ).forEach { (time, event) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(time, style = MaterialTheme.typography.bodyLarge, color = p.quiet, modifier = Modifier.width(64.dp))
                Text(event, style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(color = p.hairline)
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

/** The stepped trace: awake on top, deep at the bottom, maintenance windows as short climbs to light. */
@Composable
private fun Hypnogram(p: StudyPalette, height: Dp) {
    val levels = listOf("Awake" to Phase.ACTIVE, "Light" to Phase.LIGHT, "Deep" to Phase.DEEP)
    Column {
        Row(Modifier.fillMaxWidth().height(height)) {
            Column(Modifier.width(52.dp).height(height), verticalArrangement = Arrangement.SpaceAround) {
                levels.forEach { (label, _) -> Text(label, style = MaterialTheme.typography.labelMedium, color = p.quiet) }
            }
            Canvas(Modifier.weight(1f).height(height)) {
                val total = StudyNight.minutesFrom(StudyNight.screenOff, StudyNight.screenOn).toFloat()
                fun x(t: LocalTime) = size.width * StudyNight.minutesFrom(StudyNight.screenOff, t) / total
                fun y(phase: Phase) = size.height * when (phase) {
                    Phase.ACTIVE -> 1f / 6
                    Phase.LIGHT -> 3f / 6
                    else -> 5f / 6
                }
                levels.forEach { (_, phase) ->
                    drawLine(p.hairline, Offset(0f, y(phase)), Offset(size.width, y(phase)), strokeWidth = 1.dp.toPx())
                }
                StudyNight.spans.filter { it.phase == Phase.DEEP }.forEach { span ->
                    drawRect(
                        p.accent.copy(alpha = 0.16f),
                        Offset(x(span.from), y(Phase.DEEP)),
                        Size(
                            x(span.to) - x(span.from),
                            size.height - y(Phase.DEEP),
                        ),
                    )
                }
                val path = Path()
                StudyNight.spans.forEachIndexed { i, span ->
                    if (i == 0) path.moveTo(x(span.from), y(span.phase)) else path.lineTo(x(span.from), y(span.phase))
                    if (span.phase == Phase.DEEP) {
                        StudyNight.maintenance.filter { inside(it, span) }.forEach { m ->
                            val mx = x(m)
                            path.lineTo(mx, y(Phase.DEEP))
                            path.lineTo(mx, y(Phase.LIGHT))
                            path.lineTo(mx + 2.dp.toPx(), y(Phase.LIGHT))
                            path.lineTo(mx + 2.dp.toPx(), y(Phase.DEEP))
                        }
                    }
                    path.lineTo(x(span.to), y(span.phase))
                }
                drawPath(path, p.accent, style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Miter))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Spacer(Modifier.width(52.dp))
            StudyStrip(
                p.strip,
                StudyNight.spans,
                StudyNight.screenOff,
                StudyNight.screenOn,
                Modifier.weight(1f).height(16.dp),
                StudyNight.maintenance,
                StudyNight.breaks.map { it.at },
                tickOverhang = 2.dp,
            )
        }
    }
}

@Composable
private fun HourScale(p: StudyPalette, labels: List<String>, start: Dp) {
    Row(Modifier.fillMaxWidth().padding(start = start, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = p.quiet) }
    }
}

@Composable
private fun Facts(p: StudyPalette, facts: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth()) {
        facts.forEachIndexed { i, (label, value) ->
            if (i > 0) HorizontalDivider(color = p.hairline)
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = p.quiet, modifier = Modifier.weight(1f))
                Box { Text(value, style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}
