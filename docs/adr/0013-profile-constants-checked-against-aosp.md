# 0013. Profile constants checked against AOSP 16 and 17

- Status: Accepted
- Date: 2026-11-08
- Phase: P2
- Spec: 5.1, 6.1, 6.2

## Context

SPEC 6.2 asks for every key in the profile table to be checked, by name and by meaning, against
AOSP `DeviceIdleController` for API 37 before P2, with renamed or removed keys mapped in an ADR.
Source: `apex/jobscheduler/service/java/com/android/server/DeviceIdleController.java` at
`android-16.0.0_r1` and `android-17.0.0_r1`. Both tags define the same 36 `KEY_*` names
(`AospDeviceIdle.KEYS`, checked by the parser tests). `stepIdleStateLocked` differs in one place
only: on API 37, `idle_after_inactive_to=0` with the flag `enable_non_scheduled_exit_from_idle_pending`
passes straight through IDLE_PENDING. `handleMotionDetectedLocked` and its callers are identical.

## Decision

No key was renamed or removed, so the table in SPEC 6.2 stands. What each key does, as the code
uses it:

| Key | Field | Where it acts | Meaning |
| --- | --- | --- | --- |
| `inactive_to` | `INACTIVE_TIMEOUT` | `becomeActiveLocked(…, INACTIVE_TIMEOUT, …)` sets `mInactiveTimeout`; `becomeInactiveIfAppropriateLocked` schedules the step | Time in INACTIVE after screen off and unplug before IDLE_PENDING. Also the INACTIVE time after the accelerometer check in SENSING reports movement (`onAnyMotionResult` → `non_stationary`) |
| `idle_after_inactive_to` | `IDLE_AFTER_INACTIVE_TIMEOUT` | `case STATE_INACTIVE`: `startMonitoringMotionLocked()`, then `scheduleAlarmLocked(delay)` | Time in IDLE_PENDING. Significant-motion monitoring starts on entering this state, which is why the phone's `[0,motion]` resets come from IDLE_PENDING (audit §4.4) |
| `sensing_to` | `SENSING_TIMEOUT` | `case STATE_IDLE_PENDING`: only `if (mUseMotionSensor && mAnyMotionDetector.hasSensor())` | Longest wait for the accelerometer check in SENSING. Without a motion sensor, SENSING passes at once |
| `locating_to` | `LOCATING_TIMEOUT` | `case STATE_SENSING`: only `if (mIsLocationPrefetchEnabled)`, which is false when `Flags.removeIdleLocation()` | Longest wait for a location fix. No effect on API 36–37 builds with `remove_idle_location=true` (both emulators and the owner's phone, ADR 0004). Kept in the profiles: harmless there, and it still matters on builds without the flag |
| `motion_inactive_to` | `MOTION_INACTIVE_TIMEOUT` | the motion sensor listener (and `cmd deviceidle motion`) → `motionLocked()` → `handleMotionDetectedLocked(MOTION_INACTIVE_TIMEOUT, "motion")` → `mInactiveTimeout`; also `isStationaryLocked` and the motion-timeout alarm | INACTIVE time after a significant-motion reset, used in place of `inactive_to`; also how long without motion before stationary listeners are told the device is still |
| `light_after_inactive_to` | `LIGHT_IDLE_AFTER_INACTIVE_TIMEOUT` | `becomeInactiveIfAppropriateLocked` → `scheduleLightAlarmLocked` | Time from INACTIVE to the first light-idle step |
| `idle_to` | `IDLE_TIMEOUT` | `case STATE_LOCATING` / `STATE_QUICK_DOZE_DELAY` reset `mNextIdleDelay`; `case STATE_IDLE_MAINTENANCE` schedules it | Length of the first deep IDLE window |
| `idle_factor` | `IDLE_FACTOR` | `mNextIdleDelay = mNextIdleDelay * IDLE_FACTOR` after each IDLE entry | Growth of each later IDLE window |
| `max_idle_to` | `MAX_IDLE_TIMEOUT` | `Math.min(mNextIdleDelay, MAX_IDLE_TIMEOUT)`, and never below `idle_to` | Cap on an IDLE window |

What this means for the profiles in SPEC 6.1:

- **Everyday.** Deep idle about 10 minutes after a still screen off: 5 min + 5 min + up to 1 min
  of sensing, and LOCATING is skipped. `motion_inactive_to` 5 min makes a re-walk after a
  significant motion as slow as the first walk, not One UI's stock value.
- **Pocket.** 5 min + 3 min + 30 s. With lever C on, significant motion and the any-motion
  check cannot reset it.
- **Night.** 2 min + 2 min + 30 s. IDLE windows of 2 h, 4 h, then 8 h (`idle_factor` 2, capped by
  `max_idle_to` 8 h).
- **Strict.** Same IDLE windows as Night, capped at 12 h.
- **Classic and Strict.** Forced idle (`force-idle deep`) skips the walk, so their walk timers stay
  stock, as the table already says.
- **No profile uses `idle_after_inactive_to=0`.** Custom profiles inherit the guardrails in SPEC 6.3;
  0 is allowed only where it behaves the same on both releases, so the editor refuses values
  below 1 s for this key.

## Consequences

- The plain-language lines in the profile editor (SPEC 6.3) use the meanings in the table. They
  note that `locating_to` has no effect on this phone and that `sensing_to` matters only where a
  motion sensor exists.
- Parser and engine tests take stock from the device, never from AOSP defaults (ADR 0004).
- One UI's own keys (`skip_sensing`, `skip_locating`) are not AOSP keys. Their effect is checked
  on the phone in a planned session, never assumed (ADR 0012).
