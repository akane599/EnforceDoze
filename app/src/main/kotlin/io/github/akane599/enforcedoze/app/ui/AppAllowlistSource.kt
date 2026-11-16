// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import com.akylas.enforcedoze.R
import io.github.akane599.enforcedoze.feature.allowlist.AllowlistEntry
import io.github.akane599.enforcedoze.feature.allowlist.AllowlistSource
import io.github.akane599.enforcedoze.feature.allowlist.AllowlistState
import io.github.akane599.enforcedoze.feature.allowlist.EntryKind
import io.github.akane599.enforcedoze.feature.allowlist.Suggestion
import io.github.akane599.enforcedoze.feature.allowlist.SuggestionReason
import io.github.akane599.enforcedoze.parse.Allowlist
import io.github.akane599.enforcedoze.parse.DeviceIdleDump
import io.github.akane599.enforcedoze.privilege.WorkerResult
import io.github.akane599.enforcedoze.privilege.probe.LocalReader
import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.NoticeKind
import io.github.akane599.enforcedoze.ui.StateNotice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

/**
 * The allowlist (SPEC 9.1) as `cmd deviceidle whitelist` prints it through the worker, or below T2 as the app's own
 * `dumpsys deviceidle` lists it (needs `DUMP`), the keep marks from the engine's state and suggestions from the
 * owner's late reports.
 */
internal class AppAllowlistSource(private val services: AppServices) : AllowlistSource {
    private val reads = MutableStateFlow(0)
    private val local = LocalReader()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<AllowlistState> = combine(
        reads,
        services.canWrite,
        services.notices.notices,
        services.prefs.changes,
    ) { _, canWrite, notices, _ -> canWrite to notices }.mapLatest { (canWrite, notices) ->
        val read = read()
        val keep = services.graph.store.load().allowlistKeep.orEmpty()
        val entries = read.rows?.entries.orEmpty().map { row ->
            val kind = when {
                row.kind == USER -> EntryKind.USER
                row.kind.startsWith(SYSTEM_EXCEPT_IDLE) -> EntryKind.SYSTEM_EXCEPT_IDLE
                else -> EntryKind.SYSTEM
            }
            AllowlistEntry(row.packageName, services.labels.label(row.packageName) ?: row.packageName, kind, keep = row.packageName in keep)
        }.distinctBy { it.packageName to it.kind }.sortedBy { it.label.lowercase() }
        val listed = entries.map { it.packageName }.toSet()
        val readError = read.error?.let {
            val title = services.graph.app.getString(R.string.ui_error_allowlist)
            StateNotice("allowlist-read", NoticeKind.ERROR, title, raw = it)
        }
        AllowlistState(
            entries = entries,
            suggestions = suggestions(listed),
            addable = services.labels.apps(exclude = listed + services.graph.app.packageName),
            canWrite = canWrite,
            notices = notices + listOfNotNull(readError),
        )
    }

    private data class Read(val rows: Allowlist?, val error: String?)

    /** The worker's read, else the app's own; with neither, the error lines both left. */
    private suspend fun read(): Read {
        val worker = services.graph.controller.withWorker { it.readAllowlist() }
        if (worker is WorkerResult.Ok) return Read(Allowlist.parse(worker.value), null)
        val own = local.deviceIdle()
        if (own.isSuccess) return Read(Allowlist.fromDump(DeviceIdleDump.parse(own.stdout)), null)
        val ownLine = own.stderr.ifBlank { own.error.orEmpty() }.ifBlank { null }
        val lines = listOfNotNull((worker as? WorkerResult.Failed)?.error?.toString(), ownLine)
        return Read(null, lines.joinToString("\n").ifBlank { "exit ${own.exitCode}" })
    }

    /** Apps named in late reports that are not on the list yet, the latest report first (SPEC 9.1). */
    private fun suggestions(listed: Set<String>): List<Suggestion> = services.prefs.lateApps
        .filter { (app, _) -> app !in listed && services.labels.installed(app) }
        .map { (app, time) -> Suggestion(app, services.labels.label(app) ?: app, SuggestionReason.Late(time)) }

    override suspend fun add(packageName: String): ActionOutcome = change(packageName, add = true)

    override suspend fun remove(packageName: String): ActionOutcome = change(packageName, add = false)

    override suspend fun keep(packageName: String, keep: Boolean) {
        val marks = services.graph.store.load().allowlistKeep.orEmpty().let { if (keep) it + packageName else it - packageName }
        val sent = services.access.send { it.engine.setAllowlistKeep(marks).let { ActionOutcome.Done } }
        if (sent != ActionOutcome.Done) services.graph.store.save(services.graph.store.load().copy(allowlistKeep = marks))
        reads.value++
    }

    override fun seen(noticeId: String) = services.prefs.seen(noticeId)

    /** `cmd deviceidle whitelist +pkg` or `-pkg` through the worker; the list is read back afterwards. */
    private suspend fun change(packageName: String, add: Boolean): ActionOutcome {
        val result = services.graph.controller.withWorker { it.allowlist(packageName, add) }
        reads.value++
        return outcome(result)
    }

    companion object {
        /** `cmd deviceidle whitelist` rows are `system-excidle`, `system` or `user` (every `deviceidle-whitelist.txt` fixture). */
        private const val USER = "user"
        private const val SYSTEM_EXCEPT_IDLE = "system-excidle"

        fun outcome(result: WorkerResult<CommandOutcome>): ActionOutcome = when (result) {
            is WorkerResult.Ok -> if (result.value.isSuccess) {
                ActionOutcome.Done
            } else {
                val lines = listOf(result.value.stderr, result.value.stdout, result.value.error.orEmpty())
                ActionOutcome.Failed(lines.firstOrNull { it.isNotBlank() }?.trim() ?: "exit ${result.value.exitCode}")
            }

            is WorkerResult.Unavailable -> ActionOutcome.Unavailable(result.reason)
            is WorkerResult.Failed -> ActionOutcome.Failed(result.error.toString())
        }
    }
}
