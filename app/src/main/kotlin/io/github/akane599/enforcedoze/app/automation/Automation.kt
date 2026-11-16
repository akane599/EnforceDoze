// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.automation

import io.github.akane599.enforcedoze.app.evidence.LastResults
import android.util.Log
import android.os.SystemClock
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
    fun syncReceiver(on: Boolean = graph.prefs.automation) = AutomationReceiver.enable(graph.app, on)

    /** One call; nothing it sends can crash the app: a failure is logged with the client, or to logcat if even that fails. */
    suspend fun handle(intent: Intent, sender: String?) {
        val action = AutomationAction.of(intent.action) ?: return
        if (!graph.prefs.automation) return
        val now = System.currentTimeMillis()
        try {
            val client = clients.match(intent.getStringExtra(EXTRA_TOKEN))
            // Extras are checked only for a known client, so a call without a token learns nothing and costs nothing.
            val problem = if (client != null) problem(action, intent) else null
            when (val admission = gate.admit(action, client, sender, problem, SystemClock.elapsedRealtime())) {
                is Admission.Dropped -> if (admission.log) log(now, admission.client ?: UNKNOWN_CLIENT, action, describe(admission))
                is Admission.Run -> runLogged(action, admission.client, intent, now)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expected: Exception) {
            Log.w(TAG, "automation call ${action.name} failed", expected)
        }
    }

    private suspend fun runLogged(action: AutomationAction, client: String, intent: Intent, now: Long) {
        try {
            run(action, client, intent, now)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expected: Exception) {
            log(now, client, action, "failed: ${expected.message ?: expected.javaClass.simpleName}")
        }
    }

    /** A call that cannot run as sent; it is dropped before the rate limit, so it does not block the next good one. */
    private suspend fun problem(action: AutomationAction, intent: Intent): String? = when (action) {
        AutomationAction.APPLY_PROFILE -> when (val id = intent.getStringExtra(EXTRA_ID)) {
            null -> "no id"
            else -> "no profile $id".takeIf { graph.profiles.profile(id) == null }
        }

        // Sensors Off stops the camera and microphone too, so a client must say which way: no default.
        AutomationAction.SENSORS_OFF -> "on must be true or false".takeIf { on(intent) == null }

        else -> null
    }

    private fun describe(dropped: Admission.Dropped): String = when (dropped.drop) {
        Drop.BAD_TOKEN -> "rejected: unknown token"
        Drop.SENDER_MISMATCH -> "rejected: sent by ${dropped.detail}"
        Drop.INVALID -> "rejected: ${dropped.detail}"
        Drop.RATE_LIMITED -> "dropped: within 30 s of the last change"
    }

    private suspend fun run(action: AutomationAction, client: String, intent: Intent, now: Long) {
        val id = intent.getStringExtra(EXTRA_ID)
        val result = when (action) {
            AutomationAction.APPLY_PROFILE -> applyProfile(id)
            AutomationAction.APPLY_PLAN -> engine(turnOn = true) { it.followPlan(SwitchReason.TASKER) }
            AutomationAction.PAUSE -> engine(turnOn = false) { it.pause() }
            AutomationAction.RESUME -> engine(turnOn = true) { it.resume() }
            AutomationAction.RESTORE_STOCK -> describe(AppSettingsSource(services).restoreStock())
            AutomationAction.SENSORS_OFF -> sensorsOff(on(intent) ?: return)
            AutomationAction.GET_STATUS -> reply(client).let { "replied" }
        }
        log(now, client, action, result, profileId = id.takeIf { action == AutomationAction.APPLY_PROFILE })
    }

    private suspend fun applyProfile(id: String?): String {
        // Checked before the rate limit too; this covers a profile deleted in between.
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
        // The same last verdict Home and the evidence provider show: the night when there is one (ADR 0035).
        val last = LastResults.latest(graph.sessions)
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
            .putExtra("report_session_id", last?.sessionId)
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
        private const val TAG = "Automation"

        fun on(intent: Intent): Boolean? =
            onValue(intent.getStringExtra(EXTRA_ON), intent.hasExtra(EXTRA_ON)) { default -> intent.getBooleanExtra(EXTRA_ON, default) }

        /**
         * `on` as the text `true`/`false` (Tasker sends extras as text) or a boolean extra; null when it is missing or
         * anything else. [read] is the boolean getter with a default: a real boolean reads the same whatever the
         * default, any other type falls back to it.
         */
        fun onValue(text: String?, present: Boolean, read: (Boolean) -> Boolean): Boolean? {
            if (text != null) return text.trim().toBooleanStrictOrNull()
            if (!present) return null
            val asFalse = read(false)
            return asFalse.takeIf { it == read(true) }
        }
    }
}
