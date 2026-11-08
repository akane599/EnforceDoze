# 0024. Plan switching lives in the engine; the host only delivers events

- Status: Accepted
- Date: 2026-11-08
- Phase: P4
- Spec: 6.5, 11.1, 12.4; extends ADR 0015

## Context

SPEC 6.5 switches a plan's slots as follows:

- **Day to night:** a non-wakeup alarm fires at the window start. The switch happens when the phone
  is next awake. If the screen is off then, it waits for the next maintenance window.
- **Night to day:** at the first unlock after the window end.
- Each switch is a log row with its reason.

SPEC 11.1 has two actions:

- `APPLY_PROFILE` applies one profile.
- `APPLY_PLAN` applies the plan.

The spec leaves three cases open:

- what `APPLY_PROFILE` does to a running plan;
- what happens after a restart in the middle of a window;
- what happens when a slot's profile was deleted.

The engine owns no timer (ADR 0015). It works only when told about an event, and every write goes
through its single actor (SPEC 12.4).

## Decision

**The engine holds the plan and its slot in the book.** `state.json` gains `plan`:

- the day profile id;
- the night id and its window as local times;
- the slot;
- an override.

So a restart neither repeats nor skips a switch. The field is additive with defaults, so a file
without it reads as "no plan". The rules are pure steps (`PlanSchedule`), each unit-tested
(`PlanSwitchTest`):

| Event (host to engine) | Rule |
| --- | --- |
| `setPlan` (owner, Recommended) | The slot the clock is in, at once; any override ends |
| `nightStarted` (window-start alarm) | Screen on, inside the window: night now. Screen off: nothing, the next event below starts it. After the window closed (a late delivery): nothing |
| `maintenanceWindow` (deep idle left with the screen off) | Day slot inside the window: night |
| `screenOn`, `screenOff` | Same as a maintenance window. The phone is awake, and at screen off the session then starts with the night's constants |
| `unlocked` (`USER_PRESENT`) | Night past its end: day. Inside the window, a 3 am unlock keeps the night. The lock screen alone never ends it |
| `start` (process or service restart) | Day slot inside the window: as if the alarm arrived now (screen on: night; screen off: the next event). Night past its end: day only if the screen is on and unlocked, else wait for the unlock |
| `setProfile` (owner tap, `APPLY_PROFILE`) | An override. The plan's slot keeps moving underneath, the override stays until `followPlan` (`APPLY_PLAN`) or a new plan |

In short, the night begins at the first moment the phone is awake inside the window. With the
screen off, that is a maintenance window or the screen itself, as SPEC 6.5 asks. No "pending" flag is
kept: an alarm that found the screen off, or an alarm never set (see below), leaves the night to the
next of those events.

**Profiles are looked up at switch time** through a new port, `ProfileCatalog`:

- An edited custom profile applies as edited.
- A deleted one logs `PROFILE_MISSING` with the reason, and the current profile stays.

The app passes a catalog over its `ProfileStore`. Tests and the default use the built-ins.

**Every switch** is a `ProfileSwitched` event with its reason: `SCHEDULE`, `OWNER`, `TASKER` or
`RECOMMENDATION`. A switch then reconciles with cause `PROFILE_SWITCH`, or `UNLOCK` or
`SCREEN_ON` when that event came with it.

**The host owns the alarm.** `EngineHost`:

- sets one `AlarmManager.RTC` alarm at the next window start through a `PendingIntent` to a
  non-exported receiver. RTC is non-wakeup and `set()` is inexact, so it needs no exact-alarm
  permission and adds no wakeup (SPEC 1.3). It uses `setWindow` with a 10-minute window: plain
  `set()` may deliver up to three quarters of the time-to-trigger late, and AOSP 17 stretches any
  shorter window to 10 minutes (`AlarmManager.setWindow`, `android-17.0.0_r1`). It is a listener
  alarm, so it lives only while the process does, and a restart is covered by `start`. It is
  rescheduled:
  - on start;
  - on a plan change;
  - on each delivery;
  - on a time or time-zone change.

  It is never set while the screen is off (SPEC 1.3): a reschedule that falls then waits for the
  next screen on. Until then the night still begins at a maintenance window.
- forwards the following to the engine:
  - `ACTION_DEVICE_IDLE_MODE_CHANGED` when idle ends with the screen off, as `maintenanceWindow`;
  - `SCREEN_ON`, `SCREEN_OFF` and `USER_PRESENT`, as before.

Day to night is never polled.

## Consequences

- No second opinion was asked (ADR 0023). This extends the command model of ADR 0015 with four
  commands of the same shape, and `state.json` stays an internal, unreleased file whose new field is
  optional. The second opinion of ADR 0015 still covers the model itself.
- `DozeEngine` takes one more constructor port; its `LongParameterList` warning is suppressed with
  the reason (ADR 0025).
- The automation limit (one switch per 30 s, SPEC 11.1) is applied by the automation API in P6,
  before it reaches the engine.
- The P4 emulator gate checks both switches on `ed-api37`: the alarm at the window start, the
  pending night at a maintenance window, and the day at the first unlock after the window end.
