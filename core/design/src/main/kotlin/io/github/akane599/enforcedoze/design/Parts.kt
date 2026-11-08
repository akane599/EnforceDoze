// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/** Status colours are for verdicts and warnings only (SPEC 10.2). */
public enum class Tone { GOOD, WARNING, CRITICAL, NEUTRAL }

/** The tone's colour, or [neutral] for [Tone.NEUTRAL]. */
@Composable
public fun Tone.color(neutral: Color = Ed.palette.ink): Color = when (this) {
    Tone.GOOD -> Ed.palette.good
    Tone.WARNING -> Ed.palette.warning
    Tone.CRITICAL -> Ed.palette.critical
    Tone.NEUTRAL -> neutral
}

/**
 * A section's heading with optional quiet detail and one text action. More space above than below; the
 * heading carries its own weight (no kicker above it).
 */
@Composable
public fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
    first: Boolean = false,
) {
    Row(
        modifier.fillMaxWidth().padding(top = if (first) 0.dp else Space.xl, bottom = Space.xs).heightIn(min = Sizes.touch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet, textAlign = TextAlign.End) }
        action?.let { TextButton(onClick = onAction) { Text(it) } }
    }
}

/** A quiet column head over ruled timetable columns. */
@Composable
public fun ColumnHead(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Ed.palette.quiet, textAlign = align, modifier = modifier, maxLines = 1)
}

/** A hairline rule between timetable rows. */
@Composable
public fun Rule(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = Space.hair, color = Ed.palette.hairline)
}

/** Where a settling verdict starts, in dp above its place. */
private const val SETTLE_FROM_DP = -6f

/**
 * The verdict word at display size in its status colour, with its one-line reason. With [settle] the word
 * settles into place once, the only celebration in the app (SPEC 10.5 polish): it starts visible and
 * eases down 6 dp, so a disabled animation loses nothing.
 */

@Composable
public fun Verdict(
    word: String,
    reason: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    large: Boolean = true,
    settle: Boolean = false,
) {
    val offset = remember { Animatable(if (settle) SETTLE_FROM_DP else 0f) }
    LaunchedEffect(settle) { if (settle) offset.animateTo(0f, tween(Motion.VERDICT_SETTLE_MS, easing = Motion.Settle)) }
    val density = LocalDensity.current
    Column(modifier.semantics(mergeDescendants = true) { }) {
        Text(
            word,
            style = if (large) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
            color = tone.color(),
            modifier = Modifier.graphicsLayer { translationY = with(density) { offset.value.dp.toPx() } },
        )
        Text(reason, style = MaterialTheme.typography.titleMedium, color = Ed.palette.ink)
    }
}

/** One figure: the number in ink, its units smaller and quieter, before or after it as the language sets. */
@Immutable
public data class Figure(val value: String, val label: String, val before: String? = null, val after: String? = null) {
    /** The figure as plain text, for sentences and TalkBack. */
    public fun plain(): String = buildString {
        before?.let { append(it) }
        append(value)
        after?.let {
            if (it.first().isLetter() || it.startsWith("%")) append(NARROW_NBSP)
            append(it.unbroken())
        }
    }
}

/** The smallest a figure steps down to before it would clip. */
private const val MIN_FIGURE_SCALE = 0.6f

/** A narrow no-break space: "0.40 %/h" keeps its unit on the number's line. */
private const val NARROW_NBSP = '\u202F'

/** A unit never breaks after its slash ("%/h", "/sa"): a word joiner follows each one. */
private fun String.unbroken(): String = replace("/", "/\u2060")

/** The figure's text with its units set at 70 % in quiet ink (SPEC 10.1). */
@Composable
public fun Figure.text(): AnnotatedString {
    val quiet = Ed.palette.quiet
    return buildAnnotatedString {
        val unitStyle = SpanStyle(fontSize = 0.7.em, color = quiet)
        before?.let { withStyle(unitStyle) { append(it) } }
        append(value)
        after?.let { unit ->
            withStyle(unitStyle) {
                if (unit.first().isLetter() || unit.startsWith("%")) append(NARROW_NBSP)
                append(unit.unbroken())
            }
        }
    }
}

/**
 * Key numbers as one ruled timetable row (not tiles): rules above and below, hairline column rules
 * between. Columns stay at least 80 dp wide (narrow screens, large fonts) by breaking into balanced rows.
 */
