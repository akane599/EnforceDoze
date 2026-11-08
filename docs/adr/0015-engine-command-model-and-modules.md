# 0015. Engine command model: desired-state reconcile in one actor; engine modules

- Status: Accepted
- Date: 2026-11-08
- Phase: P2
- Spec: 4 (intro), 5.3, 5.5, 5.6, 9.3, 12.3, 12.4, 13.3

## Context

SPEC 12.4 asks for one writer: every engine write goes through a single actor (a coroutine
channel), with a 5 s timeout and an action-log row per write. It does not say how the actor models
its work. The levers interact: forced idle (B) and sensor restriction (C) depend on the profile,
the screen, the hold-offs and the tier at once. Constants (A) depend on the profile only, are
written when the profile changes and are read back at each screen off (5.2, 5.6). The engine must
not fight another Doze manager (5.7), must re-read the device after a restart (12.4), and must not
wake the phone while the screen is off (8.2). This is a costly decision (SPEC 0.5).

Facts checked for this ADR (AOSP `android-17.0.0_r1`, `DeviceIdleController`):

- Forced idle survives screen on and charging: `updateInteractivityLocked` and
  `updateChargingLocked` skip `becomeActiveLocked` while `mForceIdle` is set. Only `unforce`
  (`exitForceIdleLocked`) clears it. An outgoing emergency call still calls `becomeActiveLocked`
  (`EmergencyCallListener.onOutgoingEmergencyCall`), whether or not idle is forced.
- So a worker lost while idle is forced leaves the phone in deep idle with the screen on until
  the worker is back or the phone reboots.

The options weighed:

1. imperative handlers per event;
2. desired-state reconcile;
3. an explicit engine state machine;
4. a hybrid.

## Decision

**Desired-state reconcile inside the single actor, with a small mode and one-shot jobs.**

- **Inputs.** Events only update an input model:
  - the active profile (from the plan) and the keys the probe found;
  - screen, charging and the set of active hold-offs;
  - which levers the current tier can write;
  - owner switches ("Re-apply on drift", all sensors off);
  - a mode: `Managed`, `Paused` (log only), `HandsOff` (after "Return to previous setup") or
    `AwaitingOwner(lever)` (the loop guard tripped).
- **Targets.** A pure function maps inputs to one target per lever. The target is `DontCare` or a
  value:
  - A: the exact string, or `Absent`;
  - B: forced or released;
  - C: normal or restricted with its allow package;
  - all-sensors privacy: on or off;
  - D: the allowlist set.
  B, C and privacy get a value only in `Managed` mode with the screen off and no hold-off.
  Otherwise they target released, normal and off. A ignores the screen and the hold-offs, so a
  screen-off event can never write constants.
- **Book.** Per lever, the book holds:
  - what the engine last wrote;
  - what was last observed: `Unknown`, `Exact`, or `Effective` when only `dumpsys` values were
    readable;
  - the times of drift corrections.
  It is persisted. After a restart every observation starts as `Unknown`, and nothing is written
  to a lever until it has been read.
- **Planner.** A pure function from targets and book to writes:
  - A target that differs from the last write is an apply, with the event as its reason.
  - An observation that differs from the last write is drift. Drift is re-applied only with
    "Re-apply on drift" on and the loop guard not tripped.
  - After 2 corrections of one lever within an hour, the mode becomes `AwaitingOwner` for that
    lever: it turns `DontCare` and the app asks the owner. The other levers keep working.
- **Executor.** The executor is the only code that touches the device ports.
  - Each write runs in its own coroutine. The executor waits for it for 5 s, then moves on.
    Binder calls block, so cancelling a coroutine cannot cut a call short. A write that overruns
    leaves the lever `Unknown`, and the read-back decides.
  - Binding the worker keeps its own timeout (4.3) and is not part of the 5 s.
  - Each write produces one action row: lever, command, expected value, read-back, outcome,
    reason.
  - At screen off, the work runs in one worker session in this order:
    1. sensors (C);
    2. forced idle (B);
    3. read-backs;
    4. one constants read for drift.
- **One-shot jobs.** Quick proof (8.6) and the `stock.json` capture are scripted steps run by the
  same actor. Each ends with a full re-read and a reconcile. The quick proof keeps the persisted
  "undo on next start" flag of 8.6.
