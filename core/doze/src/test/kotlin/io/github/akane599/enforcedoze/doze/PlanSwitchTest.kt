// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.profile.Profile
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** SPEC 6.5 day-to-night and night-to-day switching, through the engine with a fake device. */
class PlanSwitchTest {
    private val device = FakeDevice()
    private val store = FakeStore()
    private val journal = FakeJournal()
    private val zone = ZoneId.of("Europe/Istanbul")
    private var now: Instant = at("22:00")
    private val plan = Plan(BuiltInProfiles.EVERYDAY.id, NightSlot(BuiltInProfiles.NIGHT.id, LocalTime.of(23, 0), LocalTime.of(7, 30)))
    private val removed = mutableSetOf<String>()

    private fun at(time: String, day: Int = 9): Instant =
        LocalTime.parse(time).atDate(java.time.LocalDate.of(2026, 11, day)).atZone(ZoneId.of("Europe/Istanbul")).toInstant()

    private fun TestScope.engine(): DozeEngine {
        val clock = object : EngineClock {
            override fun now(): Instant = now

            override fun zone(): ZoneId = zone
        }
        val catalog = ProfileCatalog { id -> BuiltInProfiles.byId(id)?.takeIf { id !in removed } }
        return DozeEngine(
            device.port,
            store,
            journal,
            clock,
            FakeDevice.SELF,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            catalog,
        )
    }

    private suspend fun DozeEngine.started(screenOn: Boolean = true, locked: Boolean = false) =
        start(EngineStart(null, screenOn = screenOn, writable = Writable.EVERYTHING, locked = locked))

    private fun snapshotsReady() {
        store.previous = PreviousSnapshot(now, ConstantsValue.Absent, null, emptyMap(), null, null, null)
        store.stock = StockSnapshot(now, FakeDevice().effective())
    }

    private fun string(profile: Profile) = ConstantsValue.Exact(DeviceIdleSettings.encode(profile.deviceIdle))

    private fun switches() = journal.events.filterIsInstance<EngineEvent.ProfileSwitched>().map { it.profileId to it.reason }

    private fun DozeEngine.slot() = status.value.plan?.slot

