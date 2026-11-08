# Progress

One short summary per phase gate (SPEC 0.5). Evidence links point into the repository or CI.

## P0 Fork and baseline — gate passed 2026-11-07

Gate: upstream builds; the section 2 audit is confirmed or corrected with real results; CI green.

Done:
- Fork `akane599/EnforceDoze` (it already existed); `origin` / `upstream` remotes; annotated tag
  `upstream-baseline` on `a5b7c4a` (1.10.2, versionCode 86); `main` starts there. The fork's earlier
  `master` work was left untouched (ADR 0002) and later deleted at the owner's request (ADR 0006).
- Upstream built unchanged: `assembleDebug` in 54 s with JDK 17. Its unit test never compiled and
  its lint failed (2 errors, 157 warnings): JUnit added and findings frozen in a baseline
  (`2b3c43c`, ADR 0003).
- Upstream run on the API 37 emulator: installs, cold start 565 ms, no crash
  (`docs/evidence/p0/upstream-main-api37.png`). Without root or Shizuku it applies nothing at screen
  off yet logs "Entering Doze" (SELinux denies `device_config`; bare sensor `restrict` is rejected).
- SPEC 2 audit from source plus API 35, 36 and 37 emulator runs: `docs/upstream-audit.md`, with the
  41-row feature parity table. Spec statements that did not hold are corrected through ADR 0004
  (non-root path, sensor `restrict` needs a package, wakelock-timed delay, LOCATING skipped on API
  36–37, Shizuku 13.1.5 still current, `cmd deviceidle motion` for motion tests).
- Fixtures: raw `deviceidle`, `sensorservice`, events and battery-history dumps with full Doze walks
  for API 35, 36 and 37 (`fixtures/`, `tools/fixtures/capture.sh`).
- CI skeleton on GitHub Actions, SHA-pinned: build, unit tests, lint against the baseline, no
  INTERNET in the merged manifest (ADR 0003).
- `AGENTS.md` / `CLAUDE.md`, `PRODUCT.md`, `DESIGN.md` seed (ADR 0005), `NOTICE`, ADRs 0001–0005,
  `docs/questions.md`.

