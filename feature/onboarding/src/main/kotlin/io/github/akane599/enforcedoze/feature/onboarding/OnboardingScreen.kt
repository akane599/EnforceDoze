// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akane599.enforcedoze.design.ColumnHead
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.Notice
import io.github.akane599.enforcedoze.design.RawLine
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.ScreenColumn
import io.github.akane599.enforcedoze.design.Sizes
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.Tag
import io.github.akane599.enforcedoze.design.Tone
import io.github.akane599.enforcedoze.design.color
import io.github.akane599.enforcedoze.design.rememberFormats
import io.github.akane599.enforcedoze.recommend.Confidence
import io.github.akane599.enforcedoze.recommend.Note
import io.github.akane599.enforcedoze.recommend.Urgency
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.LeverCheckIn
import io.github.akane599.enforcedoze.ui.ProofCard
import io.github.akane599.enforcedoze.ui.ProofStep
import io.github.akane599.enforcedoze.ui.ProofStepKind
import io.github.akane599.enforcedoze.ui.ProofStepStatus
import io.github.akane599.enforcedoze.ui.line
import io.github.akane599.enforcedoze.ui.sentence
import io.github.akane599.enforcedoze.ui.tierLine
import io.github.akane599.enforcedoze.ui.tierName
import io.github.akane599.enforcedoze.ui.word

/** What onboarding does on the owner's taps. */
public interface OnboardingActions {
    public fun next()

    public fun allowBackground()

    public fun openAppSettings()

    public fun back()

    public fun grant()

    public fun recheck()

    public fun answer(urgency: Urgency)

    public fun apply()

    public fun runProof()

    public fun finish()
}

/** What the screen shows beyond [OnboardingState]: a check running, and the last Apply. */
public data class OnboardingStatus(val checking: Boolean = false, val applyOutcome: ActionOutcome? = null)

/**
 * Onboarding (SPEC 9.1): six stops on one route line. Each step says what it does and why, and its actions sit at
 * the bottom; the owner can always go on read-only (T0).
 */
@Composable
public fun OnboardingScreen(
    state: OnboardingState,
    step: OnboardingStep,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
    status: OnboardingStatus = OnboardingStatus(),
) {
    Scaffold(
        modifier = modifier,
        containerColor = Ed.palette.ground,
        topBar = { Route(step, actions) },
        bottomBar = { Actions(state, step, actions, status) },
    ) { padding ->
        ScreenColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Space.l))
            when (step) {
                OnboardingStep.WELCOME -> Welcome()
                OnboardingStep.PRIVILEGE -> Privilege(state.privilege)
                OnboardingStep.CAPABILITIES -> Capabilities(state, status.checking, actions)
                OnboardingStep.URGENCY -> UrgencyStep(state.urgency, actions)
                OnboardingStep.PLAN -> PlanStep(state, status.applyOutcome)
                OnboardingStep.PROOF -> Proof(state)
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/** The six steps as stops on a route line; the current stop in the accent, the passed ones in ink. */
@Composable
private fun Route(step: OnboardingStep, actions: OnboardingActions) {
    val palette = Ed.palette
    val count = OnboardingStep.entries.size
    val label = stringResource(R.string.onboarding_step, step.ordinal + 1, count, stringResource(step.title()))
    Box(Modifier.fillMaxWidth().background(palette.ground).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Space.column).fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.weight(1f).height(STOP_SIZE).semantics { contentDescription = label }) {
                    val r = size.height / 2
                    val gap = (size.width - 2 * r) / (count - 1)
                    drawLine(palette.hairline, Offset(r, r), Offset(size.width - r, r), strokeWidth = LINE.toPx())
                    repeat(count) { i ->
                        val center = Offset(r + i * gap, r)
                        when {
                            i == step.ordinal -> {
                                drawCircle(palette.accent, r, center)
                                // Yellow on the light ground is 1.3:1, so an ink ring carries the mark's 3:1 there (SPEC 10.4).
                                if (!palette.dark) drawCircle(palette.ink, r - LINE.toPx() / 2, center, style = Stroke(LINE.toPx()))
                            }

                            i < step.ordinal -> drawCircle(palette.ink, r * DONE_SCALE, center)

                            else -> {
                                drawCircle(palette.ground, r * DONE_SCALE, center)
                                drawCircle(palette.quiet, r * DONE_SCALE, center, style = Stroke(LINE.toPx()))
                            }
                        }
                    }
                }
                if (step != OnboardingStep.WELCOME) {
                    TextButton(onClick = actions::back, modifier = Modifier.padding(start = Space.s)) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                }
            }
            ColumnHead(label, Modifier.padding(top = Space.xs))
        }
    }
}

private val STOP_SIZE = 14.dp
private val LINE = 2.dp
private const val DONE_SCALE = 0.7f

