// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.testing.screenshots.Primary
import io.github.akane599.enforcedoze.testing.screenshots.ownerClock
import io.github.akane599.enforcedoze.testing.screenshots.shoot
import io.github.akane599.enforcedoze.ui.ActionOutcome
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
class ProfilesScreenshotTest {
    @Before fun clock() = ownerClock()

    @Test fun list() = shoot(MODULE, "list", tall = true) { ProfilesScreen(ProfilesFixtures.state, NoList) }

    @Test fun listReadOnly() = shoot(MODULE, "list-read-only", listOf(Primary)) { ProfilesScreen(ProfilesFixtures.readOnly, NoList) }

    @Test fun detail() = shoot(MODULE, "detail", tall = true) {
        ProfileDetailScreen(ProfilesFixtures.state, BuiltInProfiles.NIGHT, NoDetail)
    }

    @Test fun detailCustom() = shoot(MODULE, "detail-custom", listOf(Primary, Primary.copy(dark = false, turkish = true)), tall = true) {
        ProfileDetailScreen(ProfilesFixtures.state, ProfilesFixtures.nightOut, NoDetail)
    }

    @Test fun detailNoStock() = shoot(MODULE, "detail-no-stock", listOf(Primary), tall = true) {
        ProfileDetailScreen(ProfilesFixtures.readOnly, BuiltInProfiles.EVERYDAY, NoDetail)
    }

    @Test fun detailStock() = shoot(MODULE, "detail-stock", listOf(Primary)) {
        ProfileDetailScreen(ProfilesFixtures.state, BuiltInProfiles.STOCK, NoDetail)
    }

    @Test fun detailFailed() = shoot(MODULE, "detail-failed", listOf(Primary)) {
        ProfileDetailScreen(
            ProfilesFixtures.state,
            BuiltInProfiles.POCKET,
            NoDetail,
            status = ApplyStatus(Slot.DAY, ActionOutcome.Failed("device_idle_constants: read back inactive_to=1800000, expected 300000")),
        )
    }

    @Test fun editor() = shoot(MODULE, "editor", tall = true) { ProfileEditorScreen(editor(ProfilesFixtures.nightOut), NoEditor) }

    @Test fun editorProblems() = shoot(
        MODULE,
        "editor-problems",
        listOf(Primary, Primary.copy(turkish = true, largeFont = true)),
        tall = true,
    ) {
        val draft = ProfilesFixtures.nightOut.toDraft().let {
            it.copy(
                name = "",
                values =
                it.values + ("sensing_to" to "5") + ("idle_factor" to "6"),
            )
        }
        ProfileEditorScreen(editor(ProfilesFixtures.nightOut).copy(draft = draft), NoEditor)
    }

    @Test fun clone() = shoot(MODULE, "clone", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        ProfilesScreen(ProfilesFixtures.state, NoList)
        CloneDialog(BuiltInProfiles.NIGHT, onClone = {}, onDismiss = {})
    }

    private fun editor(profile: Profile) = profile.toDraft().let { EditorState(it, it, ProfilesFixtures.stock, inUse = false) }

    private companion object {
        const val MODULE = "feature/profiles"
    }
}

private object NoList : ProfilesActions {
    override fun open(profileId: String) = Unit
    override fun clone(profile: Profile) = Unit
    override fun notice(notice: StateNotice) = Unit
    override fun seen(notice: StateNotice) = Unit
}

private object NoDetail : ProfileDetailActions {
    override val back: (() -> Unit) = {}
    override fun apply(slot: Slot) = Unit
    override fun clone() = Unit
    override fun edit() = Unit
    override fun export() = Unit
    override fun notice(notice: StateNotice) = Unit
    override fun seen(notice: StateNotice) = Unit
}

private object NoEditor : ProfileEditorActions {
    override fun back() = Unit
    override fun edit(draft: Draft) = Unit
    override fun save() = Unit
    override fun delete() = Unit
}
