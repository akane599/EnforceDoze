// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.automation

import kotlinx.coroutines.CancellationException
import io.github.akane599.enforcedoze.doze.DozeEngine
import io.github.akane599.enforcedoze.profile.Plan
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import io.github.akane599.enforcedoze.app.Tiles
import io.github.akane599.enforcedoze.app.ui.AppAllowlistSource
import io.github.akane599.enforcedoze.app.ui.AppServices
import io.github.akane599.enforcedoze.app.ui.AppSettingsSource
import io.github.akane599.enforcedoze.data.EventRow
import io.github.akane599.enforcedoze.data.JournalRows
import io.github.akane599.enforcedoze.doze.SwitchReason
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * The automation API (SPEC 11.1, ADR 0033). Each call is admitted by its token and the rate limit, runs through
 * the same engine commands and worker calls as the owner's own taps, and becomes one journal event with the
 * client's package. `GET_STATUS` replies to the client's package only.
 */
internal class Automation(private val services: AppServices) {
    private val graph = services.graph
    val clients: AutomationClients = AutomationClients(File(graph.app.noBackupFilesDir, "automation/clients.json"))
    private val gate = AutomationGate()

    /** A token just made, shown once in Settings and then forgotten. */
    data class NewToken(val packageName: String, val token: String)

    private val fresh = MutableStateFlow<NewToken?>(null)
    val newToken: StateFlow<NewToken?> = fresh.asStateFlow()

    fun issue(packageName: String) {
        fresh.value = NewToken(packageName, clients.issue(packageName))
    }

    fun tokenShown() {
        fresh.value = null
    }

    /** The receiver is enabled only while the switch is on, so nothing can start the app through it otherwise. */
    fun syncReceiver(on: Boolean = graph.prefs.automation) {
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        graph.app.packageManager.setComponentEnabledSetting(
            ComponentName(graph.app, AutomationReceiver::class.java),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    suspend fun handle(intent: Intent, sender: String?) {
        val action = AutomationAction.of(intent.action) ?: return
        val now = System.currentTimeMillis()
        if (!graph.prefs.automation) return
        when (val admission = gate.admit(action, clients.match(intent.getStringExtra(EXTRA_TOKEN)), sender, now)) {
            is Admission.BadToken -> if (admission.log) log(now, UNKNOWN_CLIENT, action, "rejected: unknown token")
            is Admission.Mismatch -> log(now, admission.client, action, "rejected: sent by ${admission.sender}")
            is Admission.RateLimited -> log(now, admission.client, action, "dropped: within 30 s of the last change")
            is Admission.Run -> try {
                run(action, admission.client, intent, now)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                // A call from outside never crashes the app; the failure is logged with the client like any result.
                log(now, admission.client, action, "failed: ${expected.message ?: expected.javaClass.simpleName}")
            }
        }
    }

    private suspend fun run(action: AutomationAction, client: String, intent: Intent, now: Long) {
        val id = intent.getStringExtra(EXTRA_ID)
        val result = when (action) {
            AutomationAction.APPLY_PROFILE -> applyProfile(id)
            AutomationAction.APPLY_PLAN -> engine(turnOn = true) { it.followPlan(SwitchReason.TASKER) }
            AutomationAction.PAUSE -> engine(turnOn = false) { it.pause() }
            AutomationAction.RESUME -> engine(turnOn = true) { it.resume() }
            AutomationAction.RESTORE_STOCK -> describe(AppSettingsSource(services).restoreStock())
            AutomationAction.SENSORS_OFF -> sensorsOff(intent.getBooleanExtra(EXTRA_ON, true))
            AutomationAction.GET_STATUS -> reply(client).let { "replied" }
        }
        log(now, client, action, result, profileId = id.takeIf { action == AutomationAction.APPLY_PROFILE })
    }

    private suspend fun applyProfile(id: String?): String {
        val profile = id?.let { graph.profiles.profile(it) } ?: return "rejected: no profile ${id ?: "(no id)"}"
        return engine(turnOn = true) { it.setProfile(profile, SwitchReason.TASKER) }
    }

    /** The same engine command as the owner's tap; [turnOn] starts the engine service first, as the UI does. */
    private suspend fun engine(turnOn: Boolean, command: suspend (DozeEngine) -> Unit): String =
        describe(services.access.send(turnOn = turnOn) { host -> command(host.engine).let { ActionOutcome.Done } })

    private suspend fun sensorsOff(on: Boolean): String {
        val result = graph.controller.withWorker { it.setAllSensorsOff(on) }
        Tiles.refresh(graph.app)
        return describe(AppAllowlistSource.outcome(result))
    }

    /** SPEC 11.1 `GET_STATUS`: plan, profile, mode, tier, the last session's verdict, deep share, drain, first warning. */
    private suspend fun reply(client: String) {
        val status = graph.hosts.value?.status?.value
        val saved = graph.store.load()
        val last = graph.sessions.latestSessions(1).firstOrNull()
        val warning = services.notices.notices.first().firstOrNull()?.kind?.name
        val plan = saved.plan?.plan
        val reply = Intent(STATUS_ACTION)
            .setPackage(client)
            .putExtra("plan", plan?.let { describe(it) })
            .putExtra("profile", status?.profileId ?: saved.plan?.profileId)
            .putExtra("mode", (status?.mode ?: saved.mode).name)
            .putExtra("tier", graph.controller.state.value.tier.name)
            .putExtra("verdict", last?.verdict)
            .putExtra("deep_share", last?.deepShare)
            .putExtra("drain", last?.drainPerHour)
            .putExtra("warning", warning)
        graph.app.sendBroadcast(reply)
    }

    /** The plan as one line: the day profile, then the night profile with its hours. */
    private fun describe(plan: Plan): String =
        listOfNotNull(plan.dayProfileId, plan.night?.let { "${it.profileId} ${it.start}-${it.end}" }).joinToString(" | ")

    private fun describe(outcome: ActionOutcome): String = when (outcome) {
        ActionOutcome.Done, is ActionOutcome.Applied -> "done"
        is ActionOutcome.Unavailable -> "unavailable: ${outcome.reason}"
        is ActionOutcome.Failed -> "failed: ${outcome.raw}"
    }

    private suspend fun log(time: Long, client: String, action: AutomationAction, result: String, profileId: String? = null) {
        graph.journal.insert(
            EventRow(
                time = time,
                kind = JournalRows.AUTOMATION,
                writer = client,
                reason = "${action.name}: $result",
                profileId = profileId,
            ),
        )
    }

    companion object {
        const val EXTRA_TOKEN = "token"
        const val EXTRA_ID = "id"
        const val EXTRA_ON = "on"
        const val STATUS_ACTION = "io.github.akane599.enforcedoze.action.STATUS"
        private const val UNKNOWN_CLIENT = "unknown"
    }
}
