// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import android.app.LocaleManager
import io.github.akane599.enforcedoze.app.EngineService
import com.akylas.enforcedoze.R
import io.github.akane599.enforcedoze.doze.JobResult
import io.github.akane599.enforcedoze.feature.settings.AppRef
import io.github.akane599.enforcedoze.feature.settings.PrivilegeStatus
import io.github.akane599.enforcedoze.feature.settings.SettingsSource
import io.github.akane599.enforcedoze.feature.settings.SettingsState
import io.github.akane599.enforcedoze.feature.settings.TaskerState
import io.github.akane599.enforcedoze.privilege.PrivilegeState
import io.github.akane599.enforcedoze.privilege.WorkerSource
import io.github.akane599.enforcedoze.privilege.probe.ProbeStatus
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.akane599.enforcedoze.privilege.WorkerResult
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import java.io.File
import java.util.Locale

/** Settings (SPEC 9.1) over the app's preferences, the engine's saved choices and the privilege layer. */
internal class AppSettingsSource(private val services: AppServices) : SettingsSource {
    private val app = services.graph.app
    private val syncReads = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<SettingsState> = combine(
        services.graph.controller.state,
        services.engine,
        services.prefs.changes,
        services.notices.notices,
        combine(services.capabilities, syncReads) { report, _ -> report },
    ) { privilege, _, _, notices, report -> Triple(privilege, notices, report) }
        .mapLatest { (privilege, notices, report) ->
            val canWrite = services.notices.canWrite(privilege)
            SettingsState(
                privilege = PrivilegeStatus(
                    tier = privilege.tier.ordinal,
                    via = via(privilege),
                    missingProbes = report?.results?.count { it.status == ProbeStatus.MISSING || it.status == ProbeStatus.FAILED } ?: 0,
                ),
                keepAwake = services.prefs.keepAwake.map { AppRef(it, services.labels.label(it) ?: it) }.sortedBy { it.label.lowercase() },
                // The engine saves its state after every command, so the saved file holds the choice either way.
                reapplyOnDrift = services.graph.store.load().reapplyOnDrift,
                automation = services.prefs.automation,
                tasker = if (services.labels.installed(TASKER)) TaskerState.NOT_ALLOWED else TaskerState.NOT_INSTALLED,
                retentionDays = services.prefs.retentionDays,
                language = language(),
                wallpaperAccent = services.prefs.wallpaperAccent,
                flagSyncDisabled = canWrite && flagSyncMode()?.let { it != SYNC_ON } == true,
                version = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty(),
                canWrite = canWrite,
                notices = notices,
            )
        }

    override suspend fun reapplyOnDrift(on: Boolean) {
        // Running: through the engine, which saves it. Off: in the saved state the engine starts from.
        val outcome = services.access.send { it.engine.setReapplyOnDrift(on).let { ActionOutcome.Done } }
        if (outcome != ActionOutcome.Done) services.graph.store.save(services.graph.store.load().copy(reapplyOnDrift = on))
        services.prefs.touch()
    }

    override suspend fun automation(on: Boolean) {
        services.prefs.automation = on
    }

    override suspend fun wallpaperAccent(on: Boolean) {
        services.prefs.wallpaperAccent = on
    }

    override suspend fun retention(days: Int) {
        services.prefs.retentionDays = days
    }

    override suspend fun removeKeepAwake(packageName: String) {
        services.prefs.keepAwake = services.prefs.keepAwake - packageName
    }

    /** The automation permission arrives with SPEC 11 (phase P6); until then there is nothing to grant. */
    override suspend fun allowTasker(): ActionOutcome = ActionOutcome.Unavailable(app.getString(R.string.ui_not_in_build))

    /** The phone's mode, read through the worker; null when it cannot be read. */
    private suspend fun flagSyncMode(): String? =
        (services.graph.controller.withWorker { it.flagSync() } as? WorkerResult.Ok)?.value?.takeIf { it.isSuccess }?.stdout?.trim()

    /**
     * Stops server flag sync for good (`persistent`) or lets it run again (`none`), then reads it back (SPEC 5.6). The
     * app remembers that it stopped sync, so Restore stock and Reset can let it run again.
     */
    override suspend fun flagSync(disabled: Boolean): ActionOutcome {
        val mode = if (disabled) SYNC_STOPPED else SYNC_ON
        val result = services.graph.controller.withWorker { it.setFlagSync(mode) }
        syncReads.value++
        val outcome = AppAllowlistSource.outcome(result)
        if (outcome != ActionOutcome.Done) return outcome
        val now = flagSyncMode()
        if (now != mode) return ActionOutcome.Failed("device_config get_sync_disabled_for_tests: ${now ?: "unreadable"}, wanted $mode")
        services.prefs.flagSyncStopped = disabled
        return ActionOutcome.Done
    }

    /** The engine's Restore stock, then server flag sync running again when the app had stopped it (SPEC 0.2). */
    override suspend fun restoreStock(): ActionOutcome {
        val restored = services.access.send(turnOn = true) { outcome(it.engine.restoreStock()) }
        if (restored != ActionOutcome.Done || !services.prefs.flagSyncStopped) return restored
        return flagSync(disabled = false)
    }

    /** Restore stock first; only when the phone is back to stock are the app's own records and settings removed. */
    override suspend fun reset(): ActionOutcome {
        val restored = restoreStock()
        if (restored != ActionOutcome.Done) return restored
        val graph = services.graph
        graph.switch.enabled = false
        EngineService.stop(app)
        graph.stop()
        graph.sessions.deleteSessionsBefore(Long.MAX_VALUE)
        graph.journal.deleteActionsBefore(Long.MAX_VALUE)
        graph.journal.deleteEventsBefore(Long.MAX_VALUE)
        graph.recorderFiles.pruneRaw(Long.MAX_VALUE)
        graph.recorderFiles.clearOpen()
        listOf(ENGINE_DIR, PROFILES_DIR).forEach { File(app.filesDir, it).deleteRecursively() }
        services.prefs.clear()
        services.profilesChanged()
        return ActionOutcome.Done
    }

    override fun seen(noticeId: String) = services.prefs.seen(noticeId)

    private fun via(privilege: PrivilegeState): String? = when (privilege.workerSource) {
        WorkerSource.SHIZUKU -> app.getString(R.string.ui_via_shizuku)
        WorkerSource.SUI -> app.getString(R.string.ui_via_sui)
        WorkerSource.ROOT -> app.getString(R.string.ui_via_root)
        null -> null
    }

    /** The app's own language (Android's per-app language), or null when it follows the phone. */
    private fun language(): String? {
        val locales = app.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) null else locales[0].getDisplayName(locales[0]).replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    private companion object {
        /** `device_config` sync modes (`WorkerContract.SYNC_DISABLED_MODES`). */
        private const val SYNC_ON = "none"
        private const val SYNC_STOPPED = "persistent"

        const val TASKER = "net.dinglisch.android.taskerm"
        const val ENGINE_DIR = "engine"
        const val PROFILES_DIR = "profiles"

        /** A job's result as one outcome: refused, or stock not confirmed by read-back, is never shown as done. */
        fun outcome(result: JobResult): ActionOutcome = when {
            result.refused != null -> ActionOutcome.Unavailable(result.refused.orEmpty())
            !result.stockDifferences.isNullOrEmpty() -> ActionOutcome.Failed(result.stockDifferences.orEmpty().joinToString("\n"))
            result.unverified != null -> ActionOutcome.Failed(result.unverified.orEmpty())
            else -> ActionOutcome.Done
        }
    }
}
