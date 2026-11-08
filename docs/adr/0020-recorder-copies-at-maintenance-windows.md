# 0020. The recorder copies at maintenance windows and judges only what it saw

- Status: Accepted
- Date: 2026-11-08
- Phase: P3
- Spec: 8.2 (the copy job, the screen-on batch), 8.4 (which sessions get a verdict); amends ADR 0018

## Context

1. **Jobs can run in Doze.** SPEC 8.2 and ADR 0018 copy the event log with a JobScheduler job,
   because jobs supposedly run only in maintenance windows. That premise is wrong:
   - `DeviceIdleJobsController.updateTaskStateLocked` lets the jobs of apps on the user power-save
     allowlist run while the device dozes (line 225 on `android-17.0.0_r1`, 228 on
     `android-16.0.0_r1`).
   - Upstream EnforceDoze asks the owner to put it on that list (`ForceDozeService`), so an upgraded
     install is likely on it.
   - Its copy job would then run inside deep IDLE as soon as any other wakeup made the job's latency
     alarm due. The self-audit would rightly count it.
2. **Every deep maintenance window is announced.**
   - When IDLE moves to IDLE_MAINTENANCE, `stepIdleStateLocked` posts `MSG_REPORT_IDLE_OFF` (line
     4115 on 17, 4023 on 16).
   - The handler broadcasts `PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED` when deep idle changed
     (lines 2023–2038 on 17, from 1997 on 16).
   - The intent goes to registered receivers only, in the foreground queue. Its options defer delivery
     to cached processes until they are active (line 2789 on 17, 2751 on 16). The engine runs a
     foreground service, so its process is not cached.
   - Once the receivers have the broadcast, the window lasts at least `min_deep_maintenance_time`
     longer (lines 767–776 on 17). That is 30 s in every fixture.
3. **The history cursor can lag a whole day.** The battery history is read from the last `NEXT:`
   cursor. Only sessions long enough for a verdict need a screen-on read, so after a day of short
   sessions the read would cover the whole day. The phone prints about 300 KB of history per hour
   (fixture: 3.3 MB over 10.5 h), and the 2 s target of SPEC 8.2 would not hold.
4. **Gaps still feed the verdict.** A stretch that no source covered (ADR 0018) is not drawn on the
   timeline, but the verdict code carries the last state it saw through it.
5. **Open sessions after a restart.** The open session survives process death (ADR 0018). If the
   process comes back after the screen came on and went off again, the stored start is wrong.

## Decision

- **Copy at deep maintenance windows, without a job.**
  - While the engine runs, the recorder listens for `ACTION_DEVICE_IDLE_MODE_CHANGED`.
  - When deep idle ends with the screen off and a session open, it copies the event-log lines since
    its last copy: the first line, for coverage, and the `device_idle*` lines. It also takes a sensor
    snapshot, labelled `COPY`.
  - It does this inside `goAsync`, with an 8 s limit, under the 10 s that a foreground broadcast
    allows.
  - Light-idle maintenance windows are not used. A night that never reaches deep idle falls back to
    the battery history.
- **Screen off advances the cursor.**
  - The screen-off worker call also reads the history from the cursor. It keeps only the `NEXT:`,
    so the screen-on read covers the session alone.
  - Sessions no longer than the minimum (10 minutes, SPEC 8.4) read nothing at screen on. Their
    screen-off dumps are deleted.
- **What was not seen is not judged.**
  - A session gets no verdict when no source covered more than 1 minute of it, or when the walk to
    deep idle is unknown.
  - Its numbers are still stored, and the note says what was missing.
- **Resume after a restart.**
  - At start, if the screen is on, the open session's end was not seen, so the session is dropped.
  - If the screen is off, the session is resumed. At screen on, the battery history must show no
    `+screen` inside the session. Without that history, or with a `+screen` inside, the session is
    stored without a verdict.

## Consequences

- SPEC 8.2's third bullet names the idle-mode broadcast in place of the job, and SPEC 8.4 says that a
  verdict needs a complete Doze record. Both are marked "(ADR 0020)".
- ADR 0018 keeps its sources. Its copy job and its screen-off list are replaced by this ADR.
- No `JobService` and no `BIND_JOB_SERVICE` entry in the manifest.
- Revisit if a later API stops sending the idle-mode broadcast at maintenance windows.
