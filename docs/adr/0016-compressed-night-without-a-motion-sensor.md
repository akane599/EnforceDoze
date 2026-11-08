# 0016. Compressed night on a device without a motion sensor

- Status: Accepted
- Date: 2026-11-08
- Phase: P2
- Spec: 13.2, 13.3, 8.4; answers an open point of ADR 0015

## Context

SPEC 13.2 assumes the emulator walks to deep idle on its own once the timers are short. The first P2
run showed it does not:

- Profile: Test Everyday (`inactive_to` 30 s and the rest of 13.2), unplugged.
- Event log: `device_idle` went to INACTIVE (`[1,no activity]`) at screen off, then logged nothing
  for 5 minutes.
- `dumpsys deviceidle`: `mState=INACTIVE`, no `mNextAlarmTime`.
- Verdict: Did not doze, cause unknown. That verdict is correct.

The reason is in AOSP `DeviceIdleController.scheduleAlarmLocked` (`android-17.0.0_r1` line 4366;
`android-16.0.0_r1` line 4274). When `mUseMotionSensor && mMotionSensor == null`, it returns without
scheduling an alarm for any state before IDLE, except QUICK_DOZE_DELAY. The source comment says idling
"is still possible to manually poke … by pretending like the alarm is going off".

`mUseMotionSensor` comes from the framework resource `config_autoPowerModeUseMotionSensor` (line 2612),
so it cannot be changed at run time. The emulators report `mUseMotionSensor=true mMotionSensor=null`
on API 35, 36 and 37 (fixtures; ADR 0004). From IDLE on, the same function schedules alarms
(`setIdleUntil`). So maintenance windows, `idle_to`, `idle_factor` and `max_idle_to` behave normally.

The same source shows when each walk timer is read:

- `inactive_to` is copied into `mInactiveTimeout` when the device becomes active
  (`becomeActiveLocked`, line 3650 and 3662 on 17; line 3586 on 16), for example at screen on or when
  charging starts. It is used at the next screen off.
- `idle_after_inactive_to`, `sensing_to`, `locating_to` and `idle_to` are read from the constants
  each time their step is scheduled (`stepIdleStateLocked`, line 3957 on 17).

So a constants write never reschedules a pending step. This is the open point of ADR 0015.

## Decision

1. On a device that reports `mUseMotionSensor=true mMotionSensor=null`, the P2 emulator script plays
   the missing alarms. It runs `cmd deviceidle step deep` at the profile's timers until the state is
   IDLE:
   - `inactive_to` after screen off;
   - then `idle_after_inactive_to`;
   - then `sensing_to`;
   - then `locating_to`.

   From IDLE on, the script does nothing more; every later transition is the system's. The summary
   says when the walk was stepped. The stepped transitions carry the reason `s:shell` in the event log,
   so the evidence shows them.
2. Test Classic needs no stepping: forced idle reaches IDLE through `force-idle`, and the system
   schedules everything after that.
3. The verdict code does not change. It judges the transitions the device logged.
4. Motion resets stay device-only, as SPEC 13.3 already allows (ADR 0004). They are covered by
   synthetic verdict tests and parser fixtures.

## Consequences

- SPEC 13.2 gets a step marked "(ADR 0016)".
- Everyday's verdict on the emulator proves the following:
  - the profile applies;
  - the system's own idle and maintenance cycle runs;
  - evidence is collected;
  - the verdict is computed.

  It does not prove the emulator's walk timing. That is checked on the phone at P7 (SPEC 13.4).
- P3 recorder: the expected walk uses the `inactive_to` that was in force when the device last
  became active, not the current profile's value. Otherwise the first session after a profile switch
  can look late.
- P3 report: a "Did not doze" on a device without a motion sensor gets a note that the system
  schedules no walk there. The cause stays "unknown", as SPEC 8.4 lists it.
- Revisit if a later API stops skipping the alarms, or if an emulator image gains a significant-motion
  sensor.
