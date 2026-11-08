// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.doze.ProofStepKind.CONSTANTS
import io.github.akane599.enforcedoze.doze.ProofStepKind.DEEP_IDLE
import io.github.akane599.enforcedoze.doze.ProofStepKind.FORCE_IDLE
import io.github.akane599.enforcedoze.doze.ProofStepKind.SENSORS
import io.github.akane599.enforcedoze.doze.ProofStepKind.UNDO_BATTERY
import io.github.akane599.enforcedoze.doze.ProofStepKind.UNDO_FORCE_IDLE
import io.github.akane599.enforcedoze.doze.ProofStepKind.UNDO_SENSORS
import io.github.akane599.enforcedoze.doze.ProofStepKind.UNPLUG
import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** SPEC 8.6: the quick proof runs the levers for real with the screen on and always undoes them. */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickProofTest {
    private val device = FakeDevice()
    private val store = FakeStore()
    private val journal = FakeJournal()
    private val restricted = SensorValue.Restricted(FakeDevice.SELF)

    private fun TestScope.engine(): DozeEngine {
        val clock = EngineClock { BASE_TIME.plusMillis(testScheduler.currentTime) }
        return DozeEngine(device.port, store, journal, clock, FakeDevice.SELF, backgroundScope, StandardTestDispatcher(testScheduler))
    }

    private suspend fun DozeEngine.startWith(profile: Profile, writable: Writable = Writable.EVERYTHING, screenOn: Boolean = true) =
        start(EngineStart(profile, screenOn = screenOn, writable = writable))

    private fun snapshotsReady() {
        store.previous = PreviousSnapshot(BASE_TIME, ConstantsValue.Absent, null, emptyMap(), null, null, null)
        store.stock = StockSnapshot(BASE_TIME, FakeDevice().effective())
    }

    private fun assertUndone() {
        assertThat(device.forced).isFalse()
        assertThat(device.sensors).isEqualTo(SensorValue.Normal)
        assertThat(device.batteryUnplugged).isFalse()
        assertThat(store.state.proofStartedAt).isNull()
    }

    @Test
    fun `every lever is applied, confirmed and undone, the battery included`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)
        val mark = device.writes.size

        val proof = engine.quickProof()

        assertThat(proof.refused).isNull()
        assertThat(proof.steps.map { it.kind })
            .containsExactly(CONSTANTS, UNPLUG, FORCE_IDLE, DEEP_IDLE, SENSORS, UNDO_FORCE_IDLE, UNDO_SENSORS, UNDO_BATTERY).inOrder()
        assertWithMessage(proof.steps.joinToString("\n")).that(proof.ok).isTrue()
        assertThat(
            proof.steps.first().line,
        ).isEqualTo("device_idle_constants=${DeviceIdleSettings.encode(BuiltInProfiles.EVERYDAY.deviceIdle)}")
        assertThat(proof.steps.single { it.kind == DEEP_IDLE }.line).isEqualTo("cmd deviceidle get deep: IDLE")
        assertThat(device.writes.drop(mark)).containsExactly(
            DeviceWrite.Battery(true),
            DeviceWrite.ForceIdle,
            DeviceWrite.RestrictSensors(FakeDevice.SELF),
            DeviceWrite.Unforce,
            DeviceWrite.EnableSensors,
            DeviceWrite.Battery(false),
        ).inOrder()
        assertUndone()
        assertThat(journal.actions.filter { it.cause == Cause.QUICK_PROOF }.map { it.lever })
            .containsExactly(Lever.FORCE_IDLE, Lever.SENSORS, Lever.FORCE_IDLE, Lever.SENSORS).inOrder()
        assertThat(
            journal.events<EngineEvent.Battery>().map {
                it.unplugged to it.ok
            },
        ).containsExactly(true to true, false to true).inOrder()
        assertThat(engine.status.value.batteryFrozen).isFalse()
    }

    @Test
    fun `a failed step is reported and everything is still undone`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)
        device.refuse += "write:ForceIdle"

        val proof = engine.quickProof()

        assertThat(proof.ok).isFalse()
        assertThat(proof.steps.filterNot { it.ok }.map { it.kind }).containsExactly(FORCE_IDLE, DEEP_IDLE)
        assertThat(proof.steps.single { it.kind == DEEP_IDLE }.line).isEqualTo("cmd deviceidle get deep: ACTIVE")
        assertThat(proof.steps.single { it.kind == UNDO_BATTERY }.ok).isTrue()
        assertUndone()
    }

    @Test
    fun `the proof is refused with the screen off, in a hold-off, while paused and without the worker`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY, screenOn = false)
        val mark = device.writes.size
        assertThat(engine.quickProof().refused).contains("screen must be on")

        engine.screenOn()
        engine.holdOff(HoldOff.CALL, active = true)
        assertThat(engine.quickProof().refused).contains("CALL")
        engine.holdOff(HoldOff.CALL, active = false)

        engine.pause()
        assertThat(engine.quickProof().refused).contains("paused")
        engine.resume()

        engine.setWritable(Writable.EVERYTHING.copy(forceIdle = false))
        assertThat(engine.quickProof().refused).contains("worker")
        assertThat(device.writes.drop(mark).filterIsInstance<DeviceWrite.Battery>()).isEmpty()
        assertThat(store.state.proofStartedAt).isNull()
    }

    @Test
    fun `a store that cannot save turns nothing on`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)
        val mark = device.writes.size
        store.failSaves = true

        val proof = engine.quickProof()

        assertThat(proof.refused).contains("could not be saved")
        assertThat(device.writes.drop(mark)).isEmpty()
    }

    @Test
    fun `a proof cut short is undone at the next start`() = runTest {
        store.state = PersistedState(
            profileId = "everyday",
            written = mapOf(Lever.FORCE_IDLE to Setting.FORCED, Lever.SENSORS to Setting.Sensors(restricted)),
            proofStartedAt = BASE_TIME,
        )
        device.forced = true
        device.sensors = restricted
        device.batteryUnplugged = true
        val engine = engine()

        engine.startWith(BuiltInProfiles.EVERYDAY)

        assertThat(device.writes).containsAtLeast(DeviceWrite.Unforce, DeviceWrite.EnableSensors, DeviceWrite.Battery(false))
        assertUndone()
        assertThat(engine.status.value.batteryFrozen).isFalse()
    }

    @Test
    fun `without the worker the battery stays marked and is given back once the worker is there`() = runTest {
        store.state = PersistedState(profileId = "everyday", proofStartedAt = BASE_TIME)
        device.batteryUnplugged = true
        val engine = engine()

        engine.startWith(BuiltInProfiles.EVERYDAY, writable = Writable.EVERYTHING.copy(forceIdle = false))
        assertThat(device.writes.filterIsInstance<DeviceWrite.Battery>()).isEmpty()
        assertThat(engine.status.value.batteryFrozen).isTrue()

        device.refuse += "write:Battery"
        engine.setWritable(Writable.EVERYTHING)
        assertWithMessage("a refused reset keeps the mark").that(engine.status.value.batteryFrozen).isTrue()
        assertThat(journal.events<EngineEvent.Battery>().single().ok).isFalse()

        device.refuse -= "write:Battery"
        engine.setWritable(Writable.EVERYTHING)
        assertUndone()
        assertThat(engine.status.value.batteryFrozen).isFalse()
    }

    @Test
    fun `an unplug without an answer is never overtaken by its reset`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)
        device.hang += "write:Battery"

        val proof = engine.quickProof()
        advanceUntilIdle()

        assertThat(proof.steps.single { it.kind == UNPLUG }.line).contains("no answer")
        assertWithMessage("the reset waited for the unplug, so the battery ends up given back").that(device.batteryUnplugged).isFalse()
        assertThat(
            device.writes.filterIsInstance<DeviceWrite.Battery>(),
        ).containsExactly(DeviceWrite.Battery(true), DeviceWrite.Battery(false))
            .inOrder()
        // The reset's answer came too late to be confirmed, so the next start or worker change gives it back again.
        assertThat(engine.status.value.batteryFrozen).isTrue()
    }
}
