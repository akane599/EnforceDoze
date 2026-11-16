// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.settings

import io.github.akane599.enforcedoze.ui.StateNotice

/** What Settings does on the owner's taps; the route opens the confirmations and pickers. */
public interface SettingsActions {
    public fun privilege()

    public fun addKeepAwake()

    public fun removeKeepAwake(app: AppRef)

    public fun reapplyOnDrift(on: Boolean)

    public fun allowlist()

    public fun sensors()

    public fun automation(on: Boolean)

    public fun wallpaperAccent(on: Boolean)

    public fun allowTasker()

    public fun retention()

    public fun exportAll()

    public fun language()

    public fun flagSync(disabled: Boolean)

    public fun restoreStock()

    public fun reset()

    public fun licence()

    public fun notice(notice: StateNotice)

    public fun seen(notice: StateNotice)
}

/** Where Settings sends the owner; the app's navigation implements it (ADR 0029). */
public interface SettingsNavigation {
    /** The capability check (SPEC 4.4): the tier and each probe as ok or missing. */
    public fun privilege()

    /** The app picker; the chosen app joins the keep-awake list. */
    public fun addKeepAwake()

    /** The system's document picker for an evidence export of everything kept (SPEC 8.7). */
    public fun exportAll()

    /** The allowlist screen (SPEC 9.1). */
    public fun allowlist()

    /** The sensors screen (SPEC 9.1). */
    public fun sensors()

    /** The phone's per-app language page for this app. */
    public fun language()

    /** The GPL-3.0 text as shipped with the app. */
    public fun licence()

    public fun notice(notice: StateNotice)
}