    @Test fun `setting the plan in the day applies the day profile at once`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.started()
        engine.setPlan(plan, SwitchReason.OWNER)

        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.EVERYDAY))
        assertThat(engine.status.value.profileId).isEqualTo("everyday")
        assertThat(store.state.plan?.plan).isEqualTo(plan)
        assertThat(switches()).containsExactly("everyday" to SwitchReason.OWNER)
    }

    @Test fun `day to night with the screen on switches when the window-start alarm arrives`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.started()
        engine.setPlan(plan, SwitchReason.OWNER)

        now = at("23:00")
        engine.nightStarted()

        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.NIGHT))
        assertThat(engine.slot()).isEqualTo(PlanSlot.NIGHT)
        assertThat(switches().last()).isEqualTo("night" to SwitchReason.SCHEDULE)
        assertThat(journal.actions.last().cause).isEqualTo(Cause.PROFILE_SWITCH)
    }

    @Test fun `day to night with the screen off waits for the next maintenance window`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.started()
        engine.setPlan(plan, SwitchReason.OWNER)
        now = at("22:40")
        engine.screenOff()

        now = at("23:01")
        engine.nightStarted()
        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.EVERYDAY))
        assertThat(engine.slot()).isEqualTo(PlanSlot.DAY)

        now = at("23:52")
        engine.maintenanceWindow()
        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.NIGHT))
        assertThat(engine.status.value.plan).isEqualTo(PlanState(plan, PlanSlot.NIGHT))
        assertThat(switches().last()).isEqualTo("night" to SwitchReason.SCHEDULE)
    }

    @Test fun `the screen coming on inside the window starts the night when the alarm found it off`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.started(screenOn = false)
        engine.setPlan(plan, SwitchReason.OWNER)
        now = at("23:00")
        engine.nightStarted()

        now = at("23:10")
        engine.screenOn(locked = true)

        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.NIGHT))
        assertThat(engine.slot()).isEqualTo(PlanSlot.NIGHT)
    }

    @Test fun `a screen off inside the window starts the night when no alarm came`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.started()
        engine.setPlan(plan, SwitchReason.OWNER)

        now = at("23:20")
        engine.screenOff()

        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.NIGHT))
        assertThat(engine.slot()).isEqualTo(PlanSlot.NIGHT)
        assertThat(journal.actions.last().cause).isEqualTo(Cause.SCREEN_OFF)
    }

    @Test fun `screen and maintenance events outside the window leave the day alone`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.started()
        engine.setPlan(plan, SwitchReason.OWNER)
        now = at("22:59")
        engine.screenOff()
        engine.maintenanceWindow()
        engine.screenOn()

        assertThat(switches()).containsExactly("everyday" to SwitchReason.OWNER)
    }

    @Test fun `an alarm delivered after the window closed switches nothing`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.started(screenOn = false)
        engine.setPlan(plan, SwitchReason.OWNER)

        now = at("08:10", day = 10)
        engine.nightStarted()
        engine.maintenanceWindow()

        assertThat(engine.slot()).isEqualTo(PlanSlot.DAY)
        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.EVERYDAY))
    }

    @Test fun `night to day waits for the first unlock after the window end`() = runTest {
        snapshotsReady()
        now = at("23:30")
        val engine = engine()
        engine.started()
        engine.setPlan(plan, SwitchReason.OWNER)
        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.NIGHT))

        // A 3 am unlock is still the night.
        now = at("03:00", day = 10)
        engine.screenOn(locked = true)
        engine.unlocked()
        engine.screenOff()
        assertThat(engine.slot()).isEqualTo(PlanSlot.NIGHT)

        // Past the end, the lock screen alone does not end the night; the unlock does.
        now = at("07:45", day = 10)
        engine.screenOn(locked = true)
        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.NIGHT))
        engine.unlocked()

        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.EVERYDAY))
        assertThat(engine.slot()).isEqualTo(PlanSlot.DAY)
        assertThat(switches().last()).isEqualTo("everyday" to SwitchReason.SCHEDULE)
    }

    @Test fun `an unlock that comes before the screen on still ends the night`() = runTest {
        snapshotsReady()
        now = at("23:30")
        val engine = engine()
        engine.started()
        engine.setPlan(plan, SwitchReason.OWNER)
        engine.screenOff()

        now = at("08:00", day = 10)
        engine.unlocked()
        engine.screenOn()

        assertThat(engine.slot()).isEqualTo(PlanSlot.DAY)
        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.EVERYDAY))
    }

    @Test fun `past the end, a screen on without the keyguard is the unlock`() = runTest {
        snapshotsReady()
        now = at("23:30")
        val engine = engine()
        engine.started()
        engine.setPlan(plan, SwitchReason.OWNER)
        engine.screenOff()

        // Inside the window, the night holds.
        now = at("03:00", day = 10)
        engine.screenOn()
        engine.screenOff()
        assertThat(engine.slot()).isEqualTo(PlanSlot.NIGHT)

        now = at("07:45", day = 10)
        engine.screenOn()

        assertThat(engine.slot()).isEqualTo(PlanSlot.DAY)
        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.EVERYDAY))
    }

    @Test fun `an unlock with the screen off books the day and the screen on applies it`() = runTest {
        snapshotsReady()
        now = at("23:30")
        val engine = engine()
        engine.started()
        engine.setPlan(plan, SwitchReason.OWNER)
        engine.screenOff()

        now = at("08:00", day = 10)
        engine.unlocked()
        assertThat(engine.slot()).isEqualTo(PlanSlot.DAY)
        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.NIGHT))

        engine.screenOn()
        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.EVERYDAY))
    }

    @Test fun `a missing slot profile is logged once, not at every screen event`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.started()
        engine.setPlan(plan, SwitchReason.OWNER)
        removed += "night"

        now = at("23:00")
        engine.nightStarted()
        repeat(3) {
            engine.screenOff()
            engine.screenOn()
        }

        assertThat(journal.events.filterIsInstance<EngineEvent.ProfileMissing>()).hasSize(1)
        assertThat(engine.status.value.profileId).isEqualTo("everyday")
    }

    @Test fun `an override whose profile is gone ends, and the plan applies again`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.started()
        engine.setPlan(plan, SwitchReason.OWNER)
        engine.setProfile(BuiltInProfiles.POCKET, SwitchReason.TASKER)
        removed += "pocket"

        // The next start finds the override's profile gone.
        val restarted = engine()
        restarted.started()

        assertThat(restarted.status.value.plan?.override).isNull()
        assertThat(restarted.status.value.profileId).isEqualTo("everyday")
        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.EVERYDAY))
        assertThat(journal.events).contains(EngineEvent.ProfileMissing(now, "pocket", SwitchReason.SCHEDULE))
    }

    @Test fun `a profile applied outside the plan holds until the plan is followed again`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.started()
        engine.setPlan(plan, SwitchReason.OWNER)
        engine.setProfile(BuiltInProfiles.POCKET, SwitchReason.TASKER)

        now = at("23:00")
        engine.nightStarted()
        assertThat(engine.status.value.profileId).isEqualTo("pocket")
        assertThat(engine.status.value.plan?.override).isEqualTo("pocket")

        engine.followPlan(SwitchReason.TASKER)
        assertThat(engine.status.value.profileId).isEqualTo("night")
        assertThat(engine.status.value.plan?.override).isNull()
        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.NIGHT))
    }

    @Test fun `a restart keeps the night until an unlock, and ends it at once when already unlocked`() = runTest {
        snapshotsReady()
        store.state = PersistedState(profileId = "night", plan = PlanState(plan, PlanSlot.NIGHT))
        now = at("08:00", day = 10)

        val asleep = engine()
        asleep.started(screenOn = false)
        assertThat(asleep.status.value.profileId).isEqualTo("night")

        val awake = engine()
        awake.started(screenOn = true, locked = false)
        assertThat(awake.status.value.profileId).isEqualTo("everyday")
        assertThat(store.state.plan?.slot).isEqualTo(PlanSlot.DAY)
    }

    @Test fun `a restart inside the window with the screen on starts the night`() = runTest {
        snapshotsReady()
        store.state = PersistedState(profileId = "everyday", plan = PlanState(plan))
        now = at("23:20")

        val engine = engine()
        engine.started(screenOn = true)

        assertThat(engine.status.value.profileId).isEqualTo("night")
        assertThat(device.constants).isEqualTo(string(BuiltInProfiles.NIGHT))
    }

    @Test fun `a restart inside the window with the screen off waits for a maintenance window`() = runTest {
        snapshotsReady()
        store.state = PersistedState(profileId = "everyday", plan = PlanState(plan))
        now = at("23:20")

        val engine = engine()
        engine.started(screenOn = false)
        assertThat(engine.status.value.profileId).isEqualTo("everyday")

        now = at("23:40")
        engine.maintenanceWindow()
        assertThat(engine.status.value.profileId).isEqualTo("night")
    }

    @Test fun `a slot whose profile was deleted is logged and the current profile stays`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.started()
        engine.setPlan(plan, SwitchReason.OWNER)
        removed += "night"

        now = at("23:00")
        engine.nightStarted()

        assertThat(engine.status.value.profileId).isEqualTo("everyday")
        assertThat(journal.events.last()).isEqualTo(EngineEvent.ProfileMissing(now, "night", SwitchReason.SCHEDULE))
    }

    @Test fun `a plan without a night slot never switches`() = runTest {
        snapshotsReady()
        val engine = engine()
        engine.started()
        engine.setPlan(Plan(BuiltInProfiles.POCKET.id), SwitchReason.RECOMMENDATION)
        now = at("23:00")
        engine.nightStarted()
        now = at("08:00", day = 10)
        engine.unlocked()

        assertThat(switches()).containsExactly("pocket" to SwitchReason.RECOMMENDATION)
    }
}
