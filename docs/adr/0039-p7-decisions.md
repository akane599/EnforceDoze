# 0039. P7 decisions

- Status: Accepted
- Date: 2026-11-16
- Phase: P7
- Spec: 12.6, 13, 15

## Context

ADR 0023: choices that are cheap to reverse are made by the agent, each with a one-line reason here.
P7 asks for:
- the whole emulator suite in under 20 minutes (SPEC 13);
- the checklist with evidence (SPEC 15);
- the docs;
- a signed 1.0.0.

## Decision

- **The emulator suite boots `ed-api37` once** (`tools/emulator/suite.sh`). One emulator boot per
  phase cost about 30 s each. The phases' own scripts did about 50 minutes of work in total, and much
  of it twice.
  - Each phase's script runs against the suite's emulator, named in `SUITE_SERIAL`, and leaves it
    running for the next phase. The suite shuts it down at the end, also on failure.
  - The overlapping parts run once:
    - P3 runs one compressed night, Test Classic. It covers levers A, B and C, the report and the
      verdict.
    - P2 runs its hold-offs, drift, privilege and Restore stock without its two nights.
    - P2's 10-minute persistence wait stays out, as it already was by default.
  - P4's waits for the window to open or close are shorter: 25, 35 and 45 s instead of 90, 150 and
    150 s. They were sized for the slowest step. The steps before each window opens or closes take
    1, 12 and 15 s.
  - P3 compares each night with the nights run before it, so one night or two both check.
- **Every emulator script follows the owner's akane-linux rules through `tools/emulator/lib.sh`.**
  - It checks the load first and waits (exit 75) while a render, build or other heavy process runs.
  - It never uses an emulator that is already running.
  - It shuts down only an emulator it started.
  - P1–P4 predated these rules and reused a running `ed-api37`. Now they do not.
- **The release asset is always named `EnforceDoze.apk`** (SPEC 12.6: a stable name, so Obtainium
  can track it). The version is in the release tag and the APK's `versionName`.
- **`versionCode` is `major × 10000 + minor × 100 + patch`**, so 1.0.0 is 10000. Every release then
  gets a higher code, with room for patches. The fork's app id is new, so no upstream code competes.
- **Release signing runs `apksigner` after an unsigned Gradle build** (`tools/release/build-release.sh`),
  not a Gradle `signingConfig`. That is how ADR 0006's "wire the release signing" is done. `apksigner`
  reads the password from its file, so the secret never enters Gradle properties, the configuration
  cache or a log.
  - The script checks the signer's SHA-256 against the digest in `docs/release.md`.
  - Coulomb has no release build yet to compare with. The key is Coulomb's keystore, and Coulomb's
    releases must carry the same digest.
- **The engine notification shows at once** (`Notification.FOREGROUND_SERVICE_IMMEDIATE`).
  - By default the system holds back a new foreground service's notification for up to 10 s.
  - It then posts it on the app's behalf, with a wakelock booked to the app.
  - The suite's P3 went from setup to screen off within those 10 s. The held-back post landed in deep
    idle and counted as one of the app's own wakeups.
  - Showing the notification at once removes that post. After a reboot, the screen can go off within
    10 s of the engine starting, so this case also happens on a phone.
- **`docs/acceptance.md` holds the checklist**, one box per SPEC 15 item, with its evidence beside it.
  A box stays open until its evidence exists.
- **`docs/parity.md` is for people coming from upstream:** what happened to each feature, in plain
  words. The audit table with its tests stays in `docs/upstream-audit.md` §6.
- **Upstream's repository files are removed:** `fastlane/`, the Gemfile, the Weblate config,
  `docs/index.html`, the release workflow and a stray image (parity row 41).
  - `.github/FUNDING.yml` stays. It points at upstream's author, which credits them and costs the
    fork nothing.
- **No backup and no device-to-device transfer.** `allowBackup` is false, and the data extraction rules
  exclude every domain. Upstream allowed backup.
  - `stock.json` and `previous.json` describe one phone. Restored on another phone, Restore stock would
    write the first phone's values there (SPEC 0.2 rule 1).
  - The automation tokens stay with the phone they were made on.
  - A new phone starts as a fresh install: the engine captures its own stock at first use.
- **Jetifier stays on for now.** Nothing needs it any more. Turning it off re-transforms every
  dependency, and the build server's shared Gradle volume does not have the space today (ADR 0037
  outcome).

## Consequences

- `tools/emulator/suite.sh` is the command for the full suite. A single phase can still run on its
  own with its own script.
- Each phase's evidence folder holds only its latest run, and the suite writes
  `docs/evidence/suite/summary.txt` with the time of each phase.
