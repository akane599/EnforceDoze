// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.plan

import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.recommend.Urgency
import io.github.akane599.enforcedoze.testing.screenshots.Primary
import io.github.akane599.enforcedoze.testing.screenshots.ownerClock
import io.github.akane599.enforcedoze.testing.screenshots.shoot
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.CheckState
import io.github.akane599.enforcedoze.ui.LeverCheck
import io.github.akane599.enforcedoze.ui.StateNotice
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import io.github.akane599.enforcedoze.ui.R as UiR

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "hdpi")
class PlanScreenshotTest {
    @Before fun clock() = ownerClock()

    @Test fun plan() = shoot(MODULE, "plan", tall = true) { PlanScreen(PlanFixtures.state, NoPlan) }

    @Test fun planDayOnly() = shoot(MODULE, "plan-day-only", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        PlanScreen(PlanFixtures.byHand, NoPlan)
    }

    @Test fun planReadOnly() = shoot(MODULE, "plan-read-only", listOf(Primary)) { PlanScreen(PlanFixtures.readOnly, NoPlan) }

    @Test fun planFailed() = shoot(MODULE, "plan-failed", listOf(Primary)) {
        PlanScreen(
            PlanFixtures.state,
            NoPlan,
            outcome = ActionOutcome.Failed("device_idle_constants: read back idle_to=3600000, expected 900000"),
        )
    }

    // SPEC 10.3: each lever checks in with what the phone read back; the screen is on, so the screen-off levers wait.
    @Test fun planApplied() =
        shoot(MODULE, "plan-applied", listOf(Primary, Primary.copy(dark = false, turkish = true, largeFont = true)), tall = true) {
            val checks = listOf(
                LeverCheck(
                    stringResource(UiR.string.ui_lever_constants),
                    CheckState.APPLIED,
                    "inactive_to=180000,motion_inactive_to=0,idle_after_inactive_to=420000,light_after_inactive_to=60000",
                ),
                LeverCheck(stringResource(UiR.string.ui_lever_force_idle), CheckState.ARMED, null),
                LeverCheck(stringResource(UiR.string.ui_lever_sensors), CheckState.ARMED, null),
                LeverCheck(stringResource(UiR.string.ui_lever_allowlist), CheckState.APPLIED, "+org.example.chat +org.example.mail"),
            )
            PlanScreen(PlanFixtures.state, NoPlan, outcome = ActionOutcome.Applied(checks))
        }

    @Test fun recommended() = shoot(MODULE, "recommended", tall = true) { RecommendedScreen(PlanFixtures.state, NoRecommended) }

    @Test fun recommendedInUse() = shoot(MODULE, "recommended-in-use", listOf(Primary, Primary.copy(dark = false))) {
        RecommendedScreen(PlanFixtures.inUse, NoRecommended)
    }

    @Test fun recommendedByHand() = shoot(MODULE, "recommended-by-hand", tall = true) {
        RecommendedScreen(PlanFixtures.byHand, NoRecommended)
    }

    @Test fun recommendedEmpty() = shoot(MODULE, "recommended-empty", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        RecommendedScreen(PlanFixtures.empty, NoRecommended)
    }

    @Test fun pickProfile() = shoot(MODULE, "pick-profile", listOf(Primary, Primary.copy(turkish = true, largeFont = true))) {
        PlanScreen(PlanFixtures.state, NoPlan)
        ProfilePickerDialog(stringResource(R.string.plan_pick_day), BuiltInProfiles.ALL, BuiltInProfiles.EVERYDAY.id, onPick = {
        }, onDismiss = {})
    }

    @Test fun pickEdge() = shoot(MODULE, "pick-edge", listOf(Primary, Primary.copy(dark = false, largeFont = true))) {
        PlanScreen(PlanFixtures.state, NoPlan)
        EdgePickerDialog(stringResource(R.string.plan_pick_start), LocalTime.of(23, 0), LocalTime.of(7, 30), onPick = {}, onDismiss = {})
    }

    @Test fun urgency() = shoot(MODULE, "urgency", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        RecommendedScreen(PlanFixtures.state, NoRecommended)
        UrgencyDialog(Urgency.WITHIN_THE_HOUR, onPick = {}, onDismiss = {})
    }

    private companion object {
        const val MODULE = "feature/plan"
    }
}

private object NoPlan : PlanActions {
    override val back: (() -> Unit) = {}
    override fun pickDay() = Unit
    override fun pickNight() = Unit
    override fun night(on: Boolean) = Unit
    override fun editEdge(start: Boolean) = Unit
    override fun follow(on: Boolean) = Unit
    override fun recommended() = Unit
    override fun notice(notice: StateNotice) = Unit
    override fun seen(notice: StateNotice) = Unit
}

private object NoRecommended : RecommendedActions {
    override val back: (() -> Unit) = {}
    override fun apply() = Unit
    override fun somethingElse() = Unit
    override fun changeUrgency() = Unit
    override fun notice(notice: StateNotice) = Unit
    override fun seen(notice: StateNotice) = Unit
}
