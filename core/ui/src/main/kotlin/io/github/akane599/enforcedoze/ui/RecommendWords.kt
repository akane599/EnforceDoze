// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.recommend.Confidence
import io.github.akane599.enforcedoze.recommend.Note
import io.github.akane599.enforcedoze.recommend.Reason
import io.github.akane599.enforcedoze.recommend.Unmeasured
import io.github.akane599.enforcedoze.recommend.Urgency
import java.time.Duration

/**
 * One line of the "why" card in plain words, with its numbers (SPEC 7.4). [name] turns a profile id into the
 * name the owner sees. Recommended and onboarding share these lines.
 */
@Composable
public fun Reason.sentence(formats: Formats, name: (String) -> String): String = when (this) {
    is Reason.ThinHistory -> stringResource(R.string.ui_why_thin, formats.duration(screenOff.toMillis()))

    is Reason.ScreenOffPerDay -> stringResource(R.string.ui_why_screen_off, formats.duration(quarterHours(hours)))

    is Reason.MotionKeptAwake -> stringResource(R.string.ui_why_motion_kept, sessions, of, name(POCKET))

    is Reason.MotionResets -> stringResource(R.string.ui_why_motion_resets, formats.decimal(perHour, 1), name(POCKET))

    is Reason.ShortSessions -> stringResource(R.string.ui_why_short, formats.percentText(share), name(CLASSIC))

    is Reason.MessagesRightAway -> stringResource(R.string.ui_why_right_away, dropped.joinToString(", ") { name(it) })

    is Reason.UnpluggedNights ->
        stringResource(R.string.ui_why_nights, nights, of, name(NIGHT), formats.time(start), formats.time(end))

    is Reason.IrregularNights ->
        stringResource(R.string.ui_why_irregular, nights, of, formats.duration(spread.toMillis()))

    is Reason.DozingWell -> stringResource(R.string.ui_why_dozing_well, formats.percentText(deepShare), formats.drainText(drainPerHour))

    is Reason.LowGain -> pluralStringResource(R.plurals.ui_why_low_gain, nights, nights, formats.percentText(gain.coerceAtLeast(0.0)))

    is Reason.ArrivedLate -> stringResource(R.string.ui_why_late, formats.date(time.toEpochMilli()), formats.clock(time.toEpochMilli()))
}

/** A note beside the plan (SPEC 7.3, 7.4). */
@Composable
public fun Note.line(name: (String) -> String): String = when (this) {
    Note.REFINES_AFTER_FIRST_NIGHT -> stringResource(R.string.ui_note_refines)
    Note.LITTLE_TO_GAIN -> stringResource(R.string.ui_note_little_to_gain)
    Note.STOCK_NOT_MEASURED -> stringResource(R.string.ui_note_stock_not_measured, name(STOCK))
    Note.GENTLER -> stringResource(R.string.ui_note_gentler)
    Note.MOTION_NOT_MEASURED -> stringResource(R.string.ui_note_motion_not_measured, name(POCKET))
    Note.NIGHT_BY_HAND -> stringResource(R.string.ui_note_night_by_hand, name(NIGHT))
}

@Composable
public fun Confidence.word(): String = stringResource(
    when (this) {
        Confidence.LOW -> R.string.ui_confidence_low
        Confidence.MEDIUM -> R.string.ui_confidence_medium
        Confidence.HIGH -> R.string.ui_confidence_high
    },
)

/** The answer to the urgency question (SPEC 7.2 Q). */
@Composable
public fun Urgency.word(): String = stringResource(
    when (this) {
        Urgency.RIGHT_AWAY -> R.string.ui_urgency_right_away
        Urgency.WITHIN_THE_HOUR -> R.string.ui_urgency_within_hour
        Urgency.IT_CAN_WAIT -> R.string.ui_urgency_can_wait
    },
)

/** Why a signal has no number, instead of a guess (ADR 0026). */
@Composable
public fun Unmeasured.line(): String = stringResource(
    when (this) {
        Unmeasured.NO_SESSIONS -> R.string.ui_unmeasured_no_sessions
        Unmeasured.NO_STOCK_SESSIONS -> R.string.ui_unmeasured_no_stock
        Unmeasured.NOT_RECORDED -> R.string.ui_unmeasured_not_recorded
        Unmeasured.SENSORS_RESTRICTED -> R.string.ui_unmeasured_sensors_restricted
    },
)

/** Hours a day, to the nearest quarter hour, in milliseconds: "about 9 h 15 min" reads better than 9 h 13 min. */
private fun quarterHours(hours: Double): Long {
    val quarter = Duration.ofMinutes(QUARTER_MINUTES).toMillis()
    return Math.round(hours * Duration.ofHours(1).toMillis() / quarter) * quarter
}

private const val QUARTER_MINUTES = 15L
private const val STOCK = "stock"
private const val POCKET = "pocket"
private const val CLASSIC = "classic"
private const val NIGHT = "night"
