// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.profile.SensorLever
import org.junit.Test
import java.time.Instant

class SwitchOverTest {
    private val t0 = Instant.parse("2026-11-16T07:00:00Z")

    // The owner's Tasker setup as the fixtures show it: about ten minutes to deep idle, written as the global string.
    private val previous = PreviousSnapshot(
        capturedAt = t0,
        constants = ConstantsValue.of("inactive_to=300000,idle_after_inactive_to=300000,sensing_to=60000"),
        writer = "com.android.shell",
        effective = mapOf(
            "inactive_to" to "+5m0s0ms",
            "idle_after_inactive_to" to "+5m0s0ms",
            "sensing_to" to "+1m0s0ms",
            "locating_to" to "+15s0ms",
            "location_accuracy" to "50.0m",
        ),
        sensors = SensorValue.Restricted("com.android.shell"),
        forceIdle = false,
        allowlist = null,
    )

    private val stock = StockSnapshot(
        t0,
        mapOf(
            "inactive_to" to "+30m0s0ms",
            "idle_after_inactive_to" to "+30m0s0ms",
            "sensing_to" to "+4m0s0ms",
            "locating_to" to "+30s0ms",
            "location_accuracy" to "20.0m",
        ),
    )

    @Test
    fun `the found setup lists every effective value unlike stock`() {
        val found = SwitchOver.found(previous, stock)
        assertThat(found.changed).isTrue()
        assertThat(found.writer).isEqualTo("com.android.shell")
        assertThat(found.constants.keys).containsExactly("inactive_to", "idle_after_inactive_to", "sensing_to").inOrder()
        assertThat(found.differences.map { it.key })
            .containsExactly("idle_after_inactive_to", "inactive_to", "locating_to", "location_accuracy", "sensing_to").inOrder()
        assertThat(found.differences.first { it.key == "locating_to" }).isEqualTo(KeyDifference("locating_to", "+15s0ms", "+30s0ms"))
        assertThat(found.sensorsRestricted).isTrue()
    }

    @Test
    fun `a stock phone has nothing to import`() {
        val clean = previous.copy(constants = ConstantsValue.Absent, effective = stock.effective, sensors = SensorValue.Normal)
        assertThat(SwitchOver.found(clean, stock).changed).isFalse()
    }

    @Test
    fun `without stock json the string itself is the difference`() {
        val found = SwitchOver.found(previous, stock = null)
        assertThat(found.differences.map { it.key }).containsExactly("inactive_to", "idle_after_inactive_to", "sensing_to")
        assertThat(found.differences.all { it.stock == null }).isTrue()
    }

    @Test
    fun `the import keeps the string, adds writable values and leaves out the rest`() {
        val profile = SwitchOver.imported(SwitchOver.found(previous, stock), "Imported setup", taken = setOf("everyday", "imported-setup"))
        assertThat(profile.id).isEqualTo("imported-setup-2")
        assertThat(profile.builtIn).isFalse()
        assertThat(profile.levers.constants).isTrue()
        assertThat(profile.levers.sensors).isEqualTo(SensorLever.RESTRICTED)
        assertThat(profile.levers.forceIdle.enabled).isFalse()
        // locating_to read back as a duration becomes milliseconds; location_accuracy (50.0m) is not a writable value.
        assertThat(profile.deviceIdle).containsExactly(
            "inactive_to",
            "300000",
            "idle_after_inactive_to",
            "300000",
            "sensing_to",
            "60000",
            "locating_to",
            "15000",
        ).inOrder()
    }

    @Test
    fun `the check waits, fails on a foreign write and passes after a clean screen off and on`() {
        val since = t0.plusSeconds(60)
        assertThat(SwitchOver.check(null, emptyList(), emptyList())).isEqualTo(SwitchCheck.NotStarted)
        // A session that began before the start does not count.
        val before = t0 to t0.plusSeconds(600)
        assertThat(SwitchOver.check(since, emptyList(), listOf(before))).isEqualTo(SwitchCheck.Waiting(since))
        val clean = since.plusSeconds(60) to since.plusSeconds(1_800)
        assertThat(SwitchOver.check(since, emptyList(), listOf(before, clean))).isEqualTo(SwitchCheck.Complete(since, clean.second))
        // A write before the start is history; one after it fails the check, whatever the sessions show.
        val writes = listOf(ForeignWriteSeen(t0, "com.android.shell"), ForeignWriteSeen(since.plusSeconds(120), "com.android.shell"))
        assertThat(SwitchOver.check(since, writes, listOf(clean)))
            .isEqualTo(SwitchCheck.ForeignWrite(since, since.plusSeconds(120), "com.android.shell"))
    }
}
