// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.probe

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.privilege.ConstantsWriter
import io.github.akane599.enforcedoze.privilege.FakeDozeWorker
import io.github.akane599.enforcedoze.privilege.Tier
import io.github.akane599.enforcedoze.privilege.WorkerSource
import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import io.github.akane599.enforcedoze.privilege.api.CommandRunner
import io.github.akane599.enforcedoze.privilege.api.DeviceIdleSettings
import io.github.akane599.enforcedoze.privilege.api.StreamResult
import io.github.akane599.enforcedoze.testing.fixtures.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.OutputStream

class CapabilityProberTest {
    private val emulator = Fixtures.capture("sdk_gphone64_x86_64", "api37_CE2A.260420.051.A3")
    private val phone = Fixtures.capture("sm-s938b", "api37_CP2A.260605.016")

    /** Answers fixed argv lists like the app's own process would; anything else is "permission denied". */
    private class FakeRunner(private val answers: Map<List<String>, String>) : CommandRunner {
        val asked = mutableListOf<List<String>>()

        override fun run(argv: List<String>, timeoutMillis: Long): CommandOutcome {
            asked += argv
            val out = answers[argv]
            return if (out != null) {
                CommandOutcome(argv, 0, out, "", false, 1, null)
            } else {
                CommandOutcome(argv, 0, "Permission Denial: can't dump from pid=1, uid=10200", "", false, 1, null)
            }
        }

        override fun stream(argv: List<String>, timeoutMillis: Long, sink: OutputStream): StreamResult = error("not used")
    }

    /**
     * Stands in for `Settings.Global` and `DeviceIdleController`: a written string shows up in the worker's
     * read and, when [applies], in the next `dumpsys deviceidle` as the new `location_accuracy`.
     */
    private class FakeConstants(
        private val worker: FakeDozeWorker?,
        private val granted: Boolean = true,
        private val applies: Boolean = true,
    ) : ConstantsWriter {
        val written = mutableListOf<String?>()
        private val stockDump = worker?.deviceIdle.orEmpty()

        override fun canWrite(): Boolean = granted

        override fun write(value: String?): Boolean {
            written += value
            val target = worker ?: return true
            target.globalConstants = value
            val accuracy = value?.let { DeviceIdleSettings.parse(it).values["location_accuracy"] }
            target.deviceIdle = if (applies && accuracy != null) {
                stockDump.replace(Regex("location_accuracy=\\S+"), "location_accuracy=$accuracy")
            } else {
                stockDump
            }
            return true
        }
    }

    private fun prober(answers: Map<List<String>, String> = emptyMap(), constants: ConstantsWriter = FakeConstants(null, granted = false)) =
        CapabilityProber(LocalReader(FakeRunner(answers)), "com.android.shell", constants)

    private fun List<ProbeResult>.status(id: String) = single { it.id == id }.status

    private fun List<ProbeResult>.detail(id: String) = single { it.id == id }.detail

    @Test
    fun `no grants and no worker reports what is missing and never throws`() = runTest {
        val results = prober().run(worker = null, includeChanges = true)
        assertThat(results.map { it.id }).containsExactly(
            CapabilityProber.READ, CapabilityProber.KEYS, CapabilityProber.VENDOR, CapabilityProber.WRITE, CapabilityProber.FORCE,
            CapabilityProber.RESTRICT, CapabilityProber.PRIVACY, CapabilityProber.EVENTS, CapabilityProber.HISTORY,
            CapabilityProber.ONE_UI, CapabilityProber.PERSISTENCE,
        ).inOrder()
        assertThat(results.status(CapabilityProber.READ)).isEqualTo(ProbeStatus.MISSING)
        assertThat(results.detail(CapabilityProber.READ)).contains("Permission Denial")
        assertThat(results.status(CapabilityProber.KEYS)).isEqualTo(ProbeStatus.SKIPPED)
        assertThat(results.status(CapabilityProber.WRITE)).isEqualTo(ProbeStatus.MISSING)
        assertThat(results.detail(CapabilityProber.WRITE)).contains("WRITE_SECURE_SETTINGS")
        assertThat(results.status(CapabilityProber.FORCE)).isEqualTo(ProbeStatus.SKIPPED)
        assertThat(results.status(CapabilityProber.EVENTS)).isEqualTo(ProbeStatus.MISSING)
        assertThat(results.status(CapabilityProber.HISTORY)).isEqualTo(ProbeStatus.MISSING)
        assertThat(results.status(CapabilityProber.PERSISTENCE)).isEqualTo(ProbeStatus.SKIPPED)
    }

