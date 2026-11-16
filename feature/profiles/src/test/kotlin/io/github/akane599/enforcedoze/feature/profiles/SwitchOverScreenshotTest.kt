// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import io.github.akane599.enforcedoze.doze.FoundSetup
import io.github.akane599.enforcedoze.doze.KeyDifference
import io.github.akane599.enforcedoze.doze.SwitchCheck
import io.github.akane599.enforcedoze.doze.SwitchOver
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.testing.screenshots.Primary
import io.github.akane599.enforcedoze.testing.screenshots.ownerClock
import io.github.akane599.enforcedoze.testing.screenshots.shoot
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "hdpi")
class SwitchOverScreenshotTest {
    @Before fun clock() = ownerClock()

    private val at = Instant.parse("2026-11-16T06:40:00Z")

    // The owner's Tasker setup, illustrative: about ten minutes to deep idle, sensors restricted.
    private val found = FoundSetup(
        capturedAt = at,
        writer = "com.android.shell",
        constants = mapOf("inactive_to" to "300000", "idle_after_inactive_to" to "300000", "sensing_to" to "60000"),
        differences = listOf(
            KeyDifference("idle_after_inactive_to", "+5m0s0ms", "+30m0s0ms"),
            KeyDifference("inactive_to", "+5m0s0ms", "+30m0s0ms"),
            KeyDifference("sensing_to", "+1m0s0ms", "+4m0s0ms"),
        ),
        sensorsRestricted = true,
        forceIdle = false,
    )

    private val state = SwitchOverState(
        found = found,
        writer = "Shell",
        compared = listOf(BuiltInProfiles.EVERYDAY, BuiltInProfiles.POCKET),
        watching = true,
        canWrite = true,
    )

    private val imported = SwitchOver.imported(found, "Imported setup", emptySet())

    @Test fun found() = shoot(MODULE, "switch-over", tall = true) { SwitchOverScreen(state, NoSwitch) }

    @Test fun waiting() = shoot(MODULE, "switch-over-waiting", listOf(Primary, Primary.copy(dark = false, turkish = true)), tall = true) {
        SwitchOverScreen(state.copy(imported = imported, check = SwitchCheck.Waiting(at.plusSeconds(600))), NoSwitch)
    }

    @Test fun foreign() = shoot(MODULE, "switch-over-foreign", listOf(Primary), tall = true) {
        SwitchOverScreen(
            state.copy(imported = imported, check = SwitchCheck.ForeignWrite(at.plusSeconds(600), at.plusSeconds(1_500), "Tasker")),
            NoSwitch,
        )
    }

    @Test fun complete() = shoot(MODULE, "switch-over-complete", listOf(Primary, Primary.copy(dark = false, turkish = true)), tall = true) {
        SwitchOverScreen(
            state.copy(
                imported = imported,
                check = SwitchCheck.Complete(at.plusSeconds(600), at.plusSeconds(2_400)),
                managers = listOf(OtherManager("com.paget96.batteryguru", "Battery Guru")),
            ),
            NoSwitch,
        )
    }

    @Test fun stock() = shoot(MODULE, "switch-over-stock", listOf(Primary)) {
        SwitchOverScreen(
            SwitchOverState(
                found = found.copy(constants = emptyMap(), differences = emptyList(), sensorsRestricted = false),
                watching = false,
            ),
            NoSwitch,
        )
    }

    private companion object {
        const val MODULE = "feature/profiles"
    }
}

private object NoSwitch : SwitchOverActions {
    override fun import() = Unit
    override fun startCheck() = Unit
    override fun returnToPrevious() = Unit
    override fun appInfo(packageName: String) = Unit
    override fun openProfile(profileId: String) = Unit
    override val back: () -> Unit = {}
}
