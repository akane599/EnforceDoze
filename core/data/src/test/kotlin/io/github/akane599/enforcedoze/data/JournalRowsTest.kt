// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.doze.ActionRecord
import io.github.akane599.enforcedoze.doze.Cause
import io.github.akane599.enforcedoze.doze.EngineEvent
import io.github.akane599.enforcedoze.doze.HoldOff
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.doze.Mode
import io.github.akane599.enforcedoze.doze.SwitchReason
import org.junit.Test
import java.time.Instant

class JournalRowsTest {
    private val time = Instant.parse("2026-11-08T22:00:00Z")

    @Test
    fun `an action row keeps everything the log shows`() {
        val row = JournalRows.of(
            ActionRecord(
                time,
                Lever.FORCE_IDLE,
                "cmd deviceidle force-idle deep",
                "forced",
                "released",
                ActionRecord.Outcome.TIMEOUT,
                Cause.SCREEN_OFF,
                "no answer",
            ),
        )
        assertThat(row).isEqualTo(
            ActionRow(
                time = time.toEpochMilli(),
                lever = "FORCE_IDLE",
                command = "cmd deviceidle force-idle deep",
                expected = "forced",
                readBack = "released",
                outcome = "TIMEOUT",
                cause = "SCREEN_OFF",
                detail = "no answer",
            ),
        )
    }

    @Test
    fun `every event kind maps to its columns`() {
        val drift = JournalRows.of(EngineEvent.Drift(time, Lever.CONSTANTS, "a=1", "a=2", "com.android.shell", whileStopped = true))
        assertThat(drift.kind).isEqualTo("DRIFT")
        assertThat(drift.writer).isEqualTo("com.android.shell")
        assertThat(drift.whileStopped).isTrue()
        assertThat(JournalRows.of(EngineEvent.HoldOffStarted(time, HoldOff.CALL)).holdOff).isEqualTo("CALL")
        assertThat(JournalRows.of(EngineEvent.HoldOffEnded(time, HoldOff.CALL)).kind).isEqualTo("HOLD_OFF_ENDED")
        assertThat(JournalRows.of(EngineEvent.LoopGuardTripped(time, Lever.CONSTANTS)).lever).isEqualTo("CONSTANTS")
        assertThat(JournalRows.of(EngineEvent.ModeChanged(time, Mode.PAUSED)).mode).isEqualTo("PAUSED")
        val switched = JournalRows.of(EngineEvent.ProfileSwitched(time, "night", SwitchReason.SCHEDULE))
        assertThat(switched.profileId to switched.reason).isEqualTo("night" to "SCHEDULE")
        val reset = JournalRows.of(EngineEvent.Battery(time, unplugged = false, ok = false, line = "no worker: Shizuku stopped"))
        assertThat(listOf(reset.kind, reset.expected, reset.found, reset.reason))
            .containsExactly("BATTERY", "reset", "no worker: Shizuku stopped", "FAILED").inOrder()
    }
}
