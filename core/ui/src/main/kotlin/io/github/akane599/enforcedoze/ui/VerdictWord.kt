// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.design.Tone
import io.github.akane599.enforcedoze.evidence.Cause

/**
 * A session's verdict as every screen names it (SPEC 8.4). [NOT_JUDGED] is a session too short or with a gap
 * in its record (ADR 0020): it gets no verdict colour, so a missing record never reads as a good night.
 */
public enum class VerdictWord { HELD, PARTLY, DID_NOT_DOZE, NOT_JUDGED }

public val VerdictWord.tone: Tone
    get() = when (this) {
        VerdictWord.HELD -> Tone.GOOD
        VerdictWord.PARTLY -> Tone.WARNING
        VerdictWord.DID_NOT_DOZE -> Tone.CRITICAL
        VerdictWord.NOT_JUDGED -> Tone.NEUTRAL
    }

@Composable
public fun VerdictWord.word(): String = stringResource(
    when (this) {
        VerdictWord.HELD -> R.string.ui_verdict_held
        VerdictWord.PARTLY -> R.string.ui_verdict_partly
        VerdictWord.DID_NOT_DOZE -> R.string.ui_verdict_did_not_doze
        VerdictWord.NOT_JUDGED -> R.string.ui_verdict_not_judged
    },
)

/** The most likely cause of a night that did not doze, as one plain sentence. */
@StringRes
public fun Cause.sentence(): Int = when (this) {
    Cause.CHARGING -> R.string.ui_cause_charging
    Cause.HOLD_OFF -> R.string.ui_cause_hold_off
    Cause.MOTION -> R.string.ui_cause_motion
    Cause.CONSTANTS_NOT_APPLIED -> R.string.ui_cause_constants
    Cause.ANOTHER_MANAGER -> R.string.ui_cause_another
    Cause.UNKNOWN -> R.string.ui_cause_unknown
}
