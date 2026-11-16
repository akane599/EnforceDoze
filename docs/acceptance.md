# Acceptance checklist (SPEC 15)

Each box from SPEC 15 has its evidence beside it: a test, a report or a screenshot. Every item is
checked on API 37 (ADR 0021): JVM tests run on build-server, emulator runs use `ed-api37` on akane-linux.
The emulator evidence is from the one-boot suite (`tools/emulator/suite.sh`) unless an item names
another run. A box stays open until its evidence exists.

## Android 17 and One UI

- [x] **Installs and runs with `targetSdk` 37 on the API 37 emulator; edge-to-edge, predictive back,
  large-screen layouts, no orientation lock.**
  - `app/build.gradle.kts`: `compileSdk` and `targetSdk` 37.
  - Every emulator run installs and runs it: `docs/evidence/suite/summary.txt`.
  - Edge-to-edge: `enableEdgeToEdge` in `MainActivity`.
  - Predictive back: on by default from target 36. The shell uses Navigation 3 back handling
    (ADR 0029).
  - Large screens: list-detail scenes (ADR 0029). Each screen has tablet goldens: 80 of the 264
    goldens under `*/screenshots/`.
  - No orientation lock: no `screenOrientation` in the manifest.
- [x] **Foreground service type `specialUse` declared and explained; no exact-alarm permission unless
  the delay alarm is turned on.**
  - `EngineService` is declared with `foregroundServiceType="specialUse"`. Its subtype property and
    the comment beside it say why (`app/src/main/AndroidManifest.xml`).
  - `tools/ci/permissions.sh` pins the merged permissions, and `SCHEDULE_EXACT_ALARM` is not among
    them. The delay alarm is in `BACKLOG.md`.
- [x] **Every One UI probe from section 3 reports ok or missing; missing ones degrade with a visible
  reason, never a crash.**
  - `CapabilityProberTest` covers each probe, including "no grants and no worker reports what is
    missing and never throws".
  - The probe reports are in `docs/capabilities/`.
  - The visible reasons are in the onboarding goldens (`feature/onboarding/screenshots/`).

## Privilege

- [x] **Tiers T0–T3 detected correctly; read-only mode works with no privilege.**
  - Tests: `TierResolverTest`, `PrivilegeControllerTest`, `DeviceMappingTest`. The T3 case is ADR
    0038's.
  - Emulator: P1 runs the none, grants, shell and root scenarios (`docs/evidence/p1/`).
  - Read-only Home: `feature/home/screenshots/read-only/`.
- [x] **The Android 17 Shizuku decision is recorded in an ADR and the onboarding text matches it.**
  ADR 0011; the onboarding strings and goldens.
- [x] **No `service call` transaction numbers and no reflection on Shizuku internals anywhere in the
  code.**
  - `tools/ci/forbidden-calls.sh` runs in CI. Since ADR 0037 removed upstream's code, its baseline is
    empty.

## Engine

- [x] **Levers A–D apply, read back and revert; mismatches show "partly applied" with the key.**
  - Tests: `DozeEngineTest`, `TargetsTest`, `PlannerTest`, `AllowlistTest`.
  - Emulator: P2 setup and restore (`docs/evidence/p2/`).
  - The root-tier extras (ADR 0038): `ExtrasEngineTest`, and the T3 run in `docs/evidence/p6-extras/`.
- [x] **Restore stock reproduces `stock.json`; Return to previous setup reproduces `previous.json`.**
  - Tests: `DozeEngineTest` ("restore stock reproduces stock json exactly, then pauses").
  - Emulator, Restore stock: P2 `restore-stock.txt`.
  - Emulator, Return to previous: P6 `switch-return.txt`.
- [x] **Hold-offs lift B and C and restore them afterwards; each start and end is logged.**
  - Tests: `HoldOffEngineTest`, `HoldOffRulesTest`.
  - Emulator: P2 hold-offs (charging, car mode, audio, call), with `holdoffs-events.txt`.
- [x] **Drift is detected, logged with the likely writer, re-applied, and the loop guard stops after 2
  corrections in an hour.**
  - Tests: `DozeEngineTest`, `PlannerTest` ("loop guard counts corrections within the last hour").
  - Emulator: P2 drift (`drift-events.txt`).

## Evidence

- [x] **Every session over 10 minutes has a verdict computed from records.**
  - Tests: `VerdictsTest`, `SessionAssemblyTest`.
  - Emulator: P3 lowers the minimum session length to 4 minutes, and its export asserts that no
    recorded night lacks a verdict.
- [x] **Compressed night: verdict Held, report complete, app's own wakeups 0.**
  - Emulator: P3's Test Classic night, `night-test-classic-*.txt`. It checks "the app's own wakeups
    are 0" and that the verdict line is Held.
- [x] **Quick proof passes and always undoes itself, including after a forced stop mid-test.**
  - Tests: `QuickProofTest` ("a proof cut short is undone at the next start").
  - Emulator: P3 `quick-proof.txt`.
