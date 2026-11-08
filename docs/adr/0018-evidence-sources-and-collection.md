# 0018. Evidence sources and how they are collected

- Status: Accepted
- Date: 2026-11-08
- Phase: P3
- Spec: 8.1, 8.2, 8.3, 15 (the app's own wakeups)

## Context

SPEC 8.1 asks for the exact event-log tags in an ADR and names `batterystats` as the source of wakers
and the fallback for Doze states. SPEC 8.2 allows no polling while the screen is off and asks for one
worker call at screen on. This ADR records what each source gives on API 35–37 and how the app reads
it without waking the phone.

## Decision

### Doze transitions: the event log

- AOSP `services/core/java/com/android/server/EventLogTags.logtags` defines tags 34000–34010 for
  `device_idle*`, the same at `android-16.0.0_r1` and `android-17.0.0_r1`.
- DeviceIdleController (17) writes the following:
  - `device_idle (state, reason)` and `device_idle_light (state, reason)` at every state change
    (lines 4139 and 4127);
  - `device_idle_step` and `device_idle_light_step` when a step starts;
  - `device_idle_on_start`/`_on_complete` and `device_idle_off_start`/`_off_complete` around the
    idle-mode broadcasts (lines 1995–2073).
- Transitions come from `device_idle` and `device_idle_light` only. The other tags repeat the same
  changes, so they stay in the raw dump.
- The worker reads `logcat -b events -d -v epoch -T <session start − 1 min>`.
- Event-log coverage starts at the first line the read returns, of any tag. If that is more than
  1 minute after the session start, the buffer rolled over. The missing stretch comes from the copy
  job (below), then from the battery history; whatever is still missing is a labelled gap.

### Fallback and wakers: the battery history

- `dumpsys batterystats --history-start <cursor>` prints only records whose history time is at or
  after the cursor and ends with `NEXT: <last + 1>` (`BatteryStats.dumpHistory`, lines 7512 and
  7584–7587 on 17; the same on 16).
- The cursor is in the history's own monotonic clock. So the app keeps the last `NEXT` and never
  converts wall time into it. The first read uses 0. When a read is cut off, the cursor is not
  advanced.
- **API 36+ (Format 2):** each line starts with wall time in the device time zone
  (`MM-dd HH:mm:ss.SSS`, `HistoryLogTimeFormatter`).
- **API ≤ 35 (legacy):** lines start with an offset from the first record.
- Both formats print `RESET:TIME:` and `TIME: yyyy-MM-dd-HH-mm-ss[-SSS]` lines. Those give the year
  for Format 2 and the anchor for legacy offsets. A cursor read starts with such a line.
- `device_idle=full|light|off` (`noteDeviceIdleMode`) is the fallback for Doze states. A maintenance
  window shows as `off` between `full` stretches.
- Wakers per app, cut to the session:
  - wakeup alarms (`wakeupap=<uid>`);
  - jobs and syncs (`±job`, `±sync`: count and time);
  - long wakelocks (`±longwake`);
  - wakelock time of the first holder (`+wake_lock=<uid>:"tag"` to `-wake_lock`). Without
    `full-history` the history names only the first holder, and the report says so.
- Device-wide figures:
  - kernel wakeup reasons (`wake_reason`, counted);
  - mobile radio time (`±mobile_radio`).

  The history keeps no per-app radio time, so radio is a device total, not per app as SPEC 8.1
  says.

### Battery at both ends

The foreground service reads `BatteryManager` in the screen broadcasts: the level, and the charge
counter in µAh when the device reports one. Drain %/h comes from the levels, and mAh from the
counter.

### The app's own wakeups (SPEC 15)

- A dozing phone is woken by an app through a wakeup alarm. Jobs and syncs run only in maintenance
  windows.
- The app's own wakeups are therefore counted as:
  - wakeup alarms attributed to its uid;
  - jobs, syncs and first-holder wakelocks of its uid that start while deep idle is IDLE rather
    than in a maintenance window.
- The copy job's runs in maintenance windows are listed separately, not counted.
- Shell work the app asks of the worker happens at screen off, at screen on and in the copy job,
  never on its own timer.

### Collecting

- **Screen off:**
  - battery;
  - a sensor snapshot (`dumpsys sensorservice` through the worker);
  - the open session, written to a small file so it survives the process being killed.
- **Screen on:** one batch through the worker: the event log, the battery history from the cursor,
  `sensorservice` and `deviceidle`. Its duration is logged (target under 2 s, SPEC 8.2). The raw
  dumps are kept gzip-compressed for 7 days.
- **Copy job:** a one-shot JobScheduler job with only a minimum latency of 15 minutes and no
  deadline. It is scheduled at screen off, copies new `device_idle*` lines, schedules the next one
  and is cancelled at screen on.
  - The latency uses a non-wakeup alarm (`TimeController`, `ELAPSED_REALTIME`, line 378 on 17).
  - A deadline would add a wakeup alarm (line 393). So the job never has one, and it never wakes the
    phone.
  - When the job is ready during deep idle, it runs in the next maintenance window.

## Consequences

- SPEC 8.1's waker row gains "(mobile radio as a device total, ADR 0018)".
- `:core:parse` gets a battery history parser for both formats, tested on the API 35, 36 and 37
  emulator fixtures and the One UI phone fixture.
- `IDozeWorker.readBatteryHistory` always passes a cursor (0 for the first read).
- Room gains `transition`, `waker` and `sensor_snapshot` tables. The session row gains battery,
  drain, the app's own wakeups, the collection time and the coverage of each source. The schema
  still changes in place before 1.0.0.
- Revisit if a later API stops printing `NEXT:`, or if One UI's history lacks `wakeupap`.