- **Restore stock and Return to previous setup** are one-shot targets, followed by a mode change:
  - Restore stock: A `Absent`, B released, C normal, privacy off, D as captured. Then `Paused`.
  - Return to previous setup: A is the previous raw string or `Absent`. Then `HandsOff`, where
    every lever is `DontCare`, so the restored Tasker setup is never fought.
- **No timers.** The engine reconciles only on events and owns no alarm, handler or polling
  loop. Work done because of the screen-off broadcast finishes within that broadcast
  (`goAsync()`, bounded to 8 s). Whether the system attributes that window to the app is checked
  by the P3 self-audit.

**Privilege lost (corrects SPEC 4 vs 9.3 and 13.3).** "Lost" means a lever the plan uses can no
longer be written.

- Levers still writable keep the plan's values, as SPEC 4 says. A needs only
  WRITE_SECURE_SETTINGS (ADR 0012). C needs only DUMP (ADR 0004).
- Levers no longer writable show "partly applied" with the reason, and a banner explains it.
- If idle is forced when the worker goes, the banner says so, and the engine releases it the
  moment a worker is back.
- Nothing is "restored to stock" just because the worker went. Restoring A would need the same
  grant that keeps A working, so it is never forced on the owner.

**Modules (amends SPEC 12.3).**

| Module | Kind | Holds |
| --- | --- | --- |
| `:core:profile` | JVM | Profile, plan, the six built-ins (6.1, 6.2), the 6.3 guardrails |
| `:core:doze` | JVM | Inputs, targets, book, planner, executor, the actor, ports (device, constants, hold-offs, clock, store, action log) |
| `:core:doze-android` | Android library | Port adapters over `PrivilegeController`, the constants writer, `LocalReader` and the hold-off sources (audio, calls, car mode, hotspot, charging, foreground app) |

- `DeviceIdleSettings` (the string encoder) moves from `:core:privilege-api` to `:core:parse`. The
  worker keeps only the setting name in `WorkerContract`.
- `:core:data` keeps profile JSON, persistence and the action-log table, and maps to these types.
- `tools/ci/module-graph.sh` gains rules: `:core:profile` and `:core:doze` have no Android or
  privilege dependency, and nothing below `:app` depends on `:core:doze-android`.

## Second opinion

A fresh read-only subagent was given the spec sections, the privilege code and the options
neutrally.

- **Its recommendation** was the same hybrid: desired-state reconcile, a small mode instead of a
  full state machine, imperative scripts only for quick proof and the stock capture.
- **Its strongest argument against:**
  - It is more machinery than four levers seem to need.
  - The spec is written as events ("force at screen off, unforce at screen on"), and a reconciler
    modelled slightly wrong fights a foreign writer.
  - Timing rules hide inside the target function.
  Mitigation adopted: explicit `DontCare` targets, plus unit tests that pin each timing rule
  (zero constants writes on any screen off; restore only when the hold-off set empties).
- **It proposed** the module split above (a JVM engine, a separate Android adapter module, a
  profile module that `:core:recommend` can use without the engine).
- **It flagged:**
  - the blocking-binder timeout problem (adopted);
  - the SPEC 4 vs 13.3 contradiction (resolved above);
  - CPU suspend right after the screen-off broadcast (answered with `goAsync()`);
  - unverified points: whether forced idle persists through screen on and charging (verified
    above), and whether a constants change reschedules a pending Doze step (still open; the
    screen-off drift re-apply may only help the next session).

Kept from my own view:
- the screen-off order puts the time-critical writes (C, then B) before the drift read;
- the loop guard is kept per lever, with A as the case the spec names;
- the privilege-lost rule above releases forced idle first, because it is the one lever that
  cannot be undone without the worker.

## Consequences

- SPEC 4 (intro), 9.3, 12.3, 12.4 and 13.3 are edited and marked "(ADR 0015)".
- Unit tests (13.1) cover:
  - targets as table tests;
  - the planner as table tests;
  - the actor with a fake device, a test clock and an in-memory store: hold-off precedence,
    the loop guard (2 corrections in 59 minutes, a third is not made, one after 61 minutes is),
    zero constants writes on screen off, the 5 s timeout leaving `Unknown`, a restart reading
    before writing, and no pending work after a screen-off batch.
- In code, a tripped guard is a set of levers kept beside the mode (`Managed`, `Paused`,
  `HandsOff`), not a fourth mode, so two guarded levers can coexist; the behaviour is as above.
- Revisit if the loop guard misfires on the phone, or if the target function grows past what a
  table test can show at a glance.
