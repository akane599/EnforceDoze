// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.privilege.probe.CapabilityProber
import io.github.akane599.enforcedoze.privilege.probe.ProbeStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the capability probes in each scenario and leaves the report in the app's external files
 * (`capabilities/`), where the scenario script pulls it into `docs/capabilities/` (SPEC 4.4).
 */
@RunWith(AndroidJUnit4::class)
class CapabilityExportTest {
    private val fixture = ControllerFixture()

    @After
    fun tearDown() = fixture.close()

    @Test
    fun probeAndExport() {
        val scenario = OnDeviceScenario.current
        fixture.awaitScenarioState()
        val report = runBlocking { fixture.controller.probe(includeChanges = scenario.hasWorker) }
        val dir = File(fixture.context.getExternalFilesDir(null), "capabilities").apply { mkdirs() }
        File(dir, report.fileName.removeSuffix(".md") + "_${scenario.name.lowercase()}.md").writeText(report.toMarkdown())

        assertThat(report.tier).isEqualTo(scenario.expectedTier)
        val read = report.result(CapabilityProber.READ)!!.status
        assertThat(read).isEqualTo(if (scenario == OnDeviceScenario.NONE) ProbeStatus.MISSING else ProbeStatus.OK)
        assertThat(report.results.none { it.status == ProbeStatus.FAILED }).isTrue()
        if (scenario.hasWorker) {
            assertThat(report.result(CapabilityProber.FORCE)!!.status).isEqualTo(ProbeStatus.OK)
            assertThat(report.result(CapabilityProber.RESTRICT)!!.status).isEqualTo(ProbeStatus.OK)
            assertThat(report.result(CapabilityProber.PRIVACY)!!.status).isEqualTo(ProbeStatus.OK)
        }
    }
}