Gate evidence:
- Upstream builds: local log above; CI `Assemble debug`.
- Audit: `docs/upstream-audit.md` §1–4, ADR 0004.
- CI green: first run 2 min 45 s (https://github.com/akane599/EnforceDoze/actions/runs/42409805787);
  last P0 docs commit green (https://github.com/akane599/EnforceDoze/actions/runs/42410544244).

Carried over to P1:
- App id `io.github.akane599.enforcedoze`, `minSdk 34`, `compileSdk`/`targetSdk` 37, JDK 21
  (SPEC 3.1, 12.2).
- Shizuku on the emulator, the Android 17 authorization question (#2180) and its ADR.
- In-app T1 probes (the `run-as` results in the audit §4.2 are indicative only).

## P1 Privilege — gate passed 2026-11-08

Gate: worker contract tests pass on API 37; tier detection is correct with no privilege, Shizuku
and root.

Done:
- Toolchain moved to Gradle 9.9, AGP 9.5, JDK 21, `compileSdk`/`targetSdk` 37, `minSdk` 34 and the
  fork's app id (ADR 0007). Phone dumps became redacted One UI 9 fixtures (ADR 0008).
- Modules (ADR 0009): `:core:parse` (pure-JVM parsers for `deviceidle`, `sensorservice`,
  allowlists and the event log, tested on API 35–37 and phone fixtures), `:core:privilege-api`
  (`IDozeWorker` AIDL, `CommandOutcome`, `WorkerContract`, the `DeviceIdleSettings` encoder),
  `:core:privilege-worker` (the shell/root worker, the only place for hidden APIs, fixed argv
  only) and `:core:privilege` (tiers T0–T3, Shizuku, Sui and libsu root clients, `WorkerSession`
  with the 5 s bind timeout and 60 s idle unbind, capability probes and the Markdown report).
- Quality gates as CLI tasks: detekt, ktlint, licensee, forbidden calls, module graph (ADR 0010).
- Shizuku on Android 17 (ADR 0011): a fork-agnostic client; the emulator uses the
  `pm grant … API_V23` route; the report records the server's uid and version.
- Lever A redesigned (ADR 0012): `device_config device_idle` is not writable by shell on API 36–37
  and key-by-key writes drop earlier keys, so the app writes the whole global
  `device_idle_constants` string itself with WRITE_SECURE_SETTINGS (lever A now works at T1); the
  worker only reads it (`IDozeWorker` VERSION 2). The profile keys were checked against AOSP 16 and
  17 (ADR 0013). SPEC sections edited and marked.
- Shizuku-API 13.1.5 client bug found on the emulator: a bind made before the previous worker's
  death reaches the main thread joins the dying cached connection and never connects. The client
  now waits for that disconnect (at most 3 s) before binding.
- Capability probes include a lever A test write from the app process, checked in `dumpsys` and
  undone to the exact previous string.

Gate evidence (API 37 emulator, `CE2A.260420.051.A3`, `tools/emulator/p1-privilege.sh`, Shizuku
v13.7.0 thedjchi build):
- none (T0), grants (T1), shell (T2, server uid 2000) and root (T3, Shizuku after `adb root`;
  the emulator cannot give apps root): 14 of 14 instrumented tests each,
  `docs/evidence/p1/instrument-*.txt`.
- Capability reports per scenario: `docs/capabilities/sdk-gphone64-x86-64_api37_*`.
- CI green on the last code commit: https://github.com/akane599/EnforceDoze/actions/runs/42428251588

Carried over to P2:
- The engine `:core:doze` (levers A–D, hold-offs, drift, action log, `stock.json` and
  `previous.json`) and `:core:data`; second opinion on the engine command model first.
- Whether an app can observe `device_idle_constants` with a content observer, and whether
  `dumpsys settings` shows its writer (drift detection without the worker).
- Lever A persistence over 10 minutes and a reboot (probe `constants.persistence`).
- Phone session (asked for first): what One UI's `skip_sensing` does, and whether
  `protect_device_config_flags` is on there.

## P2 Engine — gate passed 2026-11-08

Gate: compressed night verdict Held with Everyday and with Classic; hold-off and drift tests pass;
Restore stock reproduces `stock.json` exactly.

Done:
- Engine model (ADR 0015, with a fresh second opinion): one actor computes a target per lever from
  profile, screen, hold-offs, tier and mode, writes only the difference with one action row and a
  read-back per write, treats drift as a read-back that differs from the last write, and guards
  each lever against write loops. Capture stock, Restore stock and Return to previous are jobs in
  the same actor.
- Modules: `:core:profile` (the six built-ins, plans, guardrails), `:core:doze` (actor, targets,
  planner, executor), `:core:doze-android` (device port over the privilege layer; hold-off sources
  for charging, calls, car mode, audio, hotspot and keep-awake apps; the host), `:core:data` (the
  engine's JSON files; Room `action`, `event` and `session` tables), `:core:evidence` (SPEC 8.4
  verdicts from the records alone).
- The app reads lever A through `dumpsys settings` and observes the global string itself
  (ADR 0014). It hosts the engine in a specialUse foreground service started at boot, after an
  update and from the app. A session recorder judges each screen-off session at screen on from the
  event log the worker reads.
- Emulators have no motion sensor, so AOSP schedules no walk before IDLE there. The P2 script steps
  the walk at the profile's timers (ADR 0016). The same source shows a constants change never
  reschedules a pending step, which closes ADR 0015's open point.
- Upstream's "wait for unlock" is ported as lever B's `untilUnlock`, off by default (ADR 0017).
- Independent engine review (`bab6b98`): releases that time out are retried; engages are saved
  before they are written; the actor survives adapter cancellations and store failures; Restore
  stock edge cases fixed.
- Lever A persistence (SPEC 4.4): the string stayed 10 minutes and survived a reboot, and the
  engine came back from BOOT_COMPLETED without logging drift. The in-app probe entry now points at
  the engine's drift check.
- Hilt (SPEC 12.2) waits for the first feature module in P5; the engine graph is hand-wired while
  the app has one service.

Gate evidence (API 37 emulator, `CE2A.260420.051.A3`, `tools/emulator/p2-engine.sh`):
- 70 of 70 checks, `docs/evidence/p2/summary.txt`:
  - Everyday and Classic nights Held, each with 2 maintenance windows
    (`night-test-*-session.txt`);
  - charging, car mode, audio and call hold-offs lift and restore on the device;
  - drift re-applied twice, the loop guard trips the third time, and clearing the guard re-applies;
  - forced idle is released once a lost worker is back;
  - persistence over 10 minutes and a reboot (`persistence-after-reboot.txt`; this run added the
    `persistence` scenario, which the default run leaves out to stay under SPEC 13's 20 minutes);
  - Restore stock reports `stockDifferences=[]`, and the string and allowlist match the start
    (`restore-stock.txt`).
- Room DAO tests on the emulator: 2 of 2, `docs/evidence/p2/journal-dao-test.txt`.
- CI green on the last code commit: https://github.com/akane599/EnforceDoze/actions/runs/42436781569

Carried over to P3:
- Event-log tag ADR (SPEC 8.1). AOSP 16 and 17 define `device_idle*` tags 34000–34010;
  DeviceIdleController writes `device_idle`, `device_idle_light`, both `_step` tags and the
  `on`/`off` start and complete tags.
- Recorder fixes:
  - use the `inactive_to` latched at the last becomeActive;
  - keep the open session across process death;
  - add a "no motion sensor" note to Did not doze (ADR 0016).
- Collectors still to build: battery and wakers (`batterystats --history`), sensor snapshots, the
  maintenance-window copy job, quick proof and export.
- The parsers' independent review comes before the P3 gate.
- Phone session, asked for first:
  - One UI's `dumpsys settings` format;
  - what `skip_sensing` does;
  - whether `protect_device_config_flags` is on;
  - the hotspot hold-off's initial state.
- A forced-idle delay is not implemented; the lever reports why (SPEC 5.3 alarm).
