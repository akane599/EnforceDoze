// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.settings

import androidx.compose.runtime.Immutable
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.StateNotice
import kotlinx.coroutines.flow.Flow

/** The privilege tier (SPEC 4: T0 normal permissions, T1 durable grants, T2 shell worker, T3 root) and what gives it. */
@Immutable
public data class PrivilegeStatus(val tier: Int, val via: String? = null, val missingProbes: Int = 0)

/** An installed app as the owner sees it. */
@Immutable
public data class AppRef(val packageName: String, val label: String)

/** An app the automation API answers (ADR 0033): a token is bound to it, made at [created]. */
@Immutable
public data class AutomationClient(val packageName: String, val label: String, val created: Long)

/** A token just made, shown once with a copy button. */
@Immutable
public data class NewToken(val label: String, val token: String)

/** Everything Settings shows, mapped by the app from its preferences, the engine and the privilege session. */
@Immutable
public data class SettingsState(
    val privilege: PrivilegeStatus,
    /** Apps that hold off forced idle and sensor restriction when in the foreground at screen off (SPEC 5.5). */
    val keepAwake: List<AppRef> = emptyList(),
    /** Re-apply a profile's constants when another writer changed them (SPEC 5.6), on by default. */
    val reapplyOnDrift: Boolean = true,
    /** The automation API receiver (SPEC 11.1), off until the owner turns it on. */
    val automation: Boolean = false,
    /** The apps with a token, in the order they were added. */
    val clients: List<AutomationClient> = emptyList(),
    /** Tasker's package when it is installed and has no token yet: offered with one tap. */
    val taskerOffer: String? = null,
    val newToken: NewToken? = null,
    /** Days of parsed records kept (SPEC 8.3): one of [RETENTION_CHOICES]. */
    val retentionDays: Int = DEFAULT_RETENTION,
    /** The app's own language as its name, or null when it follows the phone. */
    val language: String? = null,
    /** SPEC 10.2: the accent may follow the wallpaper; verdict and strip colours never do. Off by default. */
    val wallpaperAccent: Boolean = false,
    /** Server flag sync disabled for every namespace (SPEC 5.6): advanced, off by default, never set automatically. */
    val flagSyncDisabled: Boolean = false,
    val version: String,
    /** False without privilege (read-only, SPEC 9.3). */
    val canWrite: Boolean = true,
    val notices: List<StateNotice> = emptyList(),
) {
    public companion object {
        public const val DEFAULT_RETENTION: Int = 30

        /** SPEC 8.3: 7 to 90 days. */
        public val RETENTION_CHOICES: List<Int> = listOf(7, 14, 30, 60, 90)
    }
}

/** A command Settings sends to the phone, named in its outcome line. */
public enum class SettingsCommand { RESTORE_STOCK, FLAG_SYNC, RESET }

/** What Settings needs from the app (ADR 0029). */
public interface SettingsSource {
    public val state: Flow<SettingsState>

    public suspend fun reapplyOnDrift(on: Boolean)

    public suspend fun automation(on: Boolean)

    public suspend fun wallpaperAccent(on: Boolean)

    public suspend fun retention(days: Int)

    public suspend fun removeKeepAwake(packageName: String)

    /** A new token for [packageName], replacing its old one (ADR 0033). */
    public suspend fun makeToken(packageName: String)

    public suspend fun removeClient(packageName: String)

    public fun tokenShown()

    public suspend fun flagSync(disabled: Boolean): ActionOutcome

    /** Restore stock (SPEC 5.6 step 3): the global string deleted, unforce, sensors enabled, allowlist as captured. */
    public suspend fun restoreStock(): ActionOutcome

    /** Restores stock, then clears the app's records and settings. */
    public suspend fun reset(): ActionOutcome

    public fun seen(noticeId: String)
}
