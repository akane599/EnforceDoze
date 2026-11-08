// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.profile.AllowlistLever
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.ForceIdleLever
import io.github.akane599.enforcedoze.profile.GuardrailProblem
import io.github.akane599.enforcedoze.profile.Guardrails
import io.github.akane599.enforcedoze.profile.Levers
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.profile.SensorLever
import kotlinx.serialization.json.Json
import org.junit.Test

class ProfileFilesTest {
    /** The example of SPEC 6.4, verbatim. */
    private val example = """
        {
          "id": "pocket",
          "name": "Pocket",
          "schema": 1,
          "levers": {
            "constants": true,
            "forceIdle": { "enabled": false, "delaySec": 0, "untilUnlock": false },
            "sensors": "restricted",
            "allowlist": "inherit"
          },
          "deviceIdle": {
            "inactive_to": 300000,
            "idle_after_inactive_to": 180000,
            "sensing_to": 30000,
            "locating_to": 15000,
            "light_after_inactive_to": 60000
          }
        }
    """.trimIndent()

    private val pocket = Profile(
        id = "pocket",
        name = "Pocket",
        levers = Levers(constants = true, forceIdle = ForceIdleLever(enabled = false), SensorLever.RESTRICTED, AllowlistLever.INHERIT),
        deviceIdle = linkedMapOf(
            "inactive_to" to "300000",
            "idle_after_inactive_to" to "180000",
            "sensing_to" to "30000",
            "locating_to" to "15000",
            "light_after_inactive_to" to "60000",
        ),
    )

    @Test fun `the profile file is the one SPEC 6_4 shows`() {
        assertThat(Json.parseToJsonElement(ProfileFiles.encode(pocket))).isEqualTo(Json.parseToJsonElement(example))
    }

    @Test fun `factors stay decimals, flags booleans, and the key order is kept`() {
        val profile = pocket.copy(
            levers = pocket.levers.copy(sensors = SensorLever.ALL_SENSORS_OFF, allowlist = AllowlistLever.TRIMMED),
            deviceIdle = linkedMapOf("idle_factor" to "2.0", "skip_sensing" to "true", "inactive_to" to "300000"),
        )
        val text = ProfileFiles.encode(profile)
        assertThat(text).contains("\"idle_factor\": 2.0")
        assertThat(text).contains("\"skip_sensing\": true")
        assertThat(text).contains("\"sensors\": \"allSensorsOff\"")
        assertThat(text).contains("\"allowlist\": \"trimmed\"")
        assertThat(text.indexOf("idle_factor")).isLessThan(text.indexOf("inactive_to"))
    }

    @Test fun `an id never becomes a path`() {
        assertThat(ProfileFiles.fileName(pocket.copy(id = "../x/y"))).isEqualTo(".._x_y.json")
    }

    private val context = ImportContext(phoneKeys = null, stock = null, takenIds = emptySet())

    private fun imported(text: String, context: ImportContext = this.context): ProfileImport.Imported {
        val result = ProfileFiles.import(text, context)
        assertWithMessage("$result").that(result).isInstanceOf(ProfileImport.Imported::class.java)
        return result as ProfileImport.Imported
    }

    private fun refused(text: String, context: ImportContext = this.context): List<ImportProblem> {
        val result = ProfileFiles.import(text, context)
        assertWithMessage("$result").that(result).isInstanceOf(ProfileImport.Refused::class.java)
        return (result as ProfileImport.Refused).problems
    }

    @Test fun `every built-in survives export and import unchanged`() {
        val stock = Guardrails.ALLOWLIST_DURATION_KEYS.associateWith { 0L }
        for (profile in BuiltInProfiles.ALL) {
            val back = imported(ProfileFiles.encode(profile), context.copy(stock = stock)).profile
            assertThat(back).isEqualTo(profile.copy(builtIn = false))
        }
    }

    @Test fun `a custom profile with a factor, a flag and a delay survives the round trip`() {
        val custom = pocket.copy(
            id = "my-commute",
            name = "My commute",
            levers = pocket.levers.copy(forceIdle = ForceIdleLever(enabled = true, delaySec = 120, untilUnlock = true)),
            deviceIdle = linkedMapOf("idle_factor" to "2.5", "skip_sensing" to "true", "inactive_to" to "300000"),
        )
        val result = imported(ProfileFiles.encode(custom))
        assertThat(result.profile).isEqualTo(custom)
        assertThat(result.profile.deviceIdle.keys).containsExactly("idle_factor", "skip_sensing", "inactive_to").inOrder()
        assertThat(ProfileFiles.read(ProfileFiles.encode(custom)).profile).isEqualTo(custom)
    }

    @Test fun `the SPEC 6_4 example imports as Pocket`() {
        assertThat(imported(example).profile).isEqualTo(pocket)
    }

