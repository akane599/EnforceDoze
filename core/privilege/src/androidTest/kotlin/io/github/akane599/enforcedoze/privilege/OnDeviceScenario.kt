// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue

/**
 * What the emulator was set up with before the run (`tools/emulator/p1-privilege.sh`), passed as the
 * instrumentation argument `scenario`.
 */
enum class OnDeviceScenario(val expectedTier: Tier, val serverUid: Int?) {
    /** Nothing granted, no Shizuku. */
    NONE(Tier.T0, null),

    /** DUMP, WRITE_SECURE_SETTINGS and READ_LOGS granted over ADB, usage access allowed. */
    GRANTS(Tier.T1, null),

    /** Shizuku started from `adb shell`; this app authorised with `pm grant … API_V23` (ADR 0011). */
    SHELL(Tier.T2, 2000),

    /** Shizuku started after `adb root`. */
    ROOT(Tier.T3, 0),
    ;

    val hasWorker: Boolean get() = serverUid != null

    companion object {
        val current: OnDeviceScenario by lazy {
            val name = InstrumentationRegistry.getArguments().getString("scenario") ?: "none"
            OnDeviceScenario.valueOf(name.uppercase())
        }

        fun requireWorker() = assumeTrue("needs a worker; scenario is $current", current.hasWorker)
    }
}

/** A started controller for one test; the server's binder can arrive a little after the process starts. */
class ControllerFixture(val context: Context = InstrumentationRegistry.getInstrumentation().targetContext) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val controller = PrivilegeController.create(context, scope).also { it.start() }

    /** Waits for the state the scenario promises, or returns what there is after [timeoutMillis]. */
    fun awaitScenarioState(timeoutMillis: Long = 60_000): PrivilegeState = runBlocking {
        val scenario = OnDeviceScenario.current
        if (!scenario.hasWorker) return@runBlocking controller.refresh()
        withTimeoutOrNull(timeoutMillis) {
            controller.state.first { (it.shizuku as? ShizukuStatus.Ready)?.uid == scenario.serverUid }
        } ?: controller.refresh()
    }

    fun close() = runBlocking {
        controller.stop()
        scope.cancel()
    }
}
