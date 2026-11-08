// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size as Box2

/** The Doze states the strip draws, each with its own fill and pattern (SPEC 10.1). */
public enum class DozeFill { ACTIVE, LIGHT, DEEP, MISSING }

@Immutable
public data class StripSegment(val fill: DozeFill, val start: Long, val end: Long)

@Immutable
public data class StripBreak(val at: Long, val label: String)

/**
 * One session on the strip, times in epoch milliseconds. Segments may leave holes: a hole is time the
 * session did not cover (before screen off, after screen on) and stays blank; time the session covered but
 * the record lost is a [DozeFill.MISSING] segment and is hatched.
 */
@Immutable
public data class StripModel(
    val start: Long,
    val end: Long,
    val segments: List<StripSegment>,
    val maintenance: List<Long> = emptyList(),
    val breaks: List<StripBreak> = emptyList(),
) {
    /** The same session moved by [millis], to lay nights from different dates on one clock axis. */
    public fun shiftedBy(millis: Long): StripModel = StripModel(
        start + millis,
        end + millis,
        segments.map { it.copy(start = it.start + millis, end = it.end + millis) },
        maintenance.map { it + millis },
        breaks.map { it.copy(at = it.at + millis) },
    )
}

/**
 * The signature element: one band per session. Active is dotted, light idle pinstriped, deep idle solid,
 * missing data hatched; maintenance windows are thin ticks that overhang the band, breaks are notches cut
 * through it. A 1 dp gap of ground separates segments so neighbouring fills never touch.
 *
 * [axisStart]..[axisEnd] is the span the width stands for (the session's own by default; a shared clock
 * window when strips are stacked). The band draws itself once on first view unless the system turned
 * animations off (SPEC 10.3). [description] is the text summary TalkBack reads (SPEC 10.4).
 */
@Composable
public fun TimelineStrip(
    model: StripModel,
    description: String,
    modifier: Modifier = Modifier,
    axisStart: Long = model.start,
    axisEnd: Long = model.end,
    overhang: Dp = 4.dp,
    animate: Boolean = true,
) {
    val palette = Ed.palette
    val context = LocalContext.current
    val scale = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    var drawn by rememberSaveable { mutableStateOf(!animate || scale == 0f) }
    val reveal = remember { Animatable(if (drawn) 1f else 0f) }
    LaunchedEffect(drawn) {
        if (!drawn) {
            reveal.animateTo(1f, tween((Motion.STRIP_DRAW_MS * scale).toInt(), easing = Motion.Settle))
            drawn = true
        }
    }
    Canvas(modifier.semantics { contentDescription = description }) {
        val span = (axisEnd - axisStart).coerceAtLeast(1L).toFloat()
        fun x(t: Long) = (size.width * (t - axisStart) / span).coerceIn(0f, size.width)
        val hang = overhang.toPx()
        val top = hang
        val band = size.height - 2 * hang
        val gap = 1.dp.toPx()
        clipRect(right = size.width * reveal.value) {
            model.segments.sortedBy { it.start }.forEach { segment ->
                val left = x(segment.start)
                val right = (x(segment.end) - gap).coerceAtLeast(left + 1f)
                drawFill(segment.fill, palette, Offset(left, top), Box2(right - left, band))
            }
            // Ticks are ink where they overhang and ground inside the band, so they read on every fill.
            model.maintenance.forEach { t ->
                val px = x(t)
                val w = 1.dp.toPx()
                drawLine(palette.ink, Offset(px, 0f), Offset(px, top), strokeWidth = w)
                drawLine(palette.ground, Offset(px, top), Offset(px, top + band), strokeWidth = w)
                drawLine(palette.ink, Offset(px, top + band), Offset(px, size.height), strokeWidth = w)
            }
            model.breaks.forEach { brk ->
                val px = x(brk.at)
                val w = 4.dp.toPx()
                drawRect(palette.ground, Offset(px - w / 2, top), Box2(w, band))
                drawLine(palette.ink, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1.5.dp.toPx())
            }
        }
    }
}

/** One state's fill and pattern; used by the strip and by its legend. */
public fun DrawScope.drawFill(fill: DozeFill, palette: Palette, at: Offset, size: Box2) {
    when (fill) {
        DozeFill.DEEP -> drawRect(palette.deep, at, size)

        DozeFill.LIGHT -> {
            drawRect(palette.light, at, size)
            clipRect(at.x, at.y, at.x + size.width, at.y + size.height) {
                val step = 3.dp.toPx()
                var px = at.x + step / 2
                while (px < at.x + size.width) {
                    drawLine(palette.pattern, Offset(px, at.y), Offset(px, at.y + size.height), strokeWidth = 1.dp.toPx())
                    px += step
                }
            }
        }

        DozeFill.ACTIVE -> {
            drawRect(palette.active, at, size)
            clipRect(at.x, at.y, at.x + size.width, at.y + size.height) {
                val step = 4.dp.toPx()
                var py = at.y + step / 2
                while (py < at.y + size.height) {
                    var px = at.x + step / 2
                    while (px < at.x + size.width) {
                        drawCircle(palette.pattern, radius = 0.9.dp.toPx(), center = Offset(px, py))
                        px += step
                    }
                    py += step
                }
            }
        }

        DozeFill.MISSING -> clipRect(at.x, at.y, at.x + size.width, at.y + size.height) {
            val step = 5.dp.toPx()
            var px = at.x - size.height
            while (px < at.x + size.width) {
                drawLine(palette.missing, Offset(px, at.y + size.height), Offset(px + size.height, at.y), strokeWidth = 1.dp.toPx())
                px += step
            }
        }
    }
}