private fun OnboardingStep.title(): Int = when (this) {
    OnboardingStep.WELCOME -> R.string.onboarding_title_welcome
    OnboardingStep.PRIVILEGE -> R.string.onboarding_title_privilege
    OnboardingStep.CAPABILITIES -> R.string.onboarding_title_capabilities
    OnboardingStep.URGENCY -> R.string.onboarding_title_urgency
    OnboardingStep.PLAN -> R.string.onboarding_title_plan
    OnboardingStep.PROOF -> R.string.onboarding_title_proof
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = Ed.palette.ink, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(Space.m))
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink, modifier = modifier)
}

@Composable
private fun Quiet(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet, modifier = modifier)
}

@Composable
private fun Welcome() {
    Text(stringResource(R.string.onboarding_app), style = MaterialTheme.typography.displaySmall, color = Ed.palette.ink)
    Spacer(Modifier.height(Space.l))
    Body(stringResource(R.string.onboarding_welcome))
    Spacer(Modifier.height(Space.m))
    Quiet(stringResource(R.string.onboarding_welcome_undo))
}

/** ADR 0011: what answered, or the ways to a worker on Android 17 in its order, then read-only. */
@Composable
private fun Privilege(privilege: PrivilegeFinding) {
    val server = privilege.server?.let {
        val name = stringResource(if (it == ServerKind.SUI) R.string.onboarding_sui else R.string.onboarding_shizuku)
        privilege.version?.let { version -> "$name $version" } ?: name
    }
    when {
        privilege.allowed && server != null -> {
            Heading(stringResource(R.string.onboarding_privilege_allowed, server))
            Body(stringResource(if (privilege.root) R.string.onboarding_privilege_root else R.string.onboarding_privilege_shell))
        }

        privilege.running && server != null -> {
            Heading(stringResource(R.string.onboarding_privilege_running, server))
            Body(stringResource(R.string.onboarding_privilege_ask))
        }

        else -> {
            Heading(stringResource(R.string.onboarding_privilege_none))
            Body(stringResource(R.string.onboarding_privilege_why))
            Option(R.string.onboarding_option_fork, R.string.onboarding_option_fork_detail)
            Option(R.string.onboarding_option_sui, R.string.onboarding_option_sui_detail)
            Option(R.string.onboarding_option_adb, R.string.onboarding_option_adb_detail, ADB_GRANT_WORKER)
            Option(R.string.onboarding_option_read, R.string.onboarding_option_read_detail, ADB_GRANT_DUMP)
        }
    }
}

/** ADR 0011: the server treats this runtime permission, once granted, as the app's authorisation. */
private const val ADB_GRANT_WORKER = "adb shell pm grant io.github.akane599.enforcedoze moe.shizuku.manager.permission.API_V23"

/** SPEC 4.1 T1: `DUMP` is a development permission, so `pm grant` over ADB gives it for good. */
private const val ADB_GRANT_DUMP = "adb shell pm grant io.github.akane599.enforcedoze android.permission.DUMP"

@Composable
private fun Option(title: Int, detail: Int, command: String? = null) {
    Column(Modifier.padding(top = Space.l).semantics(mergeDescendants = true) { }) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = Ed.palette.ink)
        Body(stringResource(detail))
        command?.let {
            Text(
                it,
                style = RawLine,
                color = Ed.palette.ink,
                modifier = Modifier.padding(
                    top = Space.s,
                ).fillMaxWidth().background(Ed.palette.raised, MaterialTheme.shapes.extraSmall).padding(Space.s),
            )
        }
    }
}

@Composable
private fun Capabilities(state: OnboardingState, checking: Boolean, actions: OnboardingActions) {
    Heading(tierName(state.tier))
    Body(tierLine(state.tier))
    Spacer(Modifier.height(Space.l))
    state.keepAlive?.let { KeepAliveBlock(it, actions) }
    val probes = state.probes
    if (probes == null) {
        Quiet(stringResource(if (checking) R.string.onboarding_checking else R.string.onboarding_not_checked))
        return
    }
    Rule()
    probes.forEach { probe ->
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m).semantics(mergeDescendants = true) { }) {
            Row(verticalAlignment = Alignment.Top) {
                Text(probe.title, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink, modifier = Modifier.weight(1f))
                Text(
                    stringResource(probe.status.word()),
                    style = MaterialTheme.typography.titleMedium,
                    color = probe.status.tone().color(neutral = Ed.palette.quiet),
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = Space.m),
                )
            }
            // The detail is the device's own output (SPEC 9.3), so it is set as a raw line.
            Text(probe.detail, style = RawLine, color = Ed.palette.quiet, modifier = Modifier.padding(top = Space.xs))
        }
        Rule()
    }
}

