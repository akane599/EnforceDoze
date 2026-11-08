// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design.directions

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalTime

/**
 * The timeline strip as the study draws it: active dotted, light in pinstripes, deep solid, missing hatched,
 * maintenance windows as thin ticks above and below, breaks as notches cut through the band.
 */
@Composable
internal fun StudyStrip(
    style: StripStyle,
    spans: List<Span>,
    origin: LocalTime,
    end: LocalTime,
    modifier: Modifier = Modifier,
    maintenance: List<LocalTime> = emptyList(),
    breaks: List<LocalTime> = emptyList(),
    tickOverhang: Dp = 4.dp,
) {
    Canvas(modifier) {
        val total = StudyNight.minutesFrom(origin, end).toFloat()
        fun x(t: LocalTime) = size.width * StudyNight.minutesFrom(origin, t) / total
        val overhang = tickOverhang.toPx()
        val top = overhang
        val band = size.height - 2 * overhang
        spans.forEach { span ->
            val left = x(span.from)
            val right = x(span.to).coerceAtLeast(left + 1f)
            drawPhase(span.phase, style, Offset(left, top), Size(right - left, band))
        }
        maintenance.forEach { t ->
            val px = x(t)
            drawLine(style.tick, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1.dp.toPx())
        }
        breaks.forEach { t ->
            val px = x(t)
            val w = 3.dp.toPx()
            drawRect(style.ground, Offset(px - w / 2, top), Size(w, band))
            drawLine(style.notch, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1.5.dp.toPx())
        }
    }
}

internal fun DrawScope.drawPhase(phase: Phase, style: StripStyle, at: Offset, size: Size) {
    when (phase) {
        Phase.DEEP -> drawRect(style.deep, at, size)

        Phase.LIGHT -> {
            drawRect(style.light, at, size)
            clipRect(at.x, at.y, at.x + size.width, at.y + size.height) {
                val step = 3.dp.toPx()
                var px = at.x + step / 2
                while (px < at.x + size.width) {
                    drawLine(style.pattern, Offset(px, at.y), Offset(px, at.y + size.height), strokeWidth = 1.dp.toPx())
                    px += step
                }
            }
        }

        Phase.ACTIVE -> {
            drawRect(style.active, at, size)
            clipRect(at.x, at.y, at.x + size.width, at.y + size.height) {
                val step = 4.dp.toPx()
                var py = at.y + step / 2
                while (py < at.y + size.height) {
                    var px = at.x + step / 2
                    while (px < at.x + size.width) {
                        drawCircle(style.pattern, radius = 0.8.dp.toPx(), center = Offset(px, py))
                        px += step
                    }
                    py += step
                }
            }
        }

        Phase.MISSING -> clipRect(at.x, at.y, at.x + size.width, at.y + size.height) {
            drawRect(style.missing.copy(alpha = 0.25f), at, size)
            val step = 5.dp.toPx()
            var px = at.x - size.height
            while (px < at.x + size.width) {
                drawLine(style.missing, Offset(px, at.y + size.height), Offset(px + size.height, at.y), strokeWidth = 1.dp.toPx())
                px += step
            }
        }
    }
}
