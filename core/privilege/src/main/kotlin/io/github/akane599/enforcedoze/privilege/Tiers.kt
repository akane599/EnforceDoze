// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

/** Privilege tiers of SPEC 4.1. */
public enum class Tier {
    /** Normal permissions only. */
    T0,

    /** Durable grants: at least `DUMP`, so the app reads Doze and sensor state itself. */
    T1,

    /** The worker runs as shell (uid 2000). */
    T2,

    /** The worker runs as root. */
    T3,
}

/** Durable grants of tier T1 (SPEC 4.1). */
public enum class Grant {
    DUMP,
    WRITE_SECURE_SETTINGS,
    READ_LOGS,

    /** The `GET_USAGE_STATS` app op (usage access). */
    USAGE_STATS,

    /** Exact alarms, needed only by the optional delay alarm (SPEC 5.3). */
    EXACT_ALARM,
}

/** What the Shizuku (or Sui) client knows about its server. */
public sealed interface ShizukuStatus {
    /** No binder received: Shizuku is not installed or not started. */
    public data object NotRunning : ShizukuStatus

    /** A server older than API 11, which the client library no longer supports. */
    public data object Unsupported : ShizukuStatus

    /** Running, but this app is not authorised; [canRequest] is false after "deny, don't ask again". */
    public data class PermissionNeeded(val canRequest: Boolean) : ShizukuStatus

    /** Authorised. [uid] is the server's: 0 for root, 2000 for shell. [sui] when the server is Sui. */
    public data class Ready(val uid: Int, val version: Int, val sui: Boolean) : ShizukuStatus
}

/** Where the worker process comes from. */
public enum class WorkerSource { SHIZUKU, SUI, ROOT }

public sealed interface WorkerStatus {
    /** Not connected; it connects on demand (SPEC 4.3: bind for short jobs, unbind after 60 s). */
    public data object Idle : WorkerStatus

    public data class Bound(val source: WorkerSource, val uid: Int) : WorkerStatus

    /** No connection in time: unavailable for the rest of the session (SPEC 4.3). */
    public data class Unavailable(val source: WorkerSource, val reason: String) : WorkerStatus
}

/** Everything that decides the tier. */
public data class PrivilegeState(
    val grants: Set<Grant>,
    val shizuku: ShizukuStatus,
    /** libsu reports that this app was already granted root; never asked for implicitly. */
    val rootGranted: Boolean,
    val worker: WorkerStatus,
) {
    val tier: Tier get() = TierResolver.tier(this)

    /** The worker source the tier relies on, or null below T2. */
    val workerSource: WorkerSource? get() = TierResolver.source(this)

    public companion object {
        public val NONE: PrivilegeState = PrivilegeState(emptySet(), ShizukuStatus.NotRunning, false, WorkerStatus.Idle)
    }
}

/**
 * Pure tier rules. A worker source counts while it has not been marked unavailable this session; the
 * highest tier wins and Shizuku or Sui is preferred over libsu at the same tier.
 */
public object TierResolver {
    private const val ROOT_UID = 0

    public fun tier(state: PrivilegeState): Tier {
        val best = candidates(state).maxByOrNull { it.second.ordinal }
        return best?.second ?: if (Grant.DUMP in state.grants) Tier.T1 else Tier.T0
    }

    public fun source(state: PrivilegeState): WorkerSource? = candidates(state).maxByOrNull { it.second.ordinal }?.first

    private fun candidates(state: PrivilegeState): List<Pair<WorkerSource, Tier>> = buildList {
        val shizuku = state.shizuku
        if (shizuku is ShizukuStatus.Ready) {
            val source = if (shizuku.sui) WorkerSource.SUI else WorkerSource.SHIZUKU
            add(source to if (shizuku.uid == ROOT_UID) Tier.T3 else Tier.T2)
        }
        if (state.rootGranted) add(WorkerSource.ROOT to Tier.T3)
    }.filterNot { (source, _) ->
        val worker = state.worker
        worker is WorkerStatus.Unavailable && worker.source == source
    } // Shizuku or Sui first: maxByOrNull keeps the first of equal tiers.
}
