# 0037. Removing the upstream code, row by row

- Status: Accepted
- Date: 2026-11-16
- Phase: P6
- Spec: 1.2, 2.3, 3.3, 12.1

## Context

Since ADR 0036 no upstream component is registered, so about 8,000 lines of upstream Java are dead
weight. They still carry `service call` and reflection on Shizuku internals, which the SPEC 15
checklist forbids "anywhere". They also bring a dynamic dependency version (libsuperuser `1.1.0.+`).
SPEC 12.1 allows a class to be deleted only when every parity row it served has new code with a test.
Deleting is hard to reverse, so a fresh read-only reviewer weighed the options with no preferred
answer (ADR 0023):
- A: delete everything at once;
- B: delete row by row, in clusters;
- C: wait for P7;
- D: keep the code through 1.0.

## The second opinion

The reviewer checked every row against the code and recommended B.
- **Covered today, with a test:** rows 1, 2, 4–7, 9, 10, 20, 21, 23, 26, 28–31, 34, 37 and 38.
- **Covered, but the test is weak or missing:**
  - 22 (keep awake) and 29's app reset have only screenshot tests;
  - 32 (tiles) is tested only by the emulator run;
  - 25 (the foreground-service notification) and 36 (start at boot) have no test.
- **Not covered at all.** Upstream holds the only implementation:
  - **Rows 11–14**, the root-tier features SPEC 1.2 keeps: Wi-Fi and mobile data off in Doze,
    biometrics off, app suspension. The new code only detects T3.
  - **Row 35**, the onboarding step that exempts the app from battery optimisation and adds it to One
    UI's "Never sleeping apps" (SPEC 3.3).
- **The dependency chain:** `ForceDozeService`, `SettingsActivity`, `BlockAppsActivity` and
  `RequestIgnoreBatteryActivity` hold most of the code through `Utils`. The leaf classes can go now.
- **Arguments against the other options:**
  - A breaks SPEC 12.1 for rows 11–14 and 35, and one commit mixing code, theme, resources and
    dependencies is hard to bisect.
  - C gains nothing for the covered rows and moves theme and resource surprises into the release.
  - D cannot tick the SPEC 15 box, and it ships a dynamic version.

I agreed, including the finding that rows 11–14 and 35 are open spec work. My own first reading had
counted them as covered by "T3" and onboarding.

## Decision

1. **Now:** the leaf classes go, each named with its rows in the parity table. Their layouts go with
   them.
   - Classes: `AboutAppActivity`, `Add/RemoveWhiteListReceiver`, `AirplaneTileService`,
     `AutoRestartOnUpdate`, `BootCompleteReceiver`, `BlockNotificationsActivity`,
     `DisableForceDozeService`, `LogActivity`, `ReenterDoze`, `SettingsChangeReceiver`,
     `TaskerBroadcasts*` and `WhitelistAppsActivity`.
   - Also the template `ExampleUnitTest`.
   - Row 36 gets its test first (`EngineStartTest`). `NumberPickerPreference` and
     `EnableForceDozeService` stay: upstream's settings and `Utils` still name them.
2. **Next:** port row 35 and rows 11–14, and strengthen the tests for 22, 25 and 32. Done for 35, 22, 25
   and 32 on 2026-11-16: the tile and keep-awake decisions are pure functions with JVM tests, and `:app`
   runs Robolectric for the notifications (ADR 0028's JVM arguments).
   - The root-tier features get their own ADR before any code. Each one collides with device safety:
     - Wi-Fi off cuts Wi-Fi calling;
     - mobile data off delays messages, and can affect calls where there is no VoLTE;
     - a suspended clock app would miss the owner's alarm.
   - Every command must be checked against AOSP `android-17.0.0_r1` (rule 3); `service call` stays
     forbidden.
   - Upstream's code stays reachable at the `upstream-baseline` tag.
3. **Then:** the remaining upstream classes go, with the parity table updated in the same commit.
4. **Separately**, each in its own commit:
   - the theme moves off the upstream Material and preference libraries;
   - the resources still in use move out of upstream's files (`app_name`, launcher and tile icons,
     `export_paths`);
   - the upstream dependencies and their licensee entries go;
   - `MyApplication` moves to Kotlin;
   - the permissions are audited;
   - the forbidden-call and lint baselines shrink to what is left. A baseline diff may only lose lines.
5. All of this lands before the P7 gate. `NOTICE` and the About credits are untouched throughout.
