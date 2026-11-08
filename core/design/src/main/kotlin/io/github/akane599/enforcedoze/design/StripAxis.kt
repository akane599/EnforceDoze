// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
public data class AxisLabel(val at: Long, val text: String)

/**
 * Time labels under a strip, each centred on its true position; a label that would overlap the one before
 * it is dropped, except the last, which wins over its neighbour so the window's end always shows.
 */
@Composable
public fun StripAxis(labels: List<AxisLabel>, axisStart: Long, axisEnd: Long, modifier: Modifier = Modifier) {
    val quiet = Ed.palette.quiet
    Layout(
        modifier = modifier.fillMaxWidth().padding(top = Space.xs).clearAndSetSemantics { },
        content = { labels.forEach { Text(it.text, style = MaterialTheme.typography.labelSmall, color = quiet, maxLines = 1) } },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val span = (axisEnd - axisStart).coerceAtLeast(1L).toFloat()
        val lefts = labels.mapIndexed { i, label ->
            val center = width * (label.at - axisStart) / span
            (center - placeables[i].width / 2f).toInt().coerceIn(0, (width - placeables[i].width).coerceAtLeast(0))
        }
        val shown = ArrayList<Int>()
        labels.indices.forEach { i ->
            val previous = shown.lastOrNull()
            val overlaps = previous != null && lefts[i] < lefts[previous] + placeables[previous].width + 6.dp.roundToPx()
            when {
                !overlaps -> shown += i

                i == labels.lastIndex && shown.size > 1 -> {
                    shown.removeAt(shown.lastIndex)
                    shown += i
                }
            }
        }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) { shown.forEach { placeables[it].place(lefts[it], 0) } }
    }
}

@Immutable
public data class StackedRow(
    val label: String,
    val model: StripModel,
    val value: String,
    val description: String,
    val emphasis: Boolean = false,
    val valueTone: Tone = Tone.NEUTRAL,
)

/**
 * Nights compared on one clock axis (SPEC 10.1 "stacked for comparisons"; ADR 0028): a visible hour grid
 * aligns every strip, its label and its value, so a bad night stands out from the week.
 */
@Composable
public fun StackedStrips(
    rows: List<StackedRow>,
    axisStart: Long,
    axisEnd: Long,
    gridLines: List<Long>,
    axisLabels: List<AxisLabel>,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 52.dp,
    valueWidth: Dp = 56.dp,
) {
    val palette = Ed.palette
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(labelWidth))
            StripAxis(axisLabels, axisStart, axisEnd, Modifier.weight(1f))
            Box(Modifier.width(valueWidth))
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.label,
                    style = if (row.emphasis) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                    color = if (row.emphasis) palette.ink else palette.quiet,
                    maxLines = 1,
                    modifier = Modifier.width(labelWidth),
                )
                Box(Modifier.weight(1f).height(if (row.emphasis) Sizes.stripSmall else Sizes.stripRow + 4.dp)) {
                    Canvas(Modifier.matchParentSize()) {
                        val span = (axisEnd - axisStart).coerceAtLeast(1L).toFloat()
                        gridLines.forEach { t ->
                            val x = size.width * (t - axisStart) / span
                            drawLine(palette.hairline, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                        }
                    }
                    TimelineStrip(
                        row.model,
                        row.description,
                        Modifier.matchParentSize(),
                        axisStart = axisStart,
                        axisEnd = axisEnd,
                        overhang = 2.dp,
                        animate = false,
                    )
                }
                Text(
                    row.value,
                    style = if (row.emphasis) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                    color = row.valueTone.color(if (row.emphasis) palette.ink else palette.quiet),
                    fontWeight = if (row.valueTone == Tone.NEUTRAL) null else FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(valueWidth),
                )
            }
        }
    }
}

/** The strip's key: one swatch per state with its pattern, for the report's Timeline tab. */
@Composable
public fun StripLegend(entries: List<Pair<DozeFill, String>>, modifier: Modifier = Modifier) {
    val palette = Ed.palette
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.l), verticalAlignment = Alignment.CenterVertically) {
        entries.forEach { (fill, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(18.dp).height(12.dp)) { drawFill(fill, palette, Offset.Zero, size) }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.quiet,
                    modifier = Modifier.padding(start = Space.xs),
                )
            }
        }
    }
}
