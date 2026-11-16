// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.settings

import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.testing.screenshots.Primary
import io.github.akane599.enforcedoze.testing.screenshots.ownerClock
import io.github.akane599.enforcedoze.testing.screenshots.shoot
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
        clients = listOf(AutomationClient("com.example.macro", "MacroDroid", 1_789_000_000_000L)),
        taskerOffer = "net.dinglisch.android.taskerm",
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

    /** ADR 0038 at T3: Wi-Fi off chosen, and one chosen app held back by the never-suspend list. */
    @Test fun extras() = shoot(
        MODULE,
        "settings-extras",
        listOf(Primary, Primary.copy(dark = false, turkish = true)),
        length = SETTINGS_LENGTH,
    ) {
        SettingsScreen(
            state.copy(
                privilege = PrivilegeStatus(tier = 3, via = "Sui"),
                extras = ExtrasState(
                    available = true,
                    wifiOff = true,
                    suspend = listOf(
                        SuspendApp(AppRef("org.example.feed", "Feed")),
                        SuspendApp(AppRef("org.example.chat", "Chat"), heldBack = "it can ring for calls"),
                    ),
                ),
            ),
            NoSettings,
        )
    }

    // The token is shown once, with a copy button; only its hash is kept (ADR 0033).
    @Test fun token() = shoot(MODULE, "settings-token", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        SettingsScreen(state.copy(newToken = NewToken("Tasker", "3f9c0a7be41d26c58e0b71f4a9d3c2e6")), NoSettings)
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
    override fun wifiOff(on: Boolean) = Unit
    override fun dataOff(on: Boolean) = Unit
    override fun addSuspend() = Unit
    override fun removeSuspend(app: AppRef) = Unit
    override fun reapplyOnDrift(on: Boolean) = Unit
    override fun wallpaperAccent(on: Boolean) = Unit
    override fun automation(on: Boolean) = Unit
    override fun addClient() = Unit
    override fun makeToken(packageName: String) = Unit
    override fun removeClient(packageName: String) = Unit
    override fun copyToken(token: String) = Unit
    override fun tokenShown() = Unit
    override fun retention() = Unit
    override fun exportAll() = Unit
    override fun language() = Unit
    override fun flagSync(disabled: Boolean) = Unit
    override fun restoreStock() = Unit
    override fun reset() = Unit
    override fun licence() = Unit
    override fun allowlist() = Unit
    override fun sensors() = Unit
    override fun switchOver() = Unit
    override fun notice(notice: StateNotice) = Unit
    override fun seen(notice: StateNotice) = Unit
}

private const val SETTINGS_LENGTH = 2640
