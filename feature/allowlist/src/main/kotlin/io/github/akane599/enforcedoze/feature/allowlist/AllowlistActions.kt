// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.allowlist

import io.github.akane599.enforcedoze.ui.StateNotice

/** What the allowlist does on the owner's taps. */
public interface AllowlistActions {
    public fun add()

    public fun accept(suggestion: Suggestion)

    public fun remove(entry: AllowlistEntry)

    public fun keep(entry: AllowlistEntry, keep: Boolean)

    public fun notice(notice: StateNotice)

    public fun seen(notice: StateNotice)
}

/** Where the allowlist sends the owner; the app's navigation implements it (ADR 0029). */
public interface AllowlistNavigation {
    public fun notice(notice: StateNotice)
}
