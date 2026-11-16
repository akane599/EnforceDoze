// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.app.automation.Automation
import io.github.akane599.enforcedoze.ui.succeeded
import io.github.akane599.enforcedoze.ui.LeverCheck
import io.github.akane599.enforcedoze.ui.CheckState
import io.github.akane599.enforcedoze.doze.LeverStatus
import io.github.akane599.enforcedoze.doze.LeverState
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.app.EngineGraph
import com.akylas.enforcedoze.R
import io.github.akane599.enforcedoze.doze.HoldOff
import io.github.akane599.enforcedoze.doze.EngineStatus
import io.github.akane599.enforcedoze.doze.Mode
import io.github.akane599.enforcedoze.doze.PlanState
import io.github.akane599.enforcedoze.doze.SwitchReason
import io.github.akane599.enforcedoze.privilege.Grant
import io.github.akane599.enforcedoze.privilege.ShizukuStatus
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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

    /** The automation API (SPEC 11.1, ADR 0033). */
    val automation: Automation by lazy { Automation(this) }
    val notices: AppNotices = AppNotices(graph.app, graph, access, prefs, labels)
    val recommendations: Recommendations by lazy { Recommendations(this) }
    val dayZero: DayZeroReader by lazy { DayZeroReader(this) }
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
            // The engine has written and read back by now: each lever checks in with what the phone shows.
            ActionOutcome.Applied(checks(host.status.value.levers))
        }
        if (outcome.succeeded) prefs.applied(plan, Instant.now())
        return outcome
    }

    /** The levers the profile uses, in lever order, as the engine read them back (UNUSED ones are left out). */
    private fun checks(levers: Map<Lever, LeverStatus>): List<LeverCheck> = levers.entries
        .filter { it.value.state != LeverState.UNUSED }
        .sortedBy { it.key }
        .map { (lever, status) ->
            LeverCheck(graph.app.getString(lever.label()), status.state.check(), status.seen?.toString() ?: status.reason)
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

    private val accessChanges = MutableStateFlow(0)

    /** Bumped when the worker becomes ready or a durable grant is added, so day-zero history is read again. */
    val accessChanged: StateFlow<Int> = accessChanges.asStateFlow()

    init {
        // The privilege state follows Shizuku for the whole process, engine running or not: on the first run the
        // engine is off, and without this the tier stayed T0 and the grants below never came.
        graph.controller.watch()
        // SPEC 4.1 T1: the durable grants are given once through the worker, as soon as Shizuku or Sui allows it.
        graph.scope.launch {
            graph.controller.state.map { it.shizuku is ShizukuStatus.Ready }.distinctUntilChanged().filter { it }.collect {
                grantDurable()
                dayZero.forget()
                accessChanges.value++
            }
        }
    }

    /**
     * Grants `DUMP`, `WRITE_SECURE_SETTINGS` (lever A, ADR 0012) and usage access through the worker when one is missing
     * (SPEC 4.1 T1); true when one was added. `READ_LOGS` stays with the worker, which reads the events buffer anyway.
     */
    suspend fun grantDurable(): Boolean {
        val have = graph.controller.refresh().grants
        val permissions = DURABLE_PERMISSIONS.filterKeys { it !in have }.values
        val usage = Grant.USAGE_STATS !in have
        if (permissions.isEmpty() && !usage) return false
        graph.controller.withWorker { api ->
            permissions.forEach { api.grantSelf(it) }
            if (usage) api.setSelfAppOp(USAGE_OP, ALLOW)
        }
        val added = graph.controller.refresh().grants != have
        if (added) {
            dayZero.forget()
            accessChanges.value++
        }
        return added
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
                ActionOutcome.Done, is ActionOutcome.Applied -> Unit
            }
        }
    }

    /** Whether the tier can write the levers (T2 and up, SPEC 4.1). */
    val canWrite: Flow<Boolean> = graph.controller.state.map(notices::canWrite)

    private companion object {
        /** The permissions of T1 (SPEC 4.1), each in `WorkerContract.SELF_PERMISSIONS`. */
        val DURABLE_PERMISSIONS = mapOf(
            Grant.DUMP to "android.permission.DUMP",
            Grant.WRITE_SECURE_SETTINGS to "android.permission.WRITE_SECURE_SETTINGS",
        )

        /** In `WorkerContract.SELF_APP_OPS` and `APP_OP_MODES`. */
        const val USAGE_OP = "GET_USAGE_STATS"
        const val ALLOW = "allow"
    }
}

/** What the screens need of the engine, whether or not it runs. */
internal data class EngineView(val running: Boolean, val mode: Mode, val plan: PlanState?, val status: EngineStatus?) {
    /** Off, or paused by the owner: the phone runs stock (SPEC 9.1 "pause plan (back to Stock)"). */
    val paused: Boolean get() = !running || mode == Mode.PAUSED
}

private fun LeverState.check(): CheckState = when (this) {
    LeverState.APPLIED -> CheckState.APPLIED
    LeverState.ARMED -> CheckState.ARMED
    LeverState.HELD_OFF -> CheckState.HELD_OFF
    LeverState.PARTLY -> CheckState.PARTLY
    LeverState.NOT_WRITABLE -> CheckState.NOT_WRITABLE
    LeverState.WAITING -> CheckState.WAITING
    LeverState.GUARDED -> CheckState.GUARDED
    LeverState.UNKNOWN, LeverState.UNUSED -> CheckState.UNKNOWN
}