    @Test fun `an id already on the phone gets a fresh one, so a built-in is never replaced`() {
        val result = imported(example, context.copy(takenIds = setOf("pocket", "pocket-2")))
        assertThat(result.profile.id).isEqualTo("pocket-3")
        assertThat(result.profile.name).isEqualTo("Pocket (3)")
        assertThat(result.renamedFrom).isEqualTo("pocket")
    }

    @Test fun `keys this phone does not have are kept and flagged, One UI keys are marked effect unknown`() {
        val text = example.replace("\"inactive_to\": 300000", "\"inactive_to\": 300000, \"future_key\": 5, \"skip_sensing\": true")
        val result =
            imported(
                text,
                context.copy(
                    phoneKeys = setOf(
                        "inactive_to",
                        "idle_after_inactive_to",
                        "sensing_to",
                        "locating_to",
                        "light_after_inactive_to",
                        "skip_sensing",
                    ),
                ),
            )
        assertThat(result.profile.deviceIdle).containsEntry("future_key", "5")
        assertThat(result.notes).containsExactly(ImportNote.NotOnThisPhone("future_key"), ImportNote.EffectUnknown("skip_sensing"))
    }

    @Test fun `before stock_json exists no key is called missing`() {
        val text = example.replace("\"inactive_to\": 300000", "\"inactive_to\": 300000, \"future_key\": 5")
        assertThat(imported(text).notes).isEmpty()
    }

    @Test fun `out-of-range constants are refused with the reason`() {
        val problems = refused(example.replace("\"sensing_to\": 30000", "\"sensing_to\": 5000"))
        assertThat(problems).containsExactly(ImportProblem.Guardrail(GuardrailProblem.BelowMinimum("sensing_to", 10_000.0)))
    }

    @Test fun `an allowlist duration is refused until stock_json gives its floor, and below the floor after`() {
        val text = example.replace("\"inactive_to\": 300000", "\"inactive_to\": 300000, \"sms_temp_app_allowlist_duration_ms\": 1000")
        assertThat(
            refused(text),
        ).containsExactly(ImportProblem.Guardrail(GuardrailProblem.StockUnknown("sms_temp_app_allowlist_duration_ms")))
        val stock = mapOf("sms_temp_app_allowlist_duration_ms" to 20_000L)
        assertThat(refused(text, context.copy(stock = stock)))
            .containsExactly(ImportProblem.Guardrail(GuardrailProblem.BelowStock("sms_temp_app_allowlist_duration_ms", 20_000L)))
    }

    @Test fun `a value the string cannot hold is refused, not written`() {
        val problems = refused(example.replace("\"locating_to\": 15000", "\"locating_to\": \"15 s\""))
        assertThat(problems).hasSize(1)
        assertThat((problems.single() as ImportProblem.Guardrail).problem.key).isEqualTo("locating_to")
    }

    @Test fun `a file from a newer version is refused as such`() {
        assertThat(refused(example.replace("\"schema\": 1", "\"schema\": 2"))).containsExactly(ImportProblem.NewerSchema(2))
    }

    @Test fun `every broken field is named at once`() {
        val text = example
            .replace("\"sensors\": \"restricted\"", "\"sensors\": \"sometimes\"")
            .replace("\"delaySec\": 0", "\"delaySec\": -5")
            .replace("\"constants\": true", "\"constants\": \"yes\"")
        val fields = refused(text).map { (it as ImportProblem.NotAProfile).field }
        assertThat(fields).containsExactly("levers.constants", "levers.forceIdle.delaySec", "levers.sensors")
    }

    @Test fun `text that is not a profile is refused, never thrown`() {
        for (text in listOf(
            "",
            "not json",
            "[1, 2]",
            "{}",
            """{"schema": 1, "id": "../etc", "name": "x"}""",
            """{"schema": 1, "id": "a", "name": "  "}""",
        )) {
            assertWithMessage(text).that(ProfileFiles.import(text, context)).isInstanceOf(ProfileImport.Refused::class.java)
        }
    }

    @Test fun `optional lever fields default and a profile without constants imports`() {
        val text = """
            {"id": "plain", "name": "Plain", "schema": 1,
             "levers": {"constants": false, "forceIdle": {"enabled": true}, "sensors": "off", "allowlist": "inherit"}}
        """.trimIndent()
        val profile = imported(text).profile
        assertThat(profile.levers.forceIdle).isEqualTo(ForceIdleLever(enabled = true))
        assertThat(profile.deviceIdle).isEmpty()
    }

    @Test fun `a clone gets its own id and is no longer built in`() {
        val clone = BuiltInProfiles.NIGHT.cloneAs("Late shift", BuiltInProfiles.ALL.map { it.id }.toSet())
        assertThat(clone.id).isEqualTo("late-shift")
        assertThat(clone.builtIn).isFalse()
        assertThat(clone.deviceIdle).isEqualTo(BuiltInProfiles.NIGHT.deviceIdle)
        assertThat(BuiltInProfiles.NIGHT.cloneAs("Night", setOf("night")).id).isEqualTo("night-2")
    }
}
