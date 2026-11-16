// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.allowlist

import androidx.compose.runtime.Immutable
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.PickApp
import io.github.akane599.enforcedoze.ui.StateNotice
import kotlinx.coroutines.flow.Flow

/** Where an allowlist entry comes from, as `dumpsys deviceidle whitelist` prints it (`system-excidle`, `system`, `user`). */
public enum class EntryKind { SYSTEM_EXCEPT_IDLE, SYSTEM, USER }

/** One allowlisted app. [keep] marks a user entry that Strict keeps when it trims the list (SPEC 6.1, 6.3). */
@Immutable
public data class AllowlistEntry(val packageName: String, val label: String, val kind: EntryKind, val keep: Boolean = false)

/** Why an app is suggested (SPEC 9.1). */
public sealed interface SuggestionReason {
    /** The owner said something from it arrived late; [time] is when, epoch milliseconds. */
    public data class Late(val time: Long) : SuggestionReason

    public data object Alarm : SuggestionReason

    public data object Messaging : SuggestionReason
}

@Immutable
public data class Suggestion(val packageName: String, val label: String, val reason: SuggestionReason)

/** Everything the allowlist shows, mapped by the app from the phone's list, its own keep marks and the evidence log. */
@Immutable
public data class AllowlistState(
    val entries: List<AllowlistEntry>,
    val suggestions: List<Suggestion> = emptyList(),
    /** Installed apps not on the list yet, for the picker. */
    val addable: List<PickApp> = emptyList(),
    /** False without privilege (read-only, SPEC 9.3): the list shows, nothing changes. */
    val canWrite: Boolean = true,
    val notices: List<StateNotice> = emptyList(),
) {
    public val user: List<AllowlistEntry> get() = entries.filter { it.kind == EntryKind.USER }
    public val system: List<AllowlistEntry> get() = entries.filter { it.kind != EntryKind.USER }
}

/** What the allowlist needs from the app (ADR 0029). */
public interface AllowlistSource {
    public val state: Flow<AllowlistState>

    /** `cmd deviceidle whitelist +<package>` through the worker, then read back. */
    public suspend fun add(packageName: String): ActionOutcome

    public suspend fun remove(packageName: String): ActionOutcome

    /** The keep mark is the app's own record; nothing is written to the phone. */
    public suspend fun keep(packageName: String, keep: Boolean)

    public fun seen(noticeId: String)
}
