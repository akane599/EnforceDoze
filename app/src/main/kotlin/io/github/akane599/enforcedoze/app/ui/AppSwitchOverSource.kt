// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.akane599.enforcedoze.data.JournalRows
import io.github.akane599.enforcedoze.doze.ForeignWriteSeen
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.doze.LeverState
import io.github.akane599.enforcedoze.doze.Mode
import io.github.akane599.enforcedoze.doze.SwitchOver
import io.github.akane599.enforcedoze.feature.profiles.SwitchOverSource
import io.github.akane599.enforcedoze.feature.profiles.SwitchOverState
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import java.time.Instant

/** The switch-over (SPEC 11.4) over `previous.json`, `stock.json`, the drift log and the recorded sessions. */
internal class AppSwitchOverSource(private val services: AppServices) : SwitchOverSource {
    private val graph = services.graph

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<SwitchOverState> = combine(
        services.engine,
        services.profiles,
        graph.journal.latestEvents(RECENT_EVENTS),
        services.evidence,
        services.prefs.changes,
    ) { engine, profiles, events, _, _ -> Triple(engine, profiles, events) }
        .mapLatest { (engine, profiles, events) ->
            val previous = graph.store.previous()
            val since = services.prefs.switchCheckSince?.let(Instant::ofEpochMilli)
            // Any drift not written by this app is another writer, named when the tier could see who (ADR 0014).
            val writes = events.filter { it.kind == JournalRows.DRIFT && it.writer != graph.app.packageName }
                .map { ForeignWriteSeen(Instant.ofEpochMilli(it.time), it.writer?.let(::name)) }
            val sessions = since?.let { graph.sessions.sessionsBetween(it.toEpochMilli(), System.currentTimeMillis()) }.orEmpty()
                .map { Instant.ofEpochMilli(it.start) to Instant.ofEpochMilli(it.end) }
            val constants = engine.status?.levers?.get(Lever.CONSTANTS)?.state
            SwitchOverState(
                found = previous?.let { SwitchOver.found(it, graph.store.stock()) },
                writer = previous?.writer?.let(::name),
                managers = OtherManagers.installed(services.labels),
                imported = services.prefs.importedProfile?.let { id -> profiles.firstOrNull { it.id == id } },
                compared = listOf(BuiltInProfiles.EVERYDAY, BuiltInProfiles.POCKET),
                check = SwitchOver.check(since, writes, sessions),
                watching = engine.running && engine.mode == Mode.MANAGED && constants != null && constants != LeverState.UNUSED,
                handsOff = engine.running && engine.mode == Mode.HANDS_OFF,
                canWrite = services.notices.canWrite(graph.controller.state.value),
            )
        }

    override suspend fun import(name: String): List<String> {
        val previous = graph.store.previous() ?: return listOf("previous.json: not captured yet")
        val found = SwitchOver.found(previous, graph.store.stock())
        val taken = services.profiles.first().map { it.id }.toSet()
        val profile = SwitchOver.imported(found, name, taken)
        val problems = graph.profiles.store.save(profile, services.stockMillis())
        if (problems.isNotEmpty()) return problems.map { it.toString() }
        services.prefs.importedProfile = profile.id
        services.profilesChanged()
        return emptyList()
    }

    override suspend fun startCheck() {
        services.prefs.switchCheckSince = System.currentTimeMillis()
    }

    override suspend fun returnToPrevious(): ActionOutcome =
        services.access.send(turnOn = true) { AppSettingsSource.outcome(it.engine.returnToPrevious()) }

    override fun appInfo(packageName: String) {
        graph.app.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun name(packageName: String): String = services.labels.label(packageName) ?: packageName

    private companion object {
        /** Enough events to cover a check started a few days ago on a busy phone. */
        const val RECENT_EVENTS = 500
    }
}
