// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Notice
import io.github.akane599.enforcedoze.design.Tone

/** The states every screen handles (SPEC 9.3), apart from "no data yet", which each screen words itself. */
public enum class NoticeKind { READ_ONLY, PRIVILEGE_LOST, PARTLY_APPLIED, ANOTHER_MANAGER, HOLD_OFF_ALL_NIGHT, ERROR }

/**
 * One warning. [subject] is the levers or keys concerned, the other app's name, the hold-off's name, or the
 * error's summary; [raw] is the line that caused an error, shown as it was read, never a bare "Something went
 * wrong" (SPEC 9.3). A warning holds its alert outline until [seen] (ADR 0028).
 */
@Immutable
public data class StateNotice(
    val id: String,
    val kind: NoticeKind,
    val subject: String? = null,
    val time: Long? = null,
    val raw: String? = null,
    val seen: Boolean = false,
)

/**
 * The card for one [StateNotice], worded the same on every screen. It counts as seen once it leaves the
 * screen: [onSeen] runs when the card is disposed.
 */
@Composable
public fun StateNoticeCard(
    notice: StateNotice,
    formats: Formats,
    onAction: () -> Unit,
    onSeen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = notice.subject.orEmpty()
    val (title, body, tone) = when (notice.kind) {
        NoticeKind.READ_ONLY -> Triple(
            stringResource(R.string.ui_notice_read_only),
            stringResource(R.string.ui_notice_read_only_body),
            Tone.WARNING,
        )

        NoticeKind.PRIVILEGE_LOST -> Triple(
            stringResource(R.string.ui_notice_lost),
            stringResource(R.string.ui_notice_lost_body, subject),
            Tone.CRITICAL,
        )

        NoticeKind.PARTLY_APPLIED -> Triple(
            stringResource(R.string.ui_notice_partly),
            stringResource(R.string.ui_notice_partly_body, subject),
            Tone.WARNING,
        )

        NoticeKind.ANOTHER_MANAGER -> Triple(
            stringResource(
                R.string.ui_notice_another,
                notice.subject ?: stringResource(R.string.ui_notice_another_unknown),
                notice.time?.let(formats::clock).orEmpty(),
            ),
            stringResource(R.string.ui_notice_another_body),
            Tone.WARNING,
        )

        NoticeKind.HOLD_OFF_ALL_NIGHT -> Triple(
            stringResource(R.string.ui_notice_hold_off, subject),
            stringResource(R.string.ui_notice_hold_off_body),
            Tone.WARNING,
        )

        NoticeKind.ERROR -> Triple(stringResource(R.string.ui_notice_error, subject), notice.raw.orEmpty(), Tone.CRITICAL)
    }
    val action = when (notice.kind) {
        NoticeKind.READ_ONLY -> stringResource(R.string.ui_notice_read_only_action)
        NoticeKind.PRIVILEGE_LOST -> stringResource(R.string.ui_notice_lost_action)
        else -> stringResource(R.string.ui_notice_details)
    }
    val seen by rememberUpdatedState(onSeen)
    DisposableEffect(notice.id) { onDispose { seen() } }
    Notice(
        title,
        body,
        modifier = modifier,
        tone = tone,
        seen = notice.seen,
        action = action,
        onAction = onAction,
        raw = notice.kind == NoticeKind.ERROR,
    )
}
