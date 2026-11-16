// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.feature.onboarding.OnboardingSource
import io.github.akane599.enforcedoze.feature.onboarding.OnboardingState
import io.github.akane599.enforcedoze.feature.onboarding.PrivilegeFinding
import io.github.akane599.enforcedoze.feature.onboarding.ProbeLine
import io.github.akane599.enforcedoze.feature.onboarding.ProbeWord
import io.github.akane599.enforcedoze.feature.onboarding.ServerKind
import io.github.akane599.enforcedoze.privilege.PrivilegeState
import io.github.akane599.enforcedoze.privilege.ShizukuStatus
import com.akylas.enforcedoze.R
import io.github.akane599.enforcedoze.privilege.probe.CapabilityProber
import io.github.akane599.enforcedoze.privilege.probe.CapabilityReport
import io.github.akane599.enforcedoze.privilege.probe.ProbeResult
import io.github.akane599.enforcedoze.privilege.probe.ProbeStatus
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.recommend.Urgency
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** Onboarding (SPEC 9.1) over the privilege layer, the capability probes, the recommendation and the quick proof. */
internal class AppOnboardingSource(private val services: AppServices) : OnboardingSource {
    private val applied = MutableStateFlow<Plan?>(null)

    override val state: Flow<OnboardingState> = combine(
        combine(services.graph.controller.state, services.capabilities, services.prefs.changes) { privilege, report, _ ->
            privilege to report
        },
        services.recommendations.current,
        services.profileNames,
        services.proof,
        applied,
    ) { (privilege, report), recommendation, names, proof, plan ->
        OnboardingState(
            privilege = finding(privilege),
            tier = privilege.tier.ordinal,
            probes = report?.let { lines(it, ::title) },
            urgency = services.prefs.urgency,
            recommendation = recommendation,
            profileNames = names,
            proof = proof,
            applied = plan,
        )
    }

    /** The probe's name in the owner's language; its detail stays as the device printed it (SPEC 9.3 raw lines). */
    private fun title(result: ProbeResult): String = PROBE_TITLES[result.id]?.let(services.graph.app::getString) ?: result.title

    override fun requestPermission() = services.graph.controller.requestShizukuPermission()

    override suspend fun recheck() {
        services.graph.controller.refresh()
        services.grantDurable()
        services.probe()
    }

    override suspend fun answer(urgency: Urgency) {
        services.prefs.urgency = urgency
    }

    override suspend fun apply(plan: Plan): ActionOutcome = services.applyPlan(plan).also { outcome ->
        if (outcome == ActionOutcome.Done) applied.value = plan
    }

    override suspend fun runProof() = services.runProof()

    override suspend fun finish() {
        services.prefs.onboarded = true
    }

    companion object {
        /** What the Shizuku or Sui client reports. A server's API level is not its release, so no version is shown. */
        fun finding(privilege: PrivilegeState): PrivilegeFinding = when (val shizuku = privilege.shizuku) {
            ShizukuStatus.NotRunning -> PrivilegeFinding()
            ShizukuStatus.Unsupported -> PrivilegeFinding(ServerKind.SHIZUKU, running = true)
            is ShizukuStatus.PermissionNeeded -> PrivilegeFinding(ServerKind.SHIZUKU, running = true)
            is ShizukuStatus.Ready -> PrivilegeFinding(
                server = if (shizuku.sui) ServerKind.SUI else ServerKind.SHIZUKU,
                running = true,
                allowed = true,
                root = shizuku.uid == ROOT_UID,
            )
        }

        private val PROBE_TITLES = mapOf(
            CapabilityProber.READ to R.string.ui_probe_read,
            CapabilityProber.KEYS to R.string.ui_probe_keys,
            CapabilityProber.WRITE to R.string.ui_probe_write,
            CapabilityProber.FORCE to R.string.ui_probe_force,
            CapabilityProber.RESTRICT to R.string.ui_probe_restrict,
            CapabilityProber.PRIVACY to R.string.ui_probe_privacy,
            CapabilityProber.EVENTS to R.string.ui_probe_events,
            CapabilityProber.HISTORY to R.string.ui_probe_history,
            CapabilityProber.ONE_UI to R.string.ui_probe_one_ui,
            CapabilityProber.PERSISTENCE to R.string.ui_probe_persistence,
        )

        fun lines(report: CapabilityReport, title: (ProbeResult) -> String = { it.title }): List<ProbeLine> = report.results.map {
            val word = when (it.status) {
                ProbeStatus.OK -> ProbeWord.OK
                ProbeStatus.MISSING -> ProbeWord.MISSING
                ProbeStatus.FAILED -> ProbeWord.FAILED
                ProbeStatus.SKIPPED -> ProbeWord.SKIPPED
            }
            ProbeLine(title(it), word, it.detail)
        }

        private const val ROOT_UID = 0
    }
}
