# 0014. The app reads the global string through `dumpsys settings` and observes it

- Status: Accepted
- Date: 2026-11-08
- Phase: P2
- Spec: 4.1, 5.6, 11.4

## Context

ADR 0012 moved lever A to the global `device_idle_constants` string and left two questions for P2:
whether the app can observe the setting, and whether anything but the worker can read it
(`Settings.Global.getString` refuses it, because the setting is `@hide` without `@Readable`).

Checked on the API 37 emulator (`CE2A.260420.051.A3`), with the test app targeting API 37
(`GlobalConstantsVisibilityTest`, `docs/evidence/p2/visibility-*.txt`), and by hand over ADB:

1. **Observing works without any grant.** A `ContentObserver` registered on
   `Settings.Global.getUriFor("device_idle_constants")` gets `onChange` within 5 s of
   `settings put global device_idle_constants …` from the shell uid, in the scenario with no
   grants at all and in the one with ADB grants.
2. **`dumpsys settings` shows the raw string and its writer.** The global table has one row per
   setting: `_id:597 name:device_idle_constants pkg:com.android.shell value:inactive_to=1800000
   notPreservedInRestore`. `pkg` is the package SettingsProvider recorded for the last write
   (ADR 0012). The provider's operation history lists `update` and `delete` rows with timestamps.
   The app runs `dumpsys settings` in its own process with the `DUMP` grant (tier T1) and finds the
   row with the exact value and `pkg:com.android.shell`. Without `DUMP` the test is skipped.
3. **The dump is large.** The full output runs to several hundred KB, past the default 64 KB output
   cap of `ProcessCommandRunner`, so a reader needs a larger cap and should pick the one row
   instead of keeping the text.

## Decision

- **Reading.** At T1, the app reads the raw string and its writer from the `device_idle_constants`
  row of `dumpsys settings`. The worker's `settings get global device_idle_constants` stays the
  reader at T2 and T3; it gives the value but not the writer, so drift attribution also uses the
  dump where `DUMP` is held.
- **Writer attribution for drift (5.6, 5.7).** The `pkg` of the row names the last writer: this
  app's own package (our write), `com.android.shell` (ADB, or Tasker or another tool through
  Shizuku), or another package. Drift rows log that package as a fact, not a guess. Server flag
  sync on API 36–37 normally goes through `device_config`; should a package such as Google Play
  services write this string instead, the row names it like any other writer.
- **Observing.** While the screen is on, the engine may register a content observer on the setting
  to see a foreign write sooner than the next screen off. It does nothing while the screen is off
  (SPEC 8.2), and the screen-off read stays the drift check.
- **Probe.** The lever A write probe (4.4) can run at T1 by reading the string back from
  `dumpsys settings` instead of through the worker.

## Consequences

- SPEC 4.1 (T1 row), 5.6 (drift attribution) and 11.4 (detect) are edited and marked "(ADR 0014)".
- The `dumpsys settings` row parser goes into `:core:parse` with emulator and phone fixtures. The
  phone row is checked in a planned phone session before relying on it there, because One UI may
  format the dump differently.
- `previous.json` can hold the raw string at T1 as well, so "Return to previous setup" works
  without the worker.
