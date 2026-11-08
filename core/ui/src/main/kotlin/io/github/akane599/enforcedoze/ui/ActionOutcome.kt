// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.ui

/**
 * What came of an owner's command that writes to the phone (apply a profile, change the allowlist, turn
 * sensors off). A failure carries the line that caused it, never a bare "Something went wrong" (SPEC 9.3).
 */
public sealed interface ActionOutcome {
    public data object Done : ActionOutcome

    /** The engine is off or has no privilege; [reason] is the owner-facing line the app words for it. */
    public data class Unavailable(val reason: String) : ActionOutcome

    public data class Failed(val raw: String) : ActionOutcome
}