/** SPEC 3.3: the app must keep running for the nights to be recorded; the system's own dialog grants it. */
@Composable
private fun KeepAliveBlock(keepAlive: KeepAlive, actions: OnboardingActions) {
    val status = if (keepAlive.exempt) ProbeWord.OK else ProbeWord.MISSING
    Column(Modifier.fillMaxWidth().padding(bottom = Space.l)) {
        Row(Modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.Top) {
            Text(
                stringResource(R.string.onboarding_keep_title),
                style = MaterialTheme.typography.titleMedium,
                color = Ed.palette.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(status.word()),
                style = MaterialTheme.typography.titleMedium,
                color = status.tone().color(neutral = Ed.palette.quiet),
                modifier = Modifier.padding(start = Space.m),
            )
        }
        Body(stringResource(R.string.onboarding_keep_body), Modifier.padding(top = Space.xs))
        if (keepAlive.samsung) Quiet(stringResource(R.string.onboarding_keep_samsung), Modifier.padding(top = Space.s))
        Text(keepAlive.detail, style = RawLine, color = Ed.palette.quiet, modifier = Modifier.padding(top = Space.s))
        Row(Modifier.padding(top = Space.xs)) {
            if (!keepAlive.exempt) Secondary(R.string.onboarding_keep_allow, actions::allowBackground)
            Secondary(R.string.onboarding_keep_settings, actions::openAppSettings)
        }
    }
}

private fun ProbeWord.word(): Int = when (this) {
    ProbeWord.OK -> R.string.onboarding_probe_ok
    ProbeWord.MISSING -> R.string.onboarding_probe_missing
    ProbeWord.FAILED -> R.string.onboarding_probe_failed
    ProbeWord.SKIPPED -> R.string.onboarding_probe_skipped
}

private fun ProbeWord.tone(): Tone = when (this) {
    ProbeWord.OK -> Tone.GOOD
    ProbeWord.MISSING -> Tone.WARNING
    ProbeWord.FAILED -> Tone.CRITICAL
    ProbeWord.SKIPPED -> Tone.NEUTRAL
}

@Composable
private fun UrgencyStep(selected: Urgency, actions: OnboardingActions) {
    Heading(stringResource(io.github.akane599.enforcedoze.ui.R.string.ui_urgency_question))
    Quiet(stringResource(R.string.onboarding_urgency_detail))
    Spacer(Modifier.height(Space.m))
    Column(Modifier.selectableGroup()) {
        Urgency.entries.forEach { urgency ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touch)
                    .selectable(urgency == selected, role = Role.RadioButton, onClick = { actions.answer(urgency) })
                    .padding(vertical = Space.s),
                verticalAlignment = Alignment.Top,
            ) {
                // Centred on the title's first line, so at a large font it stays with the answer, not its detail.
                val line = with(LocalDensity.current) { MaterialTheme.typography.bodyLarge.lineHeight.toDp() }
                Box(Modifier.height(line), contentAlignment = Alignment.Center) {
                    RadioButton(selected = urgency == selected, onClick = null)
                }
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(urgency.word(), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
                    Quiet(stringResource(urgency.detail()))
                }
            }
        }
    }
}

private fun Urgency.detail(): Int = when (this) {
    Urgency.RIGHT_AWAY -> R.string.onboarding_urgency_right_away
    Urgency.WITHIN_THE_HOUR -> R.string.onboarding_urgency_within_hour
    Urgency.IT_CAN_WAIT -> R.string.onboarding_urgency_can_wait
}

