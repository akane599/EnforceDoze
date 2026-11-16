// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.Motion
import io.github.akane599.enforcedoze.design.RawLine
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.Tone
import io.github.akane599.enforcedoze.design.color

/** A lever's state straight after an apply, as the engine read it back from the phone (SPEC 5.2, 10.3). */
public enum class CheckState { APPLIED, ARMED, HELD_OFF, PARTLY, NOT_WRITABLE, WAITING, UNKNOWN, GUARDED }

/** One lever after an apply: its name, its state, and the value read back (or why there is none). */
@Immutable
public data class LeverCheck(val lever: String, val state: CheckState, val readBack: String?)

/**
 * SPEC 10.3: applying a profile shows each lever checking in with its read-back result. The rows arrive one after
 * another, at the system's animator speed; with animations off they are all there at once.
 */
@Composable
public fun LeverCheckIn(checks: List<LeverCheck>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scale = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
    Column(modifier.fillMaxWidth()) {
        Rule()
        checks.forEachIndexed { i, check ->
            val alpha = remember(checks) { Animatable(if (scale == 0f) 1f else 0f) }
            LaunchedEffect(checks) {
                if (scale != 0f) {
                    alpha.animateTo(1f, tween((Motion.SHORT_MS * scale).toInt(), delayMillis = (i * Motion.SHORT_MS * scale).toInt()))
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { this.alpha = alpha.value }
                    .padding(vertical = Space.s)
                    .semantics(mergeDescendants = true) { },
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(check.lever, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(check.state.word()),
                        style = MaterialTheme.typography.titleSmall,
                        color = check.state.tone().color(neutral = Ed.palette.ink),
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = Space.m),
                    )
                }
                check.readBack?.let {
                    Text(it, style = RawLine, color = Ed.palette.quiet, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Rule()
        }
    }
}

private fun CheckState.word(): Int = when (this) {
    CheckState.APPLIED -> R.string.ui_check_applied
    CheckState.ARMED -> R.string.ui_check_armed
    CheckState.HELD_OFF -> R.string.ui_check_held_off
    CheckState.PARTLY -> R.string.ui_check_partly
    CheckState.NOT_WRITABLE -> R.string.ui_check_not_writable
    CheckState.WAITING -> R.string.ui_check_waiting
    CheckState.UNKNOWN -> R.string.ui_check_unknown
    CheckState.GUARDED -> R.string.ui_check_guarded
}

private fun CheckState.tone(): Tone = when (this) {
    CheckState.APPLIED, CheckState.ARMED, CheckState.HELD_OFF -> Tone.NEUTRAL
    CheckState.PARTLY, CheckState.WAITING, CheckState.UNKNOWN, CheckState.GUARDED -> Tone.WARNING
    CheckState.NOT_WRITABLE -> Tone.CRITICAL
}
