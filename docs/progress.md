# Progress

One short summary per phase gate (SPEC 0.5). Evidence links point into the repository or CI.

## P0 Fork and baseline — gate passed 2026-11-07

Gate: upstream builds; the section 2 audit is confirmed or corrected with real results; CI green.

Done:
- Fork `akane599/EnforceDoze` (it already existed); `origin` / `upstream` remotes; annotated tag
  `upstream-baseline` on `a5b7c4a` (1.10.2, versionCode 86); `main` starts there. The fork's earlier
  `master` work is left untouched (ADR 0002, `docs/questions.md` Q1).
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
