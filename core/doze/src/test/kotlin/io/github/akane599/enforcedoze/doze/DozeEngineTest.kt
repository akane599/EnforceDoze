// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.doze.ActionRecord.Outcome
import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DozeEngineTest {
    private val device = FakeDevice()
    private val store = FakeStore()
    private val journal = FakeJournal()
    private val restricted = DeviceWrite.RestrictSensors(FakeDevice.SELF)
    private val tasker = "inactive_to=600000,sensing_to=0"

    private fun TestScope.engine(): DozeEngine {
        val clock = EngineClock { BASE_TIME.plusMillis(testScheduler.currentTime) }
        return DozeEngine(device.port, store, journal, clock, FakeDevice.SELF, backgroundScope, StandardTestDispatcher(testScheduler))
    }

    private suspend fun DozeEngine.startWith(profile: Profile, writable: Writable = Writable.EVERYTHING) =
        start(EngineStart(profile, screenOn = true, writable = writable))

    /** As after onboarding: the string as found, and stock captured with it deleted. */
    private fun snapshotsReady(found: ConstantsValue = ConstantsValue.Absent) {
        store.previous = PreviousSnapshot(BASE_TIME, found, null, emptyMap(), null, null, null)
        store.stock = StockSnapshot(BASE_TIME, FakeDevice().effective())
    }

    private fun string(profile: Profile) = DeviceIdleSettings.encode(profile.deviceIdle)

    private fun writesSince(mark: Int) = device.writes.drop(mark)

    private fun DozeEngine.state(lever: Lever) = status.value.levers.getValue(lever).state

    @Test
    fun `start reads every lever before the first write, then applies and verifies the string`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)

        val firstWrite = device.log.indexOfFirst { it.startsWith("write:") }
        assertThat(device.log.take(firstWrite)).containsAtLeast("read:constants", "read:deviceIdle", "read:sensors", "read:allowlist")
        assertThat(device.constants).isEqualTo(ConstantsValue.Exact(string(BuiltInProfiles.EVERYDAY)))
        val action = journal.actions.single()
        assertThat(action.outcome).isEqualTo(Outcome.OK)
        assertThat(action.cause).isEqualTo(Cause.START)
        assertThat(engine.state(Lever.CONSTANTS)).isEqualTo(LeverState.APPLIED)
        assertThat(engine.status.value.partlyApplied).isFalse()
        assertThat(
            store.state.written[Lever.CONSTANTS],
        ).isEqualTo(Setting.Constants(ConstantsValue.Exact(string(BuiltInProfiles.EVERYDAY))))
    }

    @Test
    fun `nothing is written before both snapshots, and the stock capture puts the string back exactly`() = runTest {
        device.foreignWrite(tasker)
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)

        assertThat(device.writes).isEmpty()
        assertThat(store.previous?.constants).isEqualTo(ConstantsValue.Exact(tasker))
        assertThat(store.previous?.writer).isEqualTo("com.android.shell")
        assertThat(engine.state(Lever.CONSTANTS)).isEqualTo(LeverState.WAITING)

        val result = engine.captureStock()

        assertThat(result.refused).isNull()
        assertThat(result.stock?.effective).isEqualTo(FakeDevice().effective())
        assertThat(result.actions.map { it.outcome }).containsExactly(Outcome.OK, Outcome.OK)
        assertThat(device.writes.take(2)).containsExactly(DeviceWrite.SetConstants(null), DeviceWrite.SetConstants(tasker)).inOrder()
        // With both snapshots in place the profile applies.
        assertThat(device.constants).isEqualTo(ConstantsValue.Exact(string(BuiltInProfiles.EVERYDAY)))
        assertThat(engine.state(Lever.CONSTANTS)).isEqualTo(LeverState.APPLIED)
    }

    @Test
    fun `the stock capture needs the screen on`() = runTest {
        val engine = engine()
        engine.start(EngineStart(BuiltInProfiles.EVERYDAY, screenOn = false, writable = Writable.EVERYTHING))
        assertThat(engine.captureStock().refused).contains("screen")
        assertThat(device.writes).isEmpty()
    }

    @Test
    fun `screen off never writes constants and reads the string once`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)
        val mark = device.writes.size
        device.log.clear()

        engine.screenOff()
        assertThat(device.log).containsExactly("read:constants")
        engine.screenOn()

        assertThat(writesSince(mark)).isEmpty()
    }

    @Test
    fun `classic restricts sensors then forces idle at screen off, and undoes both at screen on`() = runTest {
        val engine = engine()
        engine.startWith(BuiltInProfiles.CLASSIC)
        assertThat(device.writes).isEmpty()
        assertThat(engine.state(Lever.FORCE_IDLE)).isEqualTo(LeverState.ARMED)

        engine.screenOff()
        assertThat(device.writes).containsExactly(restricted, DeviceWrite.ForceIdle).inOrder()
        assertThat(journal.actions.map { it.outcome }).containsExactly(Outcome.OK, Outcome.OK)
        assertThat(engine.state(Lever.FORCE_IDLE)).isEqualTo(LeverState.APPLIED)
        assertThat(engine.state(Lever.SENSORS)).isEqualTo(LeverState.APPLIED)

        engine.screenOn()
        assertThat(writesSince(2)).containsExactly(DeviceWrite.Unforce, DeviceWrite.EnableSensors).inOrder()
        assertThat(device.forced).isFalse()
        assertThat(device.sensors).isEqualTo(SensorValue.Normal)
        assertThat(device.writes.filterIsInstance<DeviceWrite.SetConstants>()).isEmpty()
    }

    @Test
    fun `hold-offs lift forced idle and sensors until the last one ends, and constants stay`() = runTest {
        snapshotsReady()
        val engine = engine()
        val strictKeepAll = BuiltInProfiles.STRICT
        store.state = PersistedState(allowlistKeep = emptySet())
        engine.startWith(strictKeepAll)
        engine.screenOff()
        assertThat(device.forced).isTrue()
        val string = device.constants
        val mark = device.writes.size

        engine.holdOff(HoldOff.CALL, active = true)
        assertThat(writesSince(mark)).containsExactly(DeviceWrite.Unforce, DeviceWrite.EnableSensors).inOrder()
        assertThat(engine.state(Lever.FORCE_IDLE)).isEqualTo(LeverState.HELD_OFF)
        engine.holdOff(HoldOff.AUDIO, active = true)
        engine.holdOff(HoldOff.CALL, active = false)
        assertThat(writesSince(mark)).hasSize(2)

        engine.holdOff(HoldOff.AUDIO, active = false)
        assertThat(writesSince(mark + 2)).containsExactly(restricted, DeviceWrite.ForceIdle).inOrder()
        assertThat(device.constants).isEqualTo(string)
        assertThat(writesSince(mark).filterIsInstance<DeviceWrite.SetConstants>()).isEmpty()
        assertThat(journal.events<EngineEvent.HoldOffStarted>().map { it.holdOff }).containsExactly(HoldOff.CALL, HoldOff.AUDIO)
        assertThat(journal.events<EngineEvent.HoldOffEnded>().map { it.holdOff }).containsExactly(HoldOff.CALL, HoldOff.AUDIO)
    }

    @Test
    fun `charging at screen off keeps forced idle off`() = runTest {
        val engine = engine()
        engine.startWith(BuiltInProfiles.CLASSIC)
        engine.holdOff(HoldOff.CHARGING, active = true)
        engine.screenOff()
        assertThat(device.writes).isEmpty()
        engine.holdOff(HoldOff.CHARGING, active = false)
        assertThat(device.writes).containsExactly(restricted, DeviceWrite.ForceIdle).inOrder()
    }

    @Test
    fun `two corrections in 59 minutes, and a third is not made`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)
        val ours = device.constants

        suspend fun foreignNightAfter(minutes: Int) {
            delay(minutes.minutes)
            device.foreignWrite("inactive_to=1800000")
            engine.screenOff()
            engine.screenOn()
        }
        foreignNightAfter(0)
        assertThat(device.constants).isEqualTo(ours)
        foreignNightAfter(30)
        assertThat(device.constants).isEqualTo(ours)
        foreignNightAfter(29)

        assertThat(device.constants).isEqualTo(ConstantsValue.Exact("inactive_to=1800000"))
        assertThat(journal.events<EngineEvent.Drift>()).hasSize(3)
        assertThat(journal.events<EngineEvent.Drift>().map { it.writer }.toSet()).containsExactly("com.android.shell")
        assertThat(journal.actions.count { it.cause == Cause.DRIFT }).isEqualTo(2)
        assertThat(journal.events<EngineEvent.LoopGuardTripped>().single().lever).isEqualTo(Lever.CONSTANTS)
        assertThat(engine.status.value.guarded).containsExactly(Lever.CONSTANTS)
        assertThat(engine.state(Lever.CONSTANTS)).isEqualTo(LeverState.GUARDED)
        assertThat(store.state.guarded).containsExactly(Lever.CONSTANTS)

        engine.clearGuard(Lever.CONSTANTS)
        assertThat(device.constants).isEqualTo(ours)
    }

    @Test
    fun `corrections at 0, 59 and 61 minutes are all made`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)
        val ours = device.constants
        for (gap in listOf(0, 59, 2)) {
            delay(gap.minutes)
            device.foreignWrite("inactive_to=1800000")
            engine.foreignWriteSeen()
            assertThat(device.constants).isEqualTo(ours)
        }
        assertThat(journal.actions.count { it.cause == Cause.DRIFT }).isEqualTo(3)
        assertThat(journal.events<EngineEvent.LoopGuardTripped>()).isEmpty()
    }

    @Test
    fun `drift is logged but not corrected with re-apply off`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)
        engine.setReapplyOnDrift(false)
        device.foreignWrite("inactive_to=1800000")
        engine.screenOff()

        assertThat(journal.events<EngineEvent.Drift>().single().found).isEqualTo("inactive_to=1800000")
        assertThat(device.constants).isEqualTo(ConstantsValue.Exact("inactive_to=1800000"))
        assertThat(engine.state(Lever.CONSTANTS)).isEqualTo(LeverState.PARTLY)
    }

    @Test
    fun `drift is caught on the effective values when the string cannot be read`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)
        device.canReadConstants = false
        device.foreignWrite("inactive_to=1800000")
        engine.screenOff()

        val drift = journal.events<EngineEvent.Drift>().single()
        assertThat(drift.found).contains("inactive_to=300000 (shows +30m0s0ms)")
        assertThat(drift.writer).isNull()
        assertThat(device.constants).isEqualTo(ConstantsValue.Exact(string(BuiltInProfiles.EVERYDAY)))
        assertThat(journal.actions.last().outcome).isEqualTo(Outcome.OK)
    }

    @Test
    fun `a restart reads first and logs what drifted while stopped`() = runTest {
        snapshotsReady()
        val ours = Setting.Constants(ConstantsValue.Exact(string(BuiltInProfiles.EVERYDAY)))
        store.state = PersistedState(profileId = "everyday", written = mapOf(Lever.CONSTANTS to ours))
        device.foreignWrite(tasker)
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)

        assertThat(device.log.first()).startsWith("read:")
        val drift = journal.events<EngineEvent.Drift>().single()
        assertThat(drift.whileStopped).isTrue()
        assertThat(drift.writer).isEqualTo("com.android.shell")
        assertThat(journal.actions.single().cause).isEqualTo(Cause.DRIFT)
        assertThat(device.constants).isEqualTo(ours.value)
    }

    @Test
    fun `a restart while idle is forced releases it at screen on`() = runTest {
        store.state = PersistedState(profileId = "classic", written = mapOf(Lever.FORCE_IDLE to Setting.FORCED))
        device.forced = true
        val engine = engine()
        engine.startWith(BuiltInProfiles.CLASSIC)
        assertThat(device.writes).containsExactly(DeviceWrite.Unforce)
        assertThat(device.forced).isFalse()
    }

    @Test
    fun `a write without an answer in 5 s leaves the read-back to decide`() = runTest {
        val engine = engine()
        engine.startWith(BuiltInProfiles.CLASSIC)
        device.hang += "write:ForceIdle"
        engine.screenOff()

        val action = journal.actions.single { it.lever == Lever.FORCE_IDLE }
        assertThat(action.outcome).isEqualTo(Outcome.TIMEOUT)
        assertThat(action.readBack).isEqualTo("released")
        assertThat(currentTime).isLessThan(FakeDevice.HANG_MILLIS)
        assertThat(engine.state(Lever.FORCE_IDLE)).isEqualTo(LeverState.PARTLY)

        // The late call lands; screen on still releases it.
        delay(10.seconds)
        assertThat(device.forced).isTrue()
        engine.screenOn()
        assertThat(device.forced).isFalse()
    }

    @Test
    fun `a lever with no read-back shows unknown, never applied`() = runTest {
        val engine = engine()
        engine.startWith(BuiltInProfiles.CLASSIC)
        device.canReadDeviceIdle = false
        engine.screenOff()
        assertThat(journal.actions.single { it.lever == Lever.FORCE_IDLE }.outcome).isEqualTo(Outcome.UNVERIFIED)
        assertThat(engine.state(Lever.FORCE_IDLE)).isEqualTo(LeverState.UNKNOWN)
        assertThat(engine.status.value.partlyApplied).isTrue()
    }

    @Test
    fun `a refused write is logged and not booked`() = runTest {
        val engine = engine()
        engine.startWith(BuiltInProfiles.CLASSIC)
        device.refuse += "write:ForceIdle"
        engine.screenOff()
        assertThat(journal.actions.single { it.lever == Lever.FORCE_IDLE }.outcome).isEqualTo(Outcome.FAILED)
        engine.screenOn()
        assertThat(device.writes).doesNotContain(DeviceWrite.Unforce)
    }

    @Test
    fun `no work is left pending after a screen-off batch`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.NIGHT)
        engine.screenOff()
        assertThat(engine.callsIdle).isTrue()
        val time = currentTime
        advanceUntilIdle()
        assertThat(currentTime).isEqualTo(time)
    }

    @Test
    fun `restore stock reproduces stock json exactly, then pauses`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.POCKET)
        engine.screenOff()
        assertThat(device.sensors).isEqualTo(SensorValue.Restricted(FakeDevice.SELF))

        val result = engine.restoreStock()

        assertThat(result.ok).isTrue()
        assertThat(result.stockDifferences).isEmpty()
        assertThat(device.constants).isEqualTo(ConstantsValue.Absent)
        assertThat(device.effective()).isEqualTo(store.stock?.effective)
        assertThat(device.sensors).isEqualTo(SensorValue.Normal)
        assertThat(engine.status.value.mode).isEqualTo(Mode.PAUSED)
        assertThat(journal.events<EngineEvent.ModeChanged>().single().mode).isEqualTo(Mode.PAUSED)

        val mark = device.writes.size
        engine.screenOn()
        engine.screenOff()
        assertThat(writesSince(mark)).isEmpty()
    }

    @Test
    fun `return to previous writes the owner's string back exactly and keeps hands off`() = runTest {
        device.foreignWrite(tasker)
        store.stock = StockSnapshot(BASE_TIME, FakeDevice().effective())
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)
        assertThat(device.constants).isEqualTo(ConstantsValue.Exact(string(BuiltInProfiles.EVERYDAY)))

        val result = engine.returnToPrevious()

        assertThat(result.ok).isTrue()
        assertThat(device.constants).isEqualTo(ConstantsValue.Exact(tasker))
        assertThat(engine.status.value.mode).isEqualTo(Mode.HANDS_OFF)
        val mark = device.writes.size
        device.log.clear()
        engine.screenOff()
        device.foreignWrite("inactive_to=1800000")
        engine.foreignWriteSeen()
        engine.screenOn()
        assertThat(writesSince(mark)).isEmpty()
        assertThat(device.log).isEmpty()
    }

    @Test
    fun `a lost worker leaves the other levers working and releases forced idle once it is back`() = runTest {
        val engine = engine()
        engine.startWith(BuiltInProfiles.CLASSIC)
        engine.screenOff()
        val mark = device.writes.size

        engine.setWritable(Writable(constants = true, sensors = true))
        engine.screenOn()
        assertThat(writesSince(mark)).containsExactly(DeviceWrite.EnableSensors)
        assertThat(engine.status.value.forcedWithoutWorker).isTrue()
        assertThat(engine.state(Lever.FORCE_IDLE)).isEqualTo(LeverState.NOT_WRITABLE)

        engine.setWritable(Writable.EVERYTHING)
        assertThat(writesSince(mark)).containsExactly(DeviceWrite.EnableSensors, DeviceWrite.Unforce).inOrder()
        assertThat(engine.status.value.forcedWithoutWorker).isFalse()
        assertThat(device.forced).isFalse()
    }

    @Test
    fun `a trimmed allowlist keeps the chosen apps and is put back after`() = runTest {
        snapshotsReady()
        device.allowlist = setOf("a", "b", "c")
        store.state = PersistedState(allowlistKeep = setOf("a"))
        val engine = engine()
        engine.startWith(BuiltInProfiles.STRICT)

        assertThat(device.allowlist).containsExactly("a")
        assertThat(store.state.ownerAllowlist).containsExactly("a", "b", "c")
        assertThat(journal.actions.filter { it.lever == Lever.ALLOWLIST }.map { it.outcome }).containsExactly(Outcome.OK, Outcome.OK)

        engine.setProfile(BuiltInProfiles.EVERYDAY, SwitchReason.OWNER)
        assertThat(device.allowlist).containsExactly("a", "b", "c")
        assertThat(store.state.ownerAllowlist).isNull()
        assertThat(store.state.written).doesNotContainKey(Lever.ALLOWLIST)
        assertThat(journal.events<EngineEvent.ProfileSwitched>().single().reason).isEqualTo(SwitchReason.OWNER)
    }

    @Test
    fun `switching to a profile without constants deletes the engine's string`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.startWith(BuiltInProfiles.EVERYDAY)
        engine.setProfile(BuiltInProfiles.CLASSIC, SwitchReason.SCHEDULE)
        assertThat(device.constants).isEqualTo(ConstantsValue.Absent)
        assertThat(journal.actions.last().outcome).isEqualTo(Outcome.OK)
        assertThat(engine.state(Lever.CONSTANTS)).isEqualTo(LeverState.UNUSED)
    }
}
