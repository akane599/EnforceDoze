// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.onboarding

import androidx.compose.runtime.Immutable
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.recommend.Recommendation
import io.github.akane599.enforcedoze.recommend.Urgency
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.ProofStep
import io.github.akane599.enforcedoze.ui.ProofStepStatus
import kotlinx.coroutines.flow.Flow

/** The six steps of SPEC 9.1, in order. */
public enum class OnboardingStep { WELCOME, PRIVILEGE, CAPABILITIES, URGENCY, PLAN, PROOF }

/** The server that answered the privilege check (ADR 0011: any server that keeps Shizuku's binder protocol). */
public enum class ServerKind { SHIZUKU, SUI }

/** What the privilege check found. [allowed] means the app may bind the worker. */
@Immutable
public data class PrivilegeFinding(
    val server: ServerKind? = null,
    val version: String? = null,
    val running: Boolean = false,
    val allowed: Boolean = false,
    /** The worker runs as root rather than shell. */
    val root: Boolean = false,
)

public enum class ProbeWord { OK, MISSING, FAILED, SKIPPED }

/** One capability probe as the owner reads it (SPEC 4.4): what it checks, its result, and the line it was judged on. */
@Immutable
public data class ProbeLine(val title: String, val status: ProbeWord, val detail: String)

/** Everything onboarding shows, mapped by the app from the privilege session, the probes and the recommender. */
@Immutable
public data class OnboardingState(
    val privilege: PrivilegeFinding,
    val tier: Int = 0,
    /** Null until the capability check has run. */
    val probes: List<ProbeLine>? = null,
    val urgency: Urgency = Urgency.WITHIN_THE_HOUR,
    /** The day-zero recommendation (SPEC 7.1); null when the phone holds no screen-off history at all. */
    val recommendation: Recommendation? = null,
    /** Profile ids to the names the owner sees. */
    val profileNames: Map<String, String> = emptyMap(),
    /** The quick proof's steps once it started; null before. */
    val proof: List<ProofStep>? = null,
    /** The plan the engine runs now: after Apply, the recommended one. */
    val applied: Plan? = null,
) {
    /** Levers can be applied: tier T2 or T3 (SPEC 4.1). */
    public val canWrite: Boolean get() = tier >= WORKER_TIER

    public val proofRunning: Boolean get() = proof?.any { it.status == ProofStepStatus.RUNNING || it.status == ProofStepStatus.WAITING } ==
        true

    public fun name(id: String): String = profileNames[id] ?: id

    private companion object {
        const val WORKER_TIER = 2
    }
}

/** What onboarding needs from the app (ADR 0029). */
public interface OnboardingSource {
    public val state: Flow<OnboardingState>

    /** Asks the server for permission (the Shizuku dialog, or Sui's). */
    public fun requestPermission()

    /** Probes the server and the capabilities again (SPEC 4.4). */
    public suspend fun recheck()

    public suspend fun answer(urgency: Urgency)

    public suspend fun apply(plan: Plan): ActionOutcome

    /** Runs the quick proof (SPEC 8.6); its steps arrive through [state]. */
    public suspend fun runProof()

    /** Onboarding is done: Home from now on. */
    public suspend fun finish()
}

/** Where onboarding sends the owner when it is done; the app's navigation implements it (ADR 0029). */
public fun interface OnboardingNavigation {
    public fun done()
}
