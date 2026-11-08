# 0021. Test only on API 37, on an AVD of our own

- Status: Accepted; the AOSP 16 check and the Android 16 line superseded by 0022
- Date: 2026-11-08
- Phase: P3
- Spec: 0.4 (environment), 3.1 (test targets), 12.4 (parser fixtures), 13.1 (worker contract),
  14 (P3 gate), 15 (acceptance checklist)

## Context

The spec asks for tests below API 37 in several places:

- 12.4: parser fixtures from the API 34, 35, 36 and 37 emulators;
- 13.1: the worker contract on API 37, with 35 and 36 optional;
- 14: the P3 gate, "parser fixtures for API 35–37 pass";
- 3.1 and 0.4: API 36 and API 35 AVDs as test targets.

Those AVDs (`pixel-api35`, `pixel-api36`, `pixel-api37`) belong to the owner's other projects as
well.

The owner decided on 2026-11-08:

- only API 37 is tested on the emulator;
- the API 37 emulator dumps and the owner's own phone dumps are enough for the parser fixtures;
- `minSdk` stays 34, but nothing below 37 has to be tested;
- this project uses an AVD of its own and leaves the other projects' emulators alone.

## Decision

- **Test level.** Every test that needs a device runs on API 37 only:
  - the emulator, on the AVD `ed-api37`, created for this project from the same Google APIs x86_64
    API 37 system image;
  - the owner's phone (One UI 9, API 37), only when the owner agrees (SPEC 0.4).
- **Other AVDs.** The `pixel-api*` AVDs belong to other projects. This project never starts,
  changes or wipes them.
- **`minSdk`.** It stays 34. Code below 37 is kept correct by:
  - reading AOSP source, still for both `android-16.0.0_r1` and `android-17.0.0_r1` (rule 3);
  - capability probes on the device (SPEC 4.4).

  It is not covered by tests. No gate and no acceptance item may require a run below API 37.
- **Parser fixtures.** Two sources are required:
  - the API 37 emulator captures;
  - the owner's phone captures, redacted (ADR 0008).

  The API 35 and 36 emulator captures already in `fixtures/` stay as extra cases while they pass:
  they are the only samples of the legacy battery-history layout, which `minSdk` 34 devices print.
  A parser change for API 37 that only they reject may drop them, with a note in the commit, instead
  of bending the parser to fit them. No new captures are made below 37.
- **SPEC text superseded by this ADR**, each place marked "(ADR 0021)":
  - 0.4: akane-linux runs the project's own AVD.
  - 3.1: the test targets are the API 37 AVD and the phone.
  - 12.4: fixtures come from the API 37 emulator and the owner's One UI dumps.
  - 13.1: the worker contract runs on API 37 only.
  - 14: the P3 gate reads "parser fixtures for the API 37 emulator and the owner's phone pass".
  - 15: every item is checked on API 37: the emulator, or the phone where the item says so.

## Consequences

- `AGENTS.md` names `ed-api37` as the only emulator and its start command.
- The emulator scripts (`tools/emulator/p1-privilege.sh`, `p2-engine.sh`, `p3-evidence.sh`)
  default to `ed-api37`.
- `p3-evidence.sh` keeps its `alarm-fixture` scenario for recapturing `alarm.txt` on API 37, but no
  longer runs it by default.
- Evidence from earlier phases was taken on `pixel-api37`, which runs the same system image. The
  P3 gate is taken again on `ed-api37`.
- SPEC 1.3 still names Android 16 (API 36) among the non-negotiables. A non-negotiable changes only
  with the owner (SPEC 0.5), so this ADR leaves it alone. Android 16 rests on the AOSP 16 source
  checks and the probes, not on tests. `docs/questions.md` Q6 asks whether that line should go.
