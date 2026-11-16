// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.akane599.enforcedoze.design.ColumnHead
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Notice
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.Tone
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.recommend.Note
import io.github.akane599.enforcedoze.recommend.Recommendation
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.LeverCheckIn

/** Night has no fixed window: the owner switches it on from the tile or an automation (ADR 0032). */
internal val Recommendation.byHand: Boolean get() = Note.NIGHT_BY_HAND in notes

/**
 * A plan as the board on Home draws it, before it runs: each slot with its profile and its hours. The day runs
 * from the night's end to its start; a night offered by hand has no hours.
 */
@Composable
internal fun PlanTimetable(plan: Plan, state: PlanState, formats: Formats, byHand: Boolean = false) {
    val night = plan.night
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ed.palette.raised, MaterialTheme.shapes.small)
            .padding(horizontal = Space.l, vertical = Space.s),
    ) {
        val dayHours = night?.let { stringResource(R.string.plan_hours, formats.time(it.end), formats.time(it.start)) }
        SlotLine(stringResource(R.string.plan_slot_day), state.name(plan.dayProfileId), dayHours ?: stringResource(R.string.plan_all_day))
        when {
            night != null -> {
                Rule()
                SlotLine(
                    stringResource(R.string.plan_slot_night),
                    state.name(night.profileId),
                    stringResource(R.string.plan_hours, formats.time(night.start), formats.time(night.end)),
                )
            }

            byHand -> {
                Rule()
                SlotLine(stringResource(R.string.plan_slot_night), state.name(NIGHT), stringResource(R.string.plan_by_hand), quiet = true)
            }
        }
    }
}

@Composable
private fun SlotLine(slot: String, profile: String, hours: String, quiet: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.m).semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            ColumnHead(slot)
            Text(profile, style = MaterialTheme.typography.titleLarge, color = Ed.palette.ink)
        }
        Text(
            hours,
            style = MaterialTheme.typography.titleMedium,
            color = if (quiet) Ed.palette.quiet else Ed.palette.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = Space.m),
        )
    }
}

/** The last apply: a quiet line while it runs, the reason or the raw line when it did not go through (SPEC 9.3). */
@Composable
internal fun OutcomeLine(applying: Boolean, outcome: ActionOutcome?) {
    when (outcome) {
        null -> if (applying) {
            Text(
                stringResource(R.string.plan_applying),
                style = MaterialTheme.typography.bodyMedium,
                color = Ed.palette.quiet,
                modifier = Modifier.padding(top = Space.s),
            )
        }

        ActionOutcome.Done -> Unit

        is ActionOutcome.Applied -> {
            Spacer(Modifier.height(Space.m))
            LeverCheckIn(outcome.checks)
        }

        is ActionOutcome.Unavailable -> {
            Spacer(Modifier.height(Space.m))
            Notice(stringResource(R.string.plan_apply_failed), outcome.reason, tone = Tone.WARNING)
        }

        is ActionOutcome.Failed -> {
            Spacer(Modifier.height(Space.m))
            Notice(stringResource(R.string.plan_apply_failed), outcome.raw, tone = Tone.CRITICAL, raw = true)
        }
    }
}

private const val NIGHT = "night"
