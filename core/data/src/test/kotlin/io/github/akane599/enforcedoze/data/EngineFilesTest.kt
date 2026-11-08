// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.doze.ConstantsValue
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.doze.Mode
import io.github.akane599.enforcedoze.doze.PersistedState
import io.github.akane599.enforcedoze.doze.PlanSlot
import io.github.akane599.enforcedoze.doze.PlanState
import io.github.akane599.enforcedoze.doze.PreviousSnapshot
import io.github.akane599.enforcedoze.doze.SensorValue
import io.github.akane599.enforcedoze.doze.Setting
import io.github.akane599.enforcedoze.doze.StockSnapshot
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalTime

class EngineFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val time = Instant.parse("2026-11-08T22:00:00Z")

    private fun files() = EngineFiles(folder.root.resolve("engine"))

    @Test
    fun `a missing directory reads as nothing yet`() = runTest {
        val files = files()
        assertThat(files.load()).isEqualTo(PersistedState())
        assertThat(files.previous()).isNull()
        assertThat(files.stock()).isNull()
    }

    @Test
    fun `the book round-trips with every kind of setting`() = runTest {
        val state = PersistedState(
            mode = Mode.HANDS_OFF,
            profileId = "classic",
            written = mapOf(
                Lever.CONSTANTS to Setting.Constants(ConstantsValue.Exact("inactive_to=300000,sensing_to=0")),
                Lever.FORCE_IDLE to Setting.FORCED,
                Lever.SENSORS to Setting.Sensors(SensorValue.Restricted("io.github.akane599.enforcedoze")),
                Lever.SENSOR_PRIVACY to Setting.PRIVACY_OFF,
                Lever.ALLOWLIST to Setting.Allowlist(setOf("b", "a")),
            ),
            corrections = mapOf(Lever.CONSTANTS to listOf(time, time.plusSeconds(1800))),
            guarded = setOf(Lever.FORCE_IDLE),
            ownerAllowlist = setOf("a", "b", "c"),
            reapplyOnDrift = false,
            allowlistKeep = emptySet(),
            proofStartedAt = time,
            plan = PlanState(
                Plan("everyday", NightSlot("night", LocalTime.of(23, 0), LocalTime.of(7, 30))),
                PlanSlot.NIGHT,
                override = "pocket",
            ),
        )
        val files = files()
        files.save(state)
        assertThat(EngineFiles(folder.root.resolve("engine")).load()).isEqualTo(state)
        files.save(PersistedState(written = mapOf(Lever.CONSTANTS to Setting.ABSENT, Lever.SENSORS to Setting.SENSORS_NORMAL)))
        assertThat(files.load().written).containsExactly(Lever.CONSTANTS, Setting.ABSENT, Lever.SENSORS, Setting.SENSORS_NORMAL)
        assertThat(folder.root.resolve("engine").list()).asList().containsExactly("state.json")
    }

    @Test
    fun `a plan without a night slot round-trips`() = runTest {
        val files = files()
        val state = PersistedState(profileId = "pocket", plan = PlanState(Plan("pocket")))
        files.save(state)
        assertThat(files.load()).isEqualTo(state)
    }

    @Test
    fun `previous json keeps an absent string as null and an exact string as found`() = runTest {
        val files = files()
        val absent =
            PreviousSnapshot(time, ConstantsValue.Absent, null, mapOf("inactive_to" to "+30m0s0ms"), SensorValue.Normal, false, setOf("a"))
        files.savePrevious(absent)
        assertThat(files.previous()).isEqualTo(absent)
        assertThat(folder.root.resolve("engine/previous.json").readText()).contains("\"string\": null")

        val tasker = absent.copy(
            constants = ConstantsValue.Exact("inactive_to=600000,sensing_to=0"),
            writer = "com.android.shell",
            sensors = null,
        )
        files.savePrevious(tasker)
        assertThat(files.previous()).isEqualTo(tasker)
    }

    @Test
    fun `stock json round-trips the effective values exactly`() = runTest {
        val files = files()
        val stock = StockSnapshot(time, mapOf("inactive_to" to "+30m0s0ms", "idle_factor" to "2.0"))
        files.saveStock(stock)
        assertThat(files.stock()).isEqualTo(stock)
    }

    @Test
    fun `an unreadable file is set aside and read as missing`() = runTest {
        val dir = folder.root.resolve("engine").apply { mkdirs() }
        dir.resolve("state.json").writeText("{ not json")
        dir.resolve("stock.json").writeText("""{"capturedAt": "yesterday", "effective": {}}""")
        dir.resolve("previous.json").writeText("""{"capturedAt": "2026-11-08T22:00:00Z", "string": null, "sensors": "NORMAL"}""")
        val files = EngineFiles(dir)

        assertThat(files.load()).isEqualTo(PersistedState())
        assertThat(files.stock()).isNull()
        assertThat(files.previous()?.sensors).isEqualTo(SensorValue.Normal)
        assertThat(dir.list()!!.filter { it.contains(".unreadable-") }.map { it.substringBefore(".unreadable") })
            .containsExactly("state.json", "stock.json")
    }

    @Test
    fun `a lever this version does not know sets the file aside`() = runTest {
        val dir = folder.root.resolve("engine").apply { mkdirs() }
        dir.resolve("state.json").writeText("""{"written": [{"lever": "FUTURE_LEVER", "on": true}]}""")
        assertThat(EngineFiles(dir).load()).isEqualTo(PersistedState())
    }
}
