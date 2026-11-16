// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.app.EngineGraph
import com.akylas.enforcedoze.R
import io.github.akane599.enforcedoze.doze.HoldOff
import io.github.akane599.enforcedoze.doze.EngineStatus
import io.github.akane599.enforcedoze.doze.Mode
import io.github.akane599.enforcedoze.doze.PlanState
import io.github.akane599.enforcedoze.doze.SwitchReason
import io.github.akane599.enforcedoze.privilege.probe.CapabilityReport
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.ProofStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * The three shared services every screen source is built on (ADR 0029): engine access, the evidence log and the
 * privilege layer, with the warnings decided once. Sources are thin mappers over these.
 */
internal class AppServices(val graph: EngineGraph) {
    val prefs: AppPrefs = graph.prefs
    val labels: Labels = Labels(graph.app)
    val access: EngineAccess = EngineAccess(graph.app, graph)
    val notices: AppNotices = AppNotices(graph.app, graph, access, prefs, labels)
    val recommendations: Recommendations by lazy { Recommendations(this) }
    val exports: Exports by lazy { Exports(graph) }

    /** Session and night reports from the evidence log (SPEC 8.4, 8.5), with app, profile and hold-off names. */
    val reports: Reports by lazy {
        val loader = ReportLoader(
            graph.sessions,
            graph.journal,
            profileName = { id -> graph.profiles.profile(id)?.name ?: id },
            app = { uid, packages ->
                uid?.let(labels::forUid)?.let { (label, pkg) -> AppName(label, pkg) }
                    ?: packages.firstOrNull()?.let { pkg -> AppName(labels.label(pkg) ?: pkg, pkg) }
            },
            holdOff = ::holdOffName,
        )
        Reports(loader::report)
    }

    private fun holdOffName(stored: String): String {
        val id = when (runCatching { HoldOff.valueOf(stored) }.getOrNull()) {
            HoldOff.CALL -> R.string.ui_hold_off_call
            HoldOff.AUDIO -> R.string.ui_hold_off_audio
            HoldOff.CAR_MODE -> R.string.ui_hold_off_car_mode
            HoldOff.HOTSPOT -> R.string.ui_hold_off_hotspot
            HoldOff.KEEP_AWAKE_APP -> R.string.ui_hold_off_keep_awake
            HoldOff.CHARGING -> R.string.ui_hold_off_charging
            null -> return stored
        }
        return graph.app.getString(id)
    }

    /** Bumped after the app saves or deletes a custom profile, so lists read the store again. */
    private val profileEdits = MutableStateFlow(0)

    /** The engine as screens see it: its live status while it runs, its saved state while it is off. */
    val engine: Flow<EngineView> = access.status.map { status ->
        if (status != null) {
            EngineView(running = true, mode = status.mode, plan = status.plan, status = status)
        } else {
            val saved = graph.store.load()
            EngineView(running = false, mode = saved.mode, plan = saved.plan, status = null)
        }
    }

    /** Every profile, built-ins first, then the owner's custom ones (SPEC 6.3). */
    val profiles: Flow<List<Profile>> = profileEdits.map { BuiltInProfiles.ALL + graph.profiles.store.custom() }

    val profileNames: Flow<Map<String, String>> = profiles.map { all -> all.associate { it.id to it.name } }

    /** Emits once at the start and whenever a session or a night is recorded or removed. */
    val evidence: Flow<Unit> = graph.sessions.revision().map { }.onStart { emit(Unit) }

    fun profilesChanged() {
        profileEdits.value++
    }

    /** The stock snapshot's timings in milliseconds, for the guardrails and the stock column (SPEC 6.2); null before capture. */
    suspend fun stockMillis(): Map<String, Long>? =
        graph.store.stock()?.effective?.mapNotNull { (key, value) -> value.toLongOrNull()?.let { key to it } }?.toMap()

    /**
     * Runs [plan] through the engine (SPEC 6.5), turning the engine on, and out of a pause, when needed: the owner
     * applying a plan means it to run. The plan is remembered for the feedback loop (SPEC 7.5).
     */
    suspend fun applyPlan(plan: Plan, reason: SwitchReason = SwitchReason.OWNER): ActionOutcome {
        val outcome = access.send(turnOn = true) { host ->
            host.engine.setPlan(plan, reason)
            if (host.status.value.mode == Mode.PAUSED) host.engine.resume()
            ActionOutcome.Done
        }
        if (outcome == ActionOutcome.Done) prefs.applied(plan, Instant.now())
        return outcome
    }

    /**
     * "Follow Recommended automatically" (SPEC 9.1 Plan): when it is on and the recommendation differs from the plan
     * in force, the recommendation is applied. Called when the owner turns it on and whenever a session is recorded.
     */
    suspend fun followRecommendation() {
        if (!prefs.followRecommended) return
        val recommended = recommendations.current.first()?.plan ?: return
        if (engine.first().plan?.plan != recommended) applyPlan(recommended, SwitchReason.RECOMMENDATION)
    }

    private val lastProbe = MutableStateFlow<CapabilityReport?>(null)

    /** The last capability check (SPEC 4.4) in this process; null until one ran. */
    val capabilities: StateFlow<CapabilityReport?> = lastProbe.asStateFlow()

    /** Runs the capability probes without changing anything on the phone (SPEC 4.4). */
    suspend fun probe(): CapabilityReport = graph.controller.probe(includeChanges = false).also { lastProbe.value = it }

    private val proofSteps = MutableStateFlow<List<ProofStep>?>(null)

    /** The last quick proof's steps, shared by Home and onboarding; null before one ran in this process. */
    val proof: StateFlow<List<ProofStep>?> = proofSteps.asStateFlow()

    /**
     * Runs the quick proof through the engine (SPEC 8.6), turning it on first if it is off. It runs in the graph's
     * scope, not a screen's: leaving the screen must not cut the proof off before its undo step.
     */
    fun runProof() {
        if (proofSteps.value == ProofMapping.running) return
        proofSteps.value = ProofMapping.running
        graph.scope.launch {
            val outcome = access.send(turnOn = true) { host ->
                proofSteps.value = ProofMapping.steps(host.engine.quickProof())
                ActionOutcome.Done
            }
            when (outcome) {
                is ActionOutcome.Unavailable -> proofSteps.value = ProofMapping.failed(outcome.reason)
                is ActionOutcome.Failed -> proofSteps.value = ProofMapping.failed(outcome.raw)
                ActionOutcome.Done -> Unit
            }
        }
    }

    /** Whether the tier can write the levers (T2 and up, SPEC 4.1). */
    val canWrite: Flow<Boolean> = graph.controller.state.map(notices::canWrite)
}

/** What the screens need of the engine, whether or not it runs. */
internal data class EngineView(val running: Boolean, val mode: Mode, val plan: PlanState?, val status: EngineStatus?) {
    /** Off, or paused by the owner: the phone runs stock (SPEC 9.1 "pause plan (back to Stock)"). */
    val paused: Boolean get() = !running || mode == Mode.PAUSED
}
