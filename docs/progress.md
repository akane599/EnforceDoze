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
