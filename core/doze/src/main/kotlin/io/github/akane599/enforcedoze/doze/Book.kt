// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.parse.SettingValue

/**
 * The engine's book (ADR 0015): what it last wrote and its drift history ([persisted], kept across
 * restarts) and what it last read (everything else, which starts unknown after a restart). Confined to
 * the actor; nothing else touches it.
 */
internal class Book(var persisted: PersistedState) {
    /** What the last read showed, per lever; a missing lever is unknown. */
    val seen: MutableMap<Lever, Setting> = mutableMapOf()

    /** The Settings section of the last `dumpsys deviceidle`. */
    var effective: Map<String, SettingValue>? = null

    /** The `device_idle` keys this phone has, from the last `dumpsys deviceidle` that showed any. */
    var knownKeys: Set<String>? = null

    /** The package that last wrote the global string, from `dumpsys settings` (ADR 0014). */
    var writer: String? = null

    /** What the effective values showed against lever A's last write: null unchecked, empty when they match. */
    var constantsCheck: List<String>? = null

    /** The radios as last read (ADR 0038). */
    var radios: RadioReading? = null

    /** The chosen apps as last read, with the never-suspend list's answers (ADR 0038). */
    var suspension: SuspensionReading? = null

    val written: Map<Lever, Setting> get() = persisted.written

    /** An extra put back is not booked: from then on the owner's changes to it are theirs, not drift (ADR 0038). */
    fun wrote(setting: Setting) {
        if (setting.releasesExtra) {
            forgetWritten(setting.lever)
            return
        }
        persisted = persisted.copy(written = persisted.written + (setting.lever to setting))
    }

    fun forgetWritten(lever: Lever) {
        persisted = persisted.copy(written = persisted.written - lever)
    }
}