@Composable
public fun FigureRow(figures: List<Figure>, modifier: Modifier = Modifier) {
    val palette = Ed.palette
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scale = LocalDensity.current.fontScale
        // As many columns of at least 80 dp as fit, then balanced over the rows that takes.
        val fit = (maxWidth / (80.dp * scale)).toInt().coerceIn(1, figures.size.coerceAtLeast(1))
        val rows = (figures.size + fit - 1) / fit
        val perRow = ((figures.size + rows - 1) / rows.coerceAtLeast(1)).coerceAtLeast(1)
        Column(Modifier.fillMaxWidth()) {
            Rule()
            figures.chunked(perRow).forEachIndexed { r, chunk ->
                if (r > 0) Rule()
                Row(Modifier.fillMaxWidth()) {
                    chunk.forEachIndexed { i, figure ->
                        if (i > 0) Box(Modifier.width(Space.hair).height(64.dp * scale).background(palette.hairline))
                        Column(
                            Modifier.weight(1f).semantics(mergeDescendants = true) {
                            }.padding(horizontal = Space.m, vertical = Space.m),
                        ) {
                            // A long figure ("%0,40/sa" at font 2.0) steps down in size rather than clip its unit,
                            // and sits on the same line bottom as its neighbours.
                            val style = MaterialTheme.typography.titleLarge
                            val line = with(LocalDensity.current) { style.lineHeight.toDp() }
                            Box(Modifier.height(line), contentAlignment = Alignment.BottomStart) {
                                BasicText(
                                    figure.text(),
                                    style = style,
                                    color = { palette.ink },
                                    maxLines = 1,
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = style.fontSize * MIN_FIGURE_SCALE,
                                        maxFontSize = style.fontSize,
                                    ),
                                )
                            }
                            Text(figure.label, style = MaterialTheme.typography.labelMedium, color = palette.quiet, maxLines = 2)
                        }
                    }
                    repeat(perRow - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Rule()
        }
    }
}

/**
 * A warning or note. A warning keeps its alert colour, as a 1 dp outline in its tone, until the owner has
 * seen it ([seen]); afterwards it sits quietly on the raised surface (ADR 0028, the gate-board raise).
 */
@Composable
public fun Notice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.WARNING,
    seen: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {},
    raw: Boolean = false,
) {
    val palette = Ed.palette
    val color = tone.color(palette.quiet)
    val icon = when (tone) {
        Tone.CRITICAL -> Icons.Outlined.ErrorOutline
        Tone.WARNING -> Icons.Outlined.WarningAmber
        else -> Icons.Outlined.Info
    }
    val shape = MaterialTheme.shapes.small
    Column(
        modifier
            .fillMaxWidth()
            .background(palette.raised, shape)
            .then(if (!seen && tone != Tone.NEUTRAL) Modifier.border(BorderStroke(Space.hair, color), shape) else Modifier)
            .padding(start = Space.l, end = Space.l, top = Space.l, bottom = if (action == null) Space.l else Space.xs),
    ) {
        Row(Modifier.semantics(mergeDescendants = true) { }) {
            Icon(icon, contentDescription = null, tint = if (seen) palette.quiet else color, modifier = Modifier.size(Sizes.icon))
            Spacer(Modifier.width(Space.m))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = palette.ink)
                Text(body, style = if (raw) RawLine else MaterialTheme.typography.bodyMedium, color = palette.quiet)
            }
        }
        action?.let {
            FlowRow(Modifier.fillMaxWidth().padding(start = Sizes.icon + Space.m - Space.m)) {
                TextButton(onClick = onAction) { Text(it) }
            }
        }
    }
}

/** A screen's scrolling column with the gutter and the large-screen measure (SPEC 9). */
@Composable
public fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Space.column).fillMaxWidth().padding(horizontal = Space.gutter), content = content)
    }
}

/**
 * The top app bar every screen uses: aligned with the screen's reading column on wide screens, with an
 * optional back arrow and trailing actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun EdTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = stringResource(R.string.ed_back),
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth().background(Ed.palette.ground).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.widthIn(max = Space.column + Space.gutter * 2),
            navigationIcon = {
                onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = backLabel) } }
            },
            actions = actions,
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Ed.palette.ground, titleContentColor = Ed.palette.ink),
        )
    }
}
