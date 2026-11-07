// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.api

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceIdleSettingsTest {
    /** The owner's Tasker string on One UI 9 (fixtures/sm-s938b/api37_CP2A.260605.016). */
    private val phone =
        "light_after_inactive_to=30000,inactive_to=180000,idle_after_inactive_to=120000," +
            "motion_inactive_to=180000,skip_sensing=true,min_time_to_alarm=600000"

    @Test
    fun encodeKeepsTheMapOrder() {
        val text = DeviceIdleSettings.encode(linkedMapOf("inactive_to" to "300000", "idle_factor" to "2.0", "skip_sensing" to "true"))
        assertThat(text).isEqualTo("inactive_to=300000,idle_factor=2.0,skip_sensing=true")
    }

    @Test
    fun encodeRefusesWhatCannotBeWritten() {
        assertThrows(IllegalArgumentException::class.java) { DeviceIdleSettings.encode(mapOf("inactive_to" to "5m")) }
        assertThrows(IllegalArgumentException::class.java) { DeviceIdleSettings.encode(emptyMap()) }
    }

    @Test
    fun mapProblemsNameTheKey() {
        assertThat(DeviceIdleSettings.problems(mapOf("inactive_to" to "1", "x;reboot" to "1"))).containsExactly("invalid key: x;reboot")
        assertThat(DeviceIdleSettings.problems(mapOf("inactive_to" to "1,idle_to=2"))).hasSize(1)
        assertThat(DeviceIdleSettings.problems(mapOf("inactive_to" to 300000))).hasSize(1)
        assertThat(DeviceIdleSettings.problems(emptyMap<String, String>())).containsExactly("no constants")
        val tooMany = (0..DeviceIdleSettings.MAX_KEYS).associate { "k$it" to "1" }
        assertThat(DeviceIdleSettings.problems(tooMany).single()).contains("too many")
    }

    @Test
    fun stringProblemsAcceptThePhoneString() {
        assertThat(DeviceIdleSettings.problems(phone)).isEmpty()
    }

    @Test
    fun stringProblemsRejectWhatTheParserWouldMisread() {
        assertThat(DeviceIdleSettings.problems("")).containsExactly("empty string")
        assertThat(DeviceIdleSettings.problems("inactive_to=1,")).containsExactly("not a key=value pair: ")
        assertThat(DeviceIdleSettings.problems("inactive_to=1,inactive_to=2")).containsExactly("duplicate key: inactive_to")
        assertThat(DeviceIdleSettings.problems("inactive_to = 1")).isNotEmpty()
        assertThat(DeviceIdleSettings.problems("inactive_to=1;reboot")).isNotEmpty()
        assertThat(DeviceIdleSettings.problems("inactive_to")).containsExactly("not a key=value pair: inactive_to")
    }

    @Test
    fun parseReadsThePhoneString() {
        val parsed = DeviceIdleSettings.parse(phone)
        assertThat(parsed.valid).isTrue()
        assertThat(parsed.values).containsEntry("skip_sensing", "true")
        assertThat(parsed.values).containsEntry("inactive_to", "180000")
        assertThat(parsed.values).hasSize(6)
    }

    @Test
    fun parseTreatsUnsetAsEmpty() {
        for (unset in listOf(null, "", "null")) {
            assertThat(DeviceIdleSettings.parse(unset)).isEqualTo(DeviceIdleSettings.Parsed(emptyMap(), valid = true))
        }
    }

    @Test
    fun parseFollowsKeyValueListParser() {
        // Trimmed, last duplicate wins, one trailing comma ignored.
        assertThat(DeviceIdleSettings.parse(" inactive_to = 1 ,inactive_to=2,").values).containsExactly("inactive_to", "2")
        // A pair without '=' (or an empty pair) clears every key.
        assertThat(DeviceIdleSettings.parse("inactive_to=1,oops").valid).isFalse()
        assertThat(DeviceIdleSettings.parse("inactive_to=1,,idle_to=2").valid).isFalse()
        assertThat(DeviceIdleSettings.parse(",inactive_to=1").valid).isFalse()
    }

    @Test
    fun encodeThenParseRoundTrips() {
        val values = linkedMapOf("inactive_to" to "300000", "sensing_to" to "60000", "idle_factor" to "2.0")
        assertThat(DeviceIdleSettings.parse(DeviceIdleSettings.encode(values)).values).containsExactlyEntriesIn(values).inOrder()
    }
}
