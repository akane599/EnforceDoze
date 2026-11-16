// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.settings

import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.testing.screenshots.Primary
import io.github.akane599.enforcedoze.testing.screenshots.ownerClock
import io.github.akane599.enforcedoze.testing.screenshots.shoot
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.NoticeKind
import io.github.akane599.enforcedoze.ui.StateNotice
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "hdpi")
class SettingsScreenshotTest {
    @Before fun clock() = ownerClock()

    private val state = SettingsState(
        privilege = PrivilegeStatus(tier = 2, via = "Shizuku 13.6.0"),
        keepAwake = listOf(AppRef("org.example.maps", "Maps"), AppRef("org.example.music", "Music")),
        automation = true,
        tasker = TaskerState.NOT_ALLOWED,
        version = "0.1.0",
    )

    // Settings is the longest screen; shot whole, so the language, look and advanced sections are in the picture.
    @Test fun settings() = shoot(MODULE, "settings", length = SETTINGS_LENGTH) { SettingsScreen(state, NoSettings) }

    @Test fun readOnly() =
        shoot(MODULE, "settings-read-only", listOf(Primary, Primary.copy(dark = false, turkish = true)), length = SETTINGS_LENGTH) {
            SettingsScreen(
                state.copy(
                    privilege = PrivilegeStatus(tier = 0, missingProbes = 4),
                    keepAwake = emptyList(),
                    automation = false,
                    canWrite = false,
                    notices = listOf(StateNotice("ro", NoticeKind.READ_ONLY)),
                ),
                NoSettings,
            )
        }

    @Test fun failed() = shoot(MODULE, "settings-tasker-failed", listOf(Primary)) {
        SettingsScreen(
            state,
            NoSettings,
            status = CommandStatus(
                SettingsCommand.ALLOW_TASKER,
                ActionOutcome.Failed("pm grant: Unknown permission io.github.akane599.enforcedoze.permission.CONTROL"),
            ),
        )
    }

    @Test fun reset() = shoot(MODULE, "reset", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        SettingsScreen(state, NoSettings)
        ConfirmDialog(
            stringResource(R.string.settings_reset_title),
            stringResource(R.string.settings_reset_body),
            stringResource(R.string.settings_reset),
            onConfirm = {},
            onDismiss = {},
            critical = true,
        )
    }

    @Test fun retention() = shoot(MODULE, "retention", listOf(Primary, Primary.copy(turkish = true, largeFont = true))) {
        SettingsScreen(state, NoSettings)
        RetentionDialog(30, onPick = {}, onDismiss = {})
    }

    private companion object {
        const val MODULE = "feature/settings"
    }
}

private object NoSettings : SettingsActions {
    override fun privilege() = Unit
    override fun addKeepAwake() = Unit
    override fun removeKeepAwake(app: AppRef) = Unit
    override fun reapplyOnDrift(on: Boolean) = Unit
    override fun wallpaperAccent(on: Boolean) = Unit
    override fun automation(on: Boolean) = Unit
    override fun allowTasker() = Unit
    override fun retention() = Unit
    override fun exportAll() = Unit
    override fun language() = Unit
    override fun flagSync(disabled: Boolean) = Unit
    override fun restoreStock() = Unit
    override fun reset() = Unit
    override fun licence() = Unit
    override fun allowlist() = Unit
    override fun sensors() = Unit
    override fun notice(notice: StateNotice) = Unit
    override fun seen(notice: StateNotice) = Unit
}

private const val SETTINGS_LENGTH = 2520
