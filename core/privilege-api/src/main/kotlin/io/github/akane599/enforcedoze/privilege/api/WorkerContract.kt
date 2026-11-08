// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.api

/** Names and limits shared by the worker and its clients (SPEC 4.3, 12.4). */
public object WorkerContract {
    /** Raised whenever the AIDL or the meaning of a method changes. 2: device_config writes removed (ADR 0012). */
    public const val VERSION: Int = 2

    /** The UserService class, loaded by name in the worker process (Shizuku and Sui). */
    public const val WORKER_CLASS: String = "io.github.akane599.enforcedoze.privilege.worker.DozeWorker"

    /** The libsu RootService that hosts the same worker for the root tier. */
    public const val ROOT_SERVICE_CLASS: String = "io.github.akane599.enforcedoze.privilege.worker.DozeRootService"

    /** Process name suffix for the Shizuku UserService. */
    public const val PROCESS_SUFFIX: String = "worker"

    /** Every write has a 5 s timeout (SPEC 12.4). */
    public const val WRITE_TIMEOUT_MILLIS: Long = 5_000

    /** Reads stream through a pipe and may be large (battery history); they get longer. */
    public const val READ_TIMEOUT_MILLIS: Long = 30_000

    /** No connection within this time marks the worker unavailable for the session (SPEC 4.3). */
    public const val BIND_TIMEOUT_MILLIS: Long = 5_000

    /** Longest wait for an unbound worker's disconnect before the next bind; not counted in [BIND_TIMEOUT_MILLIS]. */
    public const val UNBIND_SETTLE_MILLIS: Long = 3_000

    /** The worker unbinds this long after its last call (SPEC 4.3). */
    public const val IDLE_UNBIND_MILLIS: Long = 60_000

    /** `cmd deviceidle force-idle [light|deep]` (API 37 help). */
    public val FORCE_MODES: Set<String> = setOf("deep", "light")

    /** `cmd deviceidle get [light|deep|force|screen|charging|...]` (API 37 help); the subset the app reads. */
    public val IDLE_QUERIES: Set<String> = setOf("deep", "light", "force", "screen", "charging")

    /** Permissions the worker may grant to this app (SPEC 4.1, tier T1). */
    public val SELF_PERMISSIONS: Set<String> = setOf(
        "android.permission.DUMP",
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.READ_LOGS",
    )

    /** App ops the worker may set for this app (SPEC 4.1, tier T1). */
    public val SELF_APP_OPS: Set<String> = setOf("GET_USAGE_STATS", "SCHEDULE_EXACT_ALARM")

    public val APP_OP_MODES: Set<String> = setOf("allow", "ignore", "default")

    /** The only `device_config` namespace the worker reads. */
    public const val DEVICE_IDLE_NAMESPACE: String = "device_idle"

    /** The global setting lever A writes from the app (`DeviceIdleSettings.SETTING` in `:core:parse`, ADR 0012); the worker reads it. */
    public const val CONSTANTS_SETTING: String = "device_idle_constants"
}
