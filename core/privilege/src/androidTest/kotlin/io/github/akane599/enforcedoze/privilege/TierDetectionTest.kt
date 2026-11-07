// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** P1 gate: tier detection with no privilege, with grants, with Shizuku as shell and as root (SPEC 14). */
@RunWith(AndroidJUnit4::class)
class TierDetectionTest {
    private val fixture = ControllerFixture()

    @After
    fun tearDown() = fixture.close()

    @Test
    fun tierMatchesTheScenario() {
        val scenario = OnDeviceScenario.current
        val state = fixture.awaitScenarioState()
        assertThat(state.tier).isEqualTo(scenario.expectedTier)
        when (scenario) {
            OnDeviceScenario.NONE -> {
                assertThat(state.grants).doesNotContain(Grant.DUMP)
                assertThat(state.shizuku).isEqualTo(ShizukuStatus.NotRunning)
                assertThat(state.workerSource).isNull()
            }

            OnDeviceScenario.GRANTS -> {
                assertThat(state.grants).containsAtLeast(Grant.DUMP, Grant.WRITE_SECURE_SETTINGS, Grant.READ_LOGS, Grant.USAGE_STATS)
                assertThat(state.workerSource).isNull()
            }

            OnDeviceScenario.SHELL, OnDeviceScenario.ROOT -> {
                assertThat(state.workerSource).isEqualTo(WorkerSource.SHIZUKU)
                assertThat((state.shizuku as ShizukuStatus.Ready).sui).isFalse()
            }
        }
        // The emulator app process never holds root through libsu: it has no su for apps.
        assertThat(state.rootGranted).isFalse()
    }

    @Test
    fun theWorkerBindsOnlyWhenTheTierHasOne() {
        val scenario = OnDeviceScenario.current
        fixture.awaitScenarioState()
        val result = kotlinx.coroutines.runBlocking { fixture.controller.withWorker { it.uid() } }
        if (scenario.hasWorker) {
            assertThat(result).isEqualTo(WorkerResult.Ok(scenario.serverUid))
            assertThat(fixture.controller.state.value.worker).isEqualTo(WorkerStatus.Bound(WorkerSource.SHIZUKU, scenario.serverUid!!))
        } else {
            assertThat(result).isInstanceOf(WorkerResult.Unavailable::class.java)
        }
    }
}
