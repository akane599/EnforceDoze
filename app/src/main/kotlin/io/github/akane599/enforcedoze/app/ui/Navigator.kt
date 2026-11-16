// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.akane599.enforcedoze.ui.NoticeKind
import io.github.akane599.enforcedoze.ui.StateNotice

/** One back stack with Home at its root; a tab replaces what is above Home (ADR 0029). */
internal class Navigator(private val stack: NavBackStack<NavKey>) {
    val tab: Top get() = stack.lastOrNull { it is Top } as? Top ?: HomeKey

    /** The profile open above the profile list, marked in the list when both panes show. */
    val selectedProfile: String? get() = (stack.lastOrNull() as? ProfileKey)?.profileId

    fun go(key: Route) {
        stack.add(key)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun tab(top: Top) {
        stack.clear()
        stack.add(HomeKey)
        if (top != HomeKey) stack.add(top)
    }

    /** A screen opened from outside the app, on top of the tab it belongs to, so Back leads somewhere sensible. */
    fun open(key: Route) {
        when (key) {
            is ReportKey -> tab(LogKey)
            SensorsKey, is AllowlistKey -> tab(SettingsKey)
            else -> tab(HomeKey)
        }
        if (key !is Top) go(key)
    }

    fun restart(key: Route) {
        stack.clear()
        stack.add(key)
    }

    /** Where a warning's action leads (SPEC 9.3): the place where the owner can do something about it. */
    fun notice(notice: StateNotice) {
        when (notice.kind) {
            NoticeKind.READ_ONLY, NoticeKind.PRIVILEGE_LOST -> go(OnboardingKey(fromSettings = true))
            NoticeKind.PARTLY_APPLIED -> go(PlanKey)
            // SPEC 5.7: another writer gets the guided switch-over, not just the log.
            NoticeKind.ANOTHER_MANAGER, NoticeKind.TWO_MANAGERS -> go(SwitchOverKey)
            NoticeKind.HOLD_OFF_ALL_NIGHT -> tab(SettingsKey)
            NoticeKind.ERROR -> Unit
        }
    }
}
