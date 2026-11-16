// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.recommend.Confidence
import io.github.akane599.enforcedoze.recommend.Measured
import io.github.akane599.enforcedoze.recommend.Reason
import io.github.akane599.enforcedoze.recommend.Recommendation
import io.github.akane599.enforcedoze.recommend.Signals
import io.github.akane599.enforcedoze.recommend.Unmeasured
import io.github.akane599.enforcedoze.recommend.Urgency
import io.github.akane599.enforcedoze.testing.screenshots.Primary
import io.github.akane599.enforcedoze.testing.screenshots.ownerClock
import io.github.akane599.enforcedoze.testing.screenshots.shoot
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.ProofStep
import io.github.akane599.enforcedoze.ui.ProofStepKind
import io.github.akane599.enforcedoze.ui.ProofStepStatus
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.LocalTime
import io.github.akane599.enforcedoze.ui.R as UiR

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "hdpi")
class OnboardingScreenshotTest {
    @Before fun clock() = ownerClock()

    private val unknown = Measured.Unknown(Unmeasured.NO_SESSIONS)

    /** Illustrative day-zero numbers only. */
    private val recommendation = Recommendation(
        plan = Plan("everyday", NightSlot("night", LocalTime.of(23, 0), LocalTime.of(7, 30))),
        confidence = Confidence.MEDIUM,
        reasons = listOf(Reason.ScreenOffPerDay(8.75), Reason.UnpluggedNights(4, 5, LocalTime.of(23, 0), LocalTime.of(7, 30))),
        notes = emptySet(),
        signals = Signals(Duration.ofHours(40), Duration.ofDays(5), 8.75, 0.6, unknown, unknown, unknown, emptyList(), 5, unknown, unknown),
    )

    /** Probe names as the app words them (`:core:ui`); details as the device printed them. */
    @Composable
    private fun shell() = OnboardingState(
        privilege = PrivilegeFinding(ServerKind.SHIZUKU, "13.7.0", running = true, allowed = true),
        tier = 2,
        probes = listOf(
            ProbeLine(stringResource(UiR.string.ui_probe_keys), ProbeWord.OK, "device_idle_constants: 23 keys"),
            ProbeLine(
                stringResource(UiR.string.ui_probe_force),
                ProbeWord.OK,
                "cmd deviceidle force-idle deep: Now forced in to deep idle mode",
            ),
            ProbeLine(stringResource(UiR.string.ui_probe_restrict), ProbeWord.OK, "Mode : RESTRICTED : io.github.akane599.enforcedoze"),
            ProbeLine(stringResource(UiR.string.ui_probe_vendor), ProbeWord.MISSING, "no manufacturer fields in dumpsys deviceidle"),
        ),
        recommendation = recommendation,
        profileNames = mapOf("everyday" to "Everyday", "night" to "Night"),
    )

    @Test fun welcome() = shoot(MODULE, "welcome") { OnboardingScreen(shell(), OnboardingStep.WELCOME, NoOnboarding) }

    @Test fun privilegeNone() = shoot(MODULE, "privilege-none", tall = true) {
        OnboardingScreen(OnboardingState(PrivilegeFinding()), OnboardingStep.PRIVILEGE, NoOnboarding)
    }

    @Test fun privilegeAsk() = shoot(MODULE, "privilege-ask", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        OnboardingScreen(
            OnboardingState(PrivilegeFinding(ServerKind.SHIZUKU, "13.7.0", running = true)),
            OnboardingStep.PRIVILEGE,
            NoOnboarding,
        )
    }

    @Test fun privilegeAllowed() = shoot(MODULE, "privilege-allowed", listOf(Primary)) {
        OnboardingScreen(shell(), OnboardingStep.PRIVILEGE, NoOnboarding)
    }

    @Test fun capabilities() = shoot(MODULE, "capabilities") { OnboardingScreen(shell(), OnboardingStep.CAPABILITIES, NoOnboarding) }

    @Test fun urgency() = shoot(MODULE, "urgency", listOf(Primary, Primary.copy(dark = false, turkish = true, largeFont = true))) {
        OnboardingScreen(shell().copy(urgency = Urgency.IT_CAN_WAIT), OnboardingStep.URGENCY, NoOnboarding)
    }

    @Test fun plan() = shoot(MODULE, "plan") { OnboardingScreen(shell(), OnboardingStep.PLAN, NoOnboarding) }

    @Test fun planReadOnly() = shoot(MODULE, "plan-read-only", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        OnboardingScreen(shell().copy(tier = 0), OnboardingStep.PLAN, NoOnboarding)
    }

    @Test fun planFailed() = shoot(MODULE, "plan-failed", listOf(Primary)) {
        OnboardingScreen(
            shell(),
            OnboardingStep.PLAN,
            NoOnboarding,
            status = OnboardingStatus(applyOutcome = ActionOutcome.Failed("settings put global device_idle_constants: exit 255")),
        )
    }

    @Test fun proofRunning() = shoot(MODULE, "proof-running", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        OnboardingScreen(
            shell().copy(
                proof = listOf(
                    ProofStep(ProofStepKind.CONSTANTS, ProofStepStatus.OK, "inactive_to=+30s read back"),
                    ProofStep(ProofStepKind.FORCE_IDLE, ProofStepStatus.RUNNING),
                    ProofStep(ProofStepKind.SENSORS, ProofStepStatus.WAITING),
                    ProofStep(ProofStepKind.UNDO, ProofStepStatus.WAITING),
                ),
            ),
            OnboardingStep.PROOF,
            NoOnboarding,
        )
    }

    @Test fun proofDone() = shoot(MODULE, "proof-done") {
        OnboardingScreen(
            shell().copy(
                proof = listOf(
                    ProofStep(ProofStepKind.CONSTANTS, ProofStepStatus.OK, "inactive_to=+30s read back"),
                    ProofStep(ProofStepKind.FORCE_IDLE, ProofStepStatus.OK, "cmd deviceidle get deep: IDLE"),
                    ProofStep(ProofStepKind.SENSORS, ProofStepStatus.FAILED, "Mode : NORMAL"),
                    ProofStep(ProofStepKind.UNDO, ProofStepStatus.OK, "unforce; sensorservice enable; battery reset"),
                ),
            ),
            OnboardingStep.PROOF,
            NoOnboarding,
        )
    }

    private companion object {
        const val MODULE = "feature/onboarding"
    }
}

private object NoOnboarding : OnboardingActions {
    override fun next() = Unit
    override fun back() = Unit
    override fun grant() = Unit
    override fun recheck() = Unit
    override fun answer(urgency: Urgency) = Unit
    override fun apply() = Unit
    override fun runProof() = Unit
    override fun finish() = Unit
}