    @Test
    fun `DUMP and READ_LOGS grants read the emulator without a worker`() = runTest {
        val results = prober(
            mapOf(
                listOf("dumpsys", "deviceidle") to emulator.text("deviceidle.txt"),
                listOf("logcat", "-b", "events", "-d", "-v", "epoch") to emulator.text("events.txt"),
                listOf("dumpsys", "batterystats", "--history") to emulator.text("batterystats-history.txt"),
                listOf("getprop", "ro.build.version.oneui") to "\n",
            ),
        ).run(worker = null, includeChanges = false)
        assertThat(results.status(CapabilityProber.READ)).isEqualTo(ProbeStatus.OK)
        assertThat(results.detail(CapabilityProber.READ)).contains("DUMP grant")
        assertThat(results.status(CapabilityProber.KEYS)).isEqualTo(ProbeStatus.OK)
        assertThat(results.detail(CapabilityProber.KEYS)).startsWith("36 AOSP keys present")
        assertThat(results.status(CapabilityProber.EVENTS)).isEqualTo(ProbeStatus.OK)
        assertThat(results.detail(CapabilityProber.EVENTS)).contains("READ_LOGS")
        assertThat(results.status(CapabilityProber.HISTORY)).isEqualTo(ProbeStatus.OK)
        assertThat(results.status(CapabilityProber.ONE_UI)).isEqualTo(ProbeStatus.SKIPPED)
    }

    @Test
    fun `the phone dump shows One UI's extra keys through the worker`() = runTest {
        val worker = FakeDozeWorker().apply { deviceIdle = phone.text("deviceidle.txt") }
        val results = prober(mapOf(listOf("getprop", "ro.build.version.oneui") to "90000\n"))
            .run(worker, includeChanges = false)
        assertThat(results.detail(CapabilityProber.READ)).contains("through the worker")
        assertThat(results.detail(CapabilityProber.VENDOR)).contains("skip_sensing")
        assertThat(results.detail(CapabilityProber.ONE_UI)).isEqualTo("ro.build.version.oneui=90000")
    }

    @Test
    fun `changing probes are skipped unless asked for`() = runTest {
        val worker = FakeDozeWorker().apply { deviceIdle = emulator.text("deviceidle.txt") }
        val results = prober().run(worker, includeChanges = false)
        assertThat(results.status(CapabilityProber.FORCE)).isEqualTo(ProbeStatus.SKIPPED)
        assertThat(results.status(CapabilityProber.RESTRICT)).isEqualTo(ProbeStatus.SKIPPED)
        assertThat(results.status(CapabilityProber.PRIVACY)).isEqualTo(ProbeStatus.OK)
        assertThat(worker.calls.filter { it.startsWith("force") || it.startsWith("restrict") || it.startsWith("sensorsOff") })
            .isEmpty()
    }

    @Test
    fun `the lever A write needs the worker to read the string back first`() = runTest {
        val constants = FakeConstants(null)
        val results = prober(constants = constants).run(worker = null, includeChanges = true)
        assertThat(results.status(CapabilityProber.WRITE)).isEqualTo(ProbeStatus.SKIPPED)
        assertThat(results.detail(CapabilityProber.WRITE)).contains("WRITE_SECURE_SETTINGS held")
        assertThat(constants.written).isEmpty()
    }

    @Test
    fun `the lever A write is skipped unless asked for`() = runTest {
        val worker = FakeDozeWorker().apply { deviceIdle = emulator.text("deviceidle.txt") }
        val constants = FakeConstants(worker)
        val results = prober(constants = constants).run(worker, includeChanges = false)
        assertThat(results.status(CapabilityProber.WRITE)).isEqualTo(ProbeStatus.SKIPPED)
        assertThat(constants.written).isEmpty()
    }

    @Test
    fun `the lever A write shows in dumpsys and the absent setting is deleted again`() = runTest {
        val worker = FakeDozeWorker().apply { deviceIdle = emulator.text("deviceidle.txt") }
        val constants = FakeConstants(worker)
        val results = prober(constants = constants).run(worker, includeChanges = true)
        assertThat(results.status(CapabilityProber.WRITE)).isEqualTo(ProbeStatus.OK)
        assertThat(results.detail(CapabilityProber.WRITE)).contains("restored exactly")
        assertThat(constants.written).hasSize(2)
        assertThat(constants.written[0]).matches("location_accuracy=\\d+\\.\\d")
        assertThat(constants.written[1]).isNull()
        assertThat(worker.globalConstants).isNull()
    }

    @Test
    fun `the owner's string is put back unchanged after the lever A write`() = runTest {
        val owner = "light_after_inactive_to=30000,inactive_to=180000,skip_sensing=true"
        val worker = FakeDozeWorker().apply {
            deviceIdle = emulator.text("deviceidle.txt")
            globalConstants = owner
        }
        val constants = FakeConstants(worker)
        val results = prober(constants = constants).run(worker, includeChanges = true)
        assertThat(results.status(CapabilityProber.WRITE)).isEqualTo(ProbeStatus.OK)
        assertThat(constants.written[0]).startsWith("$owner,location_accuracy=")
        assertThat(constants.written[1]).isEqualTo(owner)
        assertThat(worker.globalConstants).isEqualTo(owner)
    }

