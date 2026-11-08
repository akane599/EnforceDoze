# 0019. Alarm wakeups come from the alarm dump

- Status: Accepted
- Date: 2026-11-08
- Phase: P3
- Spec: 8.1 (waker row), 8.2 (the screen-on batch), 15 (the app's own wakeups); amends ADR 0018

## Context

ADR 0018 took the battery history's `wakeupap=<uid>` as the wakeup alarms of an app. Reading
`BatteryStatsImpl` at `android-16.0.0_r1` and `android-17.0.0_r1` shows that this is wrong:

- `EVENT_WAKEUP_AP` (`wakeupap`) is recorded only by `noteMobileRadioApWakeupLocked` and
  `noteWifiRadioApWakeupLocked` (lines 5463 and 6459 on 17, 5925 and 6921 on 16). It means a network
  packet for that uid woke the application processor.
- `noteWakupAlarmLocked` (line 4420 on 17, 4910 on 16) counts wakeup alarms only in the per-package
  statistics. Nothing reaches the history.
- `±alarm` events reach the history only while full history recording is on.
  `noteAlarmStartOrFinishLocked` returns early otherwise (line 4382 on 17, 4872 on 16). Turning it on
  changes device state and makes the history grow faster, so the app leaves it off.

The same reading corrects ADR 0018's wakelock description:

- `-wake_lock=<uid>:"tag"` names the last holder to release, when the nesting count reaches zero
  (line 4598 on 17, 5097 on 16).
- `BatteryStatsHistory.maybeUpdateWakelockTag` (line 1261 on 17) can print an unsigned
  `wake_lock=<uid>:"tag"` in the middle of a held stretch. That happens when a holder that is
  important for logging takes over from an unimportant first holder.

## Decision

- **Alarm wakeups** per app and per alarm tag come from the `Alarm Stats:` section of
  `dumpsys alarm`:
  - `AlarmManagerService.dumpImpl` prints it the same on 16 and 17 (line 3542 on 17, 3413 on 16).
  - `deliverLocked` raises the counters: `numWakeup` for `ELAPSED_REALTIME_WAKEUP` and
    `RTC_WAKEUP` alarms (line 5650 on 17, 5514 on 16).
  - The counters only grow until reboot. The worker therefore reads the dump at screen off, in the
    same call as the sensor snapshot, and again in the screen-on batch.
  - The session's alarm wakeups are the difference between the two reads. When a counter is lower
    at screen on, it restarted (reboot, or the package was reinstalled), and the screen-on value is
    taken.
- **`wakeupap`** is reported as network wakeups per app.
- **Wakelock time** of a held stretch goes to the holder named by `+wake_lock=` or by a later
  unsigned `wake_lock=`. `-wake_lock` ends the stretch, whatever holder it names.
- **The app's own wakeups** are counted as:
  - its wakeup alarms (the alarm-stats difference);
  - its network wakeups. These are always 0 without a network permission, but they are counted
    anyway;
  - its jobs, syncs and wakelock stretches that start while deep idle is IDLE.

  The event log and the history do not stamp times from the same clock reading. So a run that
  starts in the last 2 seconds of an IDLE stretch belongs to the maintenance window that follows.

## Consequences

- SPEC 8.1's waker source and SPEC 8.2's screen-on batch name the alarm dump, marked "(ADR 0019)".
- `:core:parse` gains an `Alarm Stats:` parser, tested on the phone fixture.
- The fixture capture now saves `alarm.txt`. The emulator captures predate this and gain the file at
  the next capture.
- `IDozeWorker` gains `readAlarms`.
- ADR 0018 keeps its other decisions and points here.
