// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.allowlist

import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.testing.screenshots.Primary
import io.github.akane599.enforcedoze.testing.screenshots.ownerClock
import io.github.akane599.enforcedoze.testing.screenshots.shoot
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.AppPickerScreen
import io.github.akane599.enforcedoze.ui.NoticeKind
import io.github.akane599.enforcedoze.ui.PickApp
import io.github.akane599.enforcedoze.ui.StateNotice
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "hdpi")
class AllowlistScreenshotTest {
    @Before fun clock() = ownerClock()

    /** Illustrative package names only; no list from any phone. */
    private val state = AllowlistState(
        entries = listOf(
            AllowlistEntry("org.example.chat", "Chat", EntryKind.USER, keep = true),
            AllowlistEntry("org.example.mail", "Mail", EntryKind.USER),
            AllowlistEntry("com.android.phone", "Phone services", EntryKind.SYSTEM),
            AllowlistEntry("com.android.providers.downloads", "Download manager", EntryKind.SYSTEM_EXCEPT_IDLE),
        ),
        suggestions = listOf(
            Suggestion(
                "org.example.bank",
                "Bank",
                SuggestionReason.Late(ZonedDateTime.of(2026, 11, 15, 7, 40, 0, 0, ZoneId.of("Europe/Istanbul")).toInstant().toEpochMilli()),
            ),
            Suggestion("org.example.clock", "Clock", SuggestionReason.Alarm),
        ),
    )

    private val apps = listOf(
        PickApp("org.example.bank", "Bank", system = false),
        PickApp("org.example.maps", "Maps", system = false),
        PickApp("org.example.notes", "Notes", system = false),
        PickApp("com.android.settings", "Settings", system = true),
    )

    @Test fun allowlist() = shoot(MODULE, "allowlist", tall = true) { AllowlistScreen(state, NoAllowlist) }

    @Test fun readOnly() = shoot(MODULE, "allowlist-read-only", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        AllowlistScreen(
            AllowlistState(entries = state.system, canWrite = false, notices = listOf(StateNotice("ro", NoticeKind.READ_ONLY))),
            NoAllowlist,
        )
    }

    @Test fun failed() = shoot(MODULE, "allowlist-failed", listOf(Primary)) {
        AllowlistScreen(
            state,
            NoAllowlist,
            status = ChangeStatus("Bank", adding = true, ActionOutcome.Failed("Package org.example.bank: not found")),
        )
    }

    @Test fun picker() = shoot(MODULE, "picker", listOf(Primary, Primary.copy(dark = false, turkish = true, largeFont = true))) {
        AppPickerScreen(stringResource(R.string.allowlist_add), apps, onPick = {}, onBack = {})
    }

    private companion object {
        const val MODULE = "feature/allowlist"
    }
}

private object NoAllowlist : AllowlistActions {
    override fun add() = Unit
    override fun accept(suggestion: Suggestion) = Unit
    override fun remove(entry: AllowlistEntry) = Unit
    override fun keep(entry: AllowlistEntry, keep: Boolean) = Unit
    override fun notice(notice: StateNotice) = Unit
    override fun seen(notice: StateNotice) = Unit
}