@Composable
private fun PlanStep(state: OnboardingState, outcome: ActionOutcome?) {
    val formats = rememberFormats()
    val recommendation = state.recommendation
    if (recommendation == null) {
        Heading(stringResource(R.string.onboarding_plan_none_title))
        Body(stringResource(R.string.onboarding_plan_none))
        return
    }
    val plan = recommendation.plan
    Tag(recommendation.confidence.word(), accent = recommendation.confidence == Confidence.HIGH)
    Spacer(Modifier.height(Space.m))
    Text(
        stringResource(R.string.onboarding_by_day, state.name(plan.dayProfileId)),
        style = MaterialTheme.typography.headlineMedium,
        color = Ed.palette.ink,
    )
    val night = plan.night
    val nightLine = when {
        night != null -> stringResource(
            R.string.onboarding_at_night,
            state.name(night.profileId),
            formats.time(night.start),
            formats.time(night.end),
        )

        Note.NIGHT_BY_HAND in recommendation.notes -> stringResource(R.string.onboarding_night_by_hand, state.name(NIGHT))

        else -> null
    }
    nightLine?.let { Text(it, style = MaterialTheme.typography.titleLarge, color = Ed.palette.ink) }
    recommendation.notes.forEach { Quiet(it.line(state::name), Modifier.padding(top = Space.s)) }
    Spacer(Modifier.height(Space.l))
    Text(stringResource(R.string.onboarding_why), style = MaterialTheme.typography.titleLarge, color = Ed.palette.ink)
    Spacer(Modifier.height(Space.s))
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        recommendation.reasons.take(WHY_LINES).forEach { Body(it.sentence(formats, state::name)) }
    }
    if (!state.canWrite) Quiet(stringResource(R.string.onboarding_plan_read_only), Modifier.padding(top = Space.l))
    if (state.applied == plan) Quiet(stringResource(R.string.onboarding_plan_applied), Modifier.padding(top = Space.l))
    when (outcome) {
        is ActionOutcome.Unavailable -> Notice(
            stringResource(R.string.onboarding_apply_failed),
            outcome.reason,
            Modifier.padding(top = Space.l),
            tone = Tone.WARNING,
        )

        is ActionOutcome.Failed -> Notice(
            stringResource(R.string.onboarding_apply_failed),
            outcome.raw,
            Modifier.padding(top = Space.l),
            tone = Tone.CRITICAL,
            raw = true,
        )

        is ActionOutcome.Applied -> LeverCheckIn(outcome.checks, Modifier.padding(top = Space.m))

        else -> Unit
    }
}

private const val WHY_LINES = 3
private const val NIGHT = "night"

@Composable
private fun Proof(state: OnboardingState) {
    Heading(stringResource(R.string.onboarding_proof_title))
    Body(stringResource(R.string.onboarding_proof))
    Spacer(Modifier.height(Space.l))
    if (!state.canWrite) {
        Quiet(stringResource(R.string.onboarding_proof_read_only))
        return
    }
    ProofCard(state.proof ?: ProofStepKind.entries.map { ProofStep(it, ProofStepStatus.WAITING) })
}

/** The step's actions, at the bottom: the main one filled, the way past it as text. */
@Composable
private fun Actions(state: OnboardingState, step: OnboardingStep, actions: OnboardingActions, status: OnboardingStatus) {
    Box(Modifier.fillMaxWidth().background(Ed.palette.ground).navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        FlowRow(
            Modifier.widthIn(max = Space.column).fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.m, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            when (step) {
                OnboardingStep.WELCOME -> Main(R.string.onboarding_start, actions::next)

                OnboardingStep.PRIVILEGE -> PrivilegeActions(state.privilege, actions, status.checking)

                OnboardingStep.CAPABILITIES -> {
                    Secondary(R.string.onboarding_check_again, actions::recheck, enabled = !status.checking)
                    Main(R.string.onboarding_continue, actions::next)
                }

                OnboardingStep.URGENCY -> Main(R.string.onboarding_continue, actions::next)

                OnboardingStep.PLAN -> PlanActions(state, actions)

                OnboardingStep.PROOF -> ProofActions(state, actions)
            }
        }
    }
}

@Composable
private fun PrivilegeActions(privilege: PrivilegeFinding, actions: OnboardingActions, checking: Boolean) {
    when {
        privilege.allowed -> Main(R.string.onboarding_continue, actions::next)

        privilege.running -> {
            Secondary(R.string.onboarding_read_only, actions::next)
            Main(R.string.onboarding_grant, actions::grant)
        }

        else -> {
            Secondary(R.string.onboarding_read_only, actions::next)
            Main(R.string.onboarding_check_again, actions::recheck, enabled = !checking)
        }
    }
}

@Composable
private fun PlanActions(state: OnboardingState, actions: OnboardingActions) {
    val plan = state.recommendation?.plan
    if (plan == null || state.applied == plan || !state.canWrite) {
        Main(R.string.onboarding_continue, actions::next)
    } else {
        Secondary(R.string.onboarding_not_now, actions::next)
        Main(R.string.onboarding_apply, actions::apply)
    }
}

@Composable
private fun ProofActions(state: OnboardingState, actions: OnboardingActions) {
    val ran = state.proof?.none { it.status == ProofStepStatus.WAITING || it.status == ProofStepStatus.RUNNING } == true
    if (!state.canWrite || ran) {
        Main(R.string.onboarding_done, actions::finish)
    } else {
        Secondary(R.string.onboarding_skip, actions::finish)
        Main(
            if (state.proofRunning) R.string.onboarding_proof_running else R.string.onboarding_proof_run,
            actions::runProof,
            enabled = !state.proofRunning,
        )
    }
}

@Composable
private fun Main(label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    FilledTonalButton(onClick = onClick, enabled = enabled) { Text(stringResource(label)) }
}

@Composable
private fun Secondary(label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled) { Text(stringResource(label)) }
}