    @Test
    fun `a malformed string is left alone`() = runTest {
        val worker = FakeDozeWorker().apply {
            deviceIdle = emulator.text("deviceidle.txt")
            globalConstants = "inactive_to=180000,oops"
        }
        val constants = FakeConstants(worker)
        val results = prober(constants = constants).run(worker, includeChanges = true)
        assertThat(results.status(CapabilityProber.WRITE)).isEqualTo(ProbeStatus.SKIPPED)
        assertThat(constants.written).isEmpty()
    }

    @Test
    fun `a write that never shows in dumpsys fails but is still undone`() = runTest {
        val worker = FakeDozeWorker().apply { deviceIdle = emulator.text("deviceidle.txt") }
        val constants = FakeConstants(worker, applies = false)
        val results = prober(constants = constants).run(worker, includeChanges = true)
        assertThat(results.status(CapabilityProber.WRITE)).isEqualTo(ProbeStatus.FAILED)
        assertThat(results.detail(CapabilityProber.WRITE)).contains("did not show")
        assertThat(worker.globalConstants).isNull()
    }

    @Test
    fun `forced idle is tried and undone`() = runTest {
        val worker = FakeDozeWorker().apply { deviceIdle = emulator.text("deviceidle.txt") }
        val results = prober().run(worker, includeChanges = true)
        assertThat(results.status(CapabilityProber.FORCE)).isEqualTo(ProbeStatus.OK)
        assertThat(worker.calls).containsAtLeast("forceIdle deep", "unforce").inOrder()
        assertThat(worker.forced).isFalse()
    }

    @Test
    fun `someone else's forced idle is left alone`() = runTest {
        val worker = FakeDozeWorker().apply {
            deviceIdle = emulator.text("deviceidle.txt")
            forced = true
            deep = "IDLE"
        }
        val results = prober().run(worker, includeChanges = true)
        assertThat(results.status(CapabilityProber.FORCE)).isEqualTo(ProbeStatus.SKIPPED)
        assertThat(worker.calls).doesNotContain("unforce")
        assertThat(worker.forced).isTrue()
    }

    @Test
    fun `sensor restriction is tried and the prior mode restored`() = runTest {
        val normal = emulator.text("sensorservice.txt")
        val restricted = emulator.text("sensorservice-restricted-allow.txt")
        val worker = FakeDozeWorker().apply {
            deviceIdle = emulator.text("deviceidle.txt")
            sensorService = { allow -> if (allow == null) normal else restricted }
        }
        val results = prober().run(worker, includeChanges = true)
        assertThat(results.status(CapabilityProber.RESTRICT)).isEqualTo(ProbeStatus.OK)
        assertThat(worker.calls).containsAtLeast("restrict com.android.shell", "restrict <enable>").inOrder()
        assertThat(worker.restriction).isNull()
    }

    @Test
    fun `sensor privacy setter writes the value it already has`() = runTest {
        val worker = FakeDozeWorker().apply {
            deviceIdle = emulator.text("deviceidle.txt")
            sensorsOff = false
        }
        prober().run(worker, includeChanges = true)
        assertThat(worker.calls).contains("sensorsOff false")
        assertThat(worker.calls).doesNotContain("sensorsOff true")
    }

    @Test
    fun `a probe that throws becomes FAILED`() = runTest {
        val worker = object : FakeDozeWorker() {
            override suspend fun allSensorsOff(): CommandOutcome = error("binder died")
        }.apply { deviceIdle = emulator.text("deviceidle.txt") }
        val results = prober().run(worker, includeChanges = false)
        assertThat(results.status(CapabilityProber.PRIVACY)).isEqualTo(ProbeStatus.FAILED)
        assertThat(results.detail(CapabilityProber.PRIVACY)).contains("binder died")
    }

    @Test
    fun `the report renders a markdown table with an escaped detail`() {
        val report = CapabilityReport(
            device = DeviceInfo("Google", "sdk_gphone64_x86_64", "17", 37, "CE2A.260420.051.A3", "google/sdk/emu:17"),
            tier = Tier.T2,
            workerSource = WorkerSource.SHIZUKU,
            workerUid = 2000,
            includedChanges = true,
            capturedAt = "2026-11-08T10:00:00Z",
            results = listOf(ProbeResult("a.b", "A b", ProbeStatus.OK, "x | y\nz")),
        )
        val md = report.toMarkdown()
        assertThat(report.fileName).isEqualTo("sdk-gphone64-x86-64_api37_CE2A.260420.051.A3.md")
        assertThat(md).contains("- Tier T2 through SHIZUKU, worker uid 2000")
        assertThat(md).contains("| A b (`a.b`) | OK | x \\| y z |")
    }
}