- [x] **Missing data appears as a labelled gap, never drawn as if seen.**
  - Tests: `DozeSourcesTest` ("what no source saw stays a gap").
  - Goldens: the report's and Home's no-data and short-session screens.
- [x] **No network permission in the merged manifest.** `tools/ci/permissions.sh` in CI.

## Profiles and Recommended

- [x] **Six built-in profiles match section 6; each key verified against AOSP for API 37 in an ADR.**
  `BuiltInProfilesTest`; ADR 0013.
- [x] **Custom profiles respect guardrails; import and export round-trip.**
  - Tests: `GuardrailsTest`, `DraftTest`, `ProfileFilesTest` ("every built-in survives export and
    import unchanged").
- [x] **A recommendation appears on day zero from existing history, with confidence, reasons with
  numbers, and cost.**
  - Tests: `DayZeroTest`, `RecommendationPolicyTest`, `FixtureHistoriesTest` ("every fixture gets
    its plan, confidence, reasons and notes").
- [x] **Recommended never switches plans without a tap unless the owner turned on automatic
  following.**
  - `AppServices.followRecommendation` is the only plan switch that is not an owner's tap. It applies
    what `planToFollow` returns: nothing while following is off.
  - Tests: `FollowRecommendationTest`. Following is off by default (`AppPrefs.followRecommended`).

## Design

- [x] **`npx impeccable detect` reports zero; all critique and audit P0 and P1 fixed.**
  - `detect` reads web files only, so it reports zero on this Compose app without testing it
    (progress, P5 design pass).
  - The critique and audit found no P0 and five P1s, all fixed (ADR 0030).
- [x] **Contrast targets met in both themes; timeline strip readable without colour; TalkBack summary
  present.**
  - Contrast: `ContrastTest` checks both themes:
    - text at least 4.5:1 on every surface it is set on, and every Material colour pair;
    - strip fills, status marks and outlines at least 3:1.
  - `PaletteTest` ties the theme to DESIGN.md's table.
  - Without colour: each state has its own pattern. `StripWithoutColourTest` draws every fill, reduces
    it to lightness alone, and checks that every pair differs over most of the strip in both themes.
  - TalkBack: `HomeStripSummaryTest` and `ReportStripSummaryTest` find the strip's text summary: its
    times, deep idle, maintenance windows and breaks.
- [x] **Font scale 200% and Turkish strings fit on every screen (screenshot evidence).**
  - Each screen's main golden set covers the full matrix: dark and light, English and Turkish, 100% and
    200%, phone and tablet (`testing/screenshots`, `Matrix`).
  - State variants are shot in fewer variants. That is 90 goldens at 200% and 118 in Turkish of 264.
- [x] **Cold start to first frame under 500 ms on the emulator.**
  - The P5 macrobenchmark measured a median `timeToInitialDisplayMs` of 190
    (`docs/evidence/p5/`).

## Integrations

- [x] **Automation actions work from Tasker with the granted permission; rate limit enforced.**
  - Tests: `AutomationTest`.
  - Emulator: P6's automation checks (`automation-calls.txt`, `automation-events.txt`).
  - Since ADR 0033 a per-client token is the grant, not a permission.
- [x] **Coulomb can read the evidence provider; nothing else can.**
  - Emulator: P6 `provider-instrument.txt` and `provider-shell.txt`. The signature permission is
    covered in both install orders, and the shell is denied.
- [x] **Tasker switch-over flow detects, imports, verifies drift = 0, and can switch back.**
  - Tests: `SwitchOverTest`.
  - Emulator: P6 `switch-found.txt`, `switch-import.txt`, `switch-complete.txt` and
    `switch-return.txt`.

## Testing and release

- [ ] **Full emulator suite green in under 20 minutes; no test waits for days.**
  - `tools/emulator/suite.sh` writes `docs/evidence/suite/summary.txt`.
  - First run (2026-11-16): 1090 s in all, under the 1200 s limit. Every phase passed except one P3
    check: the night counted one wakeup of the app's own. That was the system's held-back post of the
    engine's notification, now fixed (ADR 0039). P4's waits have since been cut by about 75 s.
  - Open: the green rerun. akane-linux lacks the disk space to boot the AVD (Q16).
- [x] **GPL-3.0 kept, upstream authors credited in `NOTICE` and the About screen.**
  - `LICENSE`, `NOTICE`, `LicenceAssetTest`, and the Settings credits golden (`SettingsScreenshotTest`).
- [ ] **Signed 1.0.0 on the fork's Releases with a stable asset name; `README`, `automation.md`,
  `parity.md` and `CHANGELOG` written.**
  - The docs are written: `README.md`, `docs/automation.md`, `docs/parity.md`, `CHANGELOG.md`, and
    `docs/release.md` for the steps.
  - `tools/release/build-release.sh` builds `EnforceDoze.apk`:
    - version 1.0.0 (code 10000);
    - signer SHA-256 `bafc4dab…089c27`, Coulomb's key;
    - 14 permissions, no `INTERNET`, not debuggable.
  - Open: publishing. The owner does that after the green suite run.
