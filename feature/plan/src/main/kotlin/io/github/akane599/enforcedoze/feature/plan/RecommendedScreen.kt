// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.ScreenColumn
import io.github.akane599.enforcedoze.design.SectionHeader
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.Tag
import io.github.akane599.enforcedoze.design.rememberFormats
import io.github.akane599.enforcedoze.recommend.Measured
import io.github.akane599.enforcedoze.recommend.Recommendation
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.StateNotice
import io.github.akane599.enforcedoze.ui.StateNoticeCard
import io.github.akane599.enforcedoze.ui.line
import io.github.akane599.enforcedoze.ui.profileShortCost
import io.github.akane599.enforcedoze.ui.sentence
import io.github.akane599.enforcedoze.ui.word

/** How many reasons the why card shows: the strongest two or three (SPEC 7.4). */
private const val WHY_LINES = 3

/**
 * Recommended (SPEC 7.4, 9.1): the plan it proposes as a timetable with its confidence, the why card, what the
 * plan gives up, then every signal with its number and the history range it read.
 */
@Composable
public fun RecommendedScreen(
    state: PlanState,
    actions: RecommendedActions,
    modifier: Modifier = Modifier,
    applying: Boolean = false,
    outcome: ActionOutcome? = null,
) {
    val formats = rememberFormats()
    Scaffold(
        modifier = modifier,
        containerColor = Ed.palette.ground,
        topBar = { EdTopBar(stringResource(R.string.plan_recommended_title), onBack = actions.back) },
    ) { padding ->
        ScreenColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            val recommendation = state.recommendation
            if (recommendation == null) {
                SectionHeader(stringResource(R.string.plan_recommended_none_title), first = true)
                Text(stringResource(R.string.plan_recommended_none), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
            } else {
                Proposal(state, recommendation, actions, applying, outcome)
            }
            state.notices.forEach { notice ->
                Spacer(Modifier.height(Space.m))
                StateNoticeCard(notice, formats, onAction = { actions.notice(notice) }, onSeen = { actions.seen(notice) })
            }
            if (recommendation != null) {
                SectionHeader(stringResource(R.string.plan_why))
                Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    recommendation.reasons.take(WHY_LINES).forEach {
                        Text(it.sentence(formats, state::name), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
                    }
                }
                GiveUp(state, recommendation, formats)
            }
            SectionHeader(stringResource(R.string.plan_signals))
            // The range sits under the heading, not beside it: at 200 % a long date pair would split the heading.
            historyRange(state, formats)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ed.palette.quiet,
                    modifier = Modifier.padding(bottom = Space.s),
                )
            }
            SignalTable(state, recommendation, formats)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = actions::changeUrgency) { Text(stringResource(R.string.plan_change_answer)) }
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun Proposal(
    state: PlanState,
    recommendation: Recommendation,
    actions: RecommendedActions,
    applying: Boolean,
    outcome: ActionOutcome?,
) {
    val formats = rememberFormats()
    Spacer(Modifier.height(Space.s))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Tag(recommendation.confidence.word(), accent = false)
        if (state.recommendedInUse) Tag(stringResource(R.string.plan_in_use), accent = false)
    }
    Spacer(Modifier.height(Space.m))
    PlanTimetable(recommendation.plan, state, formats, byHand = recommendation.byHand)
    recommendation.notes.forEach {
        Text(
            it.line(state::name),
            style = MaterialTheme.typography.bodyMedium,
            color = Ed.palette.quiet,
            modifier = Modifier.padding(top = Space.s),
        )
    }
    Spacer(Modifier.height(Space.l))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        // In use already: the tag above says so, and there is nothing to apply.
        if (!state.recommendedInUse) {
            FilledTonalButton(onClick = actions::apply, enabled = state.canWrite && !applying) { Text(stringResource(R.string.plan_apply)) }
        }
        TextButton(onClick = actions::somethingElse) { Text(stringResource(R.string.plan_something_else)) }
    }
    OutcomeLine(applying, outcome)
}

/** "What you give up": each profile's short cost, the day's first (SPEC 7.4, 6.1). */
@Composable
private fun GiveUp(state: PlanState, recommendation: Recommendation, formats: Formats) {
    val ids = listOfNotNull(recommendation.plan.dayProfileId, recommendation.plan.night?.profileId).distinct()
    val profiles = ids.mapNotNull(state::profile)
    if (profiles.isEmpty()) return
    SectionHeader(stringResource(R.string.plan_give_up))
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        profiles.forEach { profile ->
            Column(Modifier.semantics(mergeDescendants = true) { }) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, color = Ed.palette.ink)
                Text(profileShortCost(profile, formats), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
            }
        }
    }
}

@Composable
private fun historyRange(state: PlanState, formats: Formats): String? {
    val from = state.historyFrom ?: return null
    val to = state.historyTo ?: return null
    return stringResource(R.string.plan_history_range, formats.date(from), formats.date(to))
}

/** One signal row: what it is, its number, or why there is none. The spec's letters (S, M, N…) stay internal (SPEC 10.4). */
private data class SignalLine(val title: Int, val value: String?, val detail: String?)

@Composable
private fun SignalTable(state: PlanState, recommendation: Recommendation?, formats: Formats) {
    Rule()
    signalLines(state, recommendation, formats).forEach { line ->
        Row(Modifier.fillMaxWidth().padding(vertical = Space.m).semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(line.title), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
                line.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet) }
            }
            Text(
                line.value ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = Ed.palette.ink,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = Space.m).weight(1f),
            )
        }
        Rule()
    }
}

@Composable
private fun signalLines(state: PlanState, recommendation: Recommendation?, formats: Formats): List<SignalLine> {
    val q = SignalLine(R.string.plan_signal_q, state.urgency.word(), null)
    val signals = recommendation?.signals ?: return listOf(q)
    val neverDeep = signals.neverDeep
    val resets = signals.motionResetsPerHour
    return listOf(
        SignalLine(
            R.string.plan_signal_s,
            stringResource(R.string.plan_per_day, formats.duration(Math.round(signals.screenOffHoursPerDay * HOUR_MS))),
            stringResource(R.string.plan_signal_s_detail, formats.percentText(signals.longShare)),
        ),
        SignalLine(
            R.string.plan_signal_m,
            (neverDeep as? Measured.Known)?.let { stringResource(R.string.plan_of, it.value.count, it.value.of) },
            listOfNotNull(
                (resets as? Measured.Known)?.let { stringResource(R.string.plan_signal_m_detail, formats.decimal(it.value, 1)) },
                ((neverDeep as? Measured.Unknown) ?: (resets as? Measured.Unknown))?.reason?.line(),
            ).joinToString("\n").ifEmpty { null },
        ),
        SignalLine(R.string.plan_signal_n, stringResource(R.string.plan_of, signals.nights.size, signals.availableNights), null),
        measured(R.string.plan_signal_d, signals.deepShare, formats::percentText),
        measured(R.string.plan_signal_r, signals.drainPerHour, formats::drainText),
        q,
    )
}

@Composable
private fun measured(title: Int, value: Measured<Double>, text: (Double) -> String): SignalLine = when (value) {
    is Measured.Known -> SignalLine(title, text(value.value), null)
    is Measured.Unknown -> SignalLine(title, null, value.reason.line())
}

private const val HOUR_MS = 3_600_000.0
