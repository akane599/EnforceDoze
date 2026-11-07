# 0012. Lever A writes the global `device_idle_constants` string, not `device_config`

- Status: Accepted
- Date: 2026-11-08
- Phase: P2 (found while finishing P1)
- Spec: 1.4, 3, 4.1, 4.3, 4.4, 5.2, 5.6, 6.2, 6.5, 11.4, 13.3, 16.1

## Context

SPEC 5.2 writes Doze constants with `device_config put device_idle <key> <value>` through the
worker and resets them with `device_config reset trusted_defaults device_idle`. Checked against AOSP
`android-16.0.0_r1` and `android-17.0.0_r1` and on the emulators, that path does not work for the
shell worker, and it misbehaves even where it is allowed.

1. **Shell may not write `device_idle` in `device_config`.** `packages/Shell/AndroidManifest.xml`
   (both tags) requests `WRITE_DEVICE_CONFIG` only with
   `android:featureFlag="!android.security.protect_device_config_flags"` ("limit adb shell to
   allowlisted flags", `core/java/android/security/flags.aconfig`, fixed read-only); shell keeps
   `WRITE_ALLOWLISTED_DEVICE_CONFIG`. The allowlist (`WritableFlags.java` in
   `packages/modules/ConfigInfrastructure`) holds three `device_idle` keys: `light_idle_to`,
   `light_idle_to_initial_flex` and `notification_allowlist_duration_ms`. On the API 36
   (`BE2A.250530.026.F3`) and API 37 (`CE2A.260420.051.A3`) emulators shell has no
   `WRITE_DEVICE_CONFIG`, and `device_config put device_idle inactive_to 111000` exits 255 with
   `SecurityException: Permission denial for flag 'device_idle/inactive_to'; allowlist permission
   granted, but must add flag to the allowlist`. `delete` fails the same way, and `reset` needs the
   same permission. Only root (T3) could use this path.
2. **Key-by-key `device_config` writes drop earlier keys from the running constants.**
   `DeviceConfig.handleChange` (`DeviceConfig.java`, API 37) hands listeners a `Properties` that
   holds only the keys in the change URI. `DeviceIdleController.Constants.onPropertiesChanged` passes
   it to `UserSettingDeviceConfigMediator.setDeviceConfigProperties`, which replaces the stored
   properties, and then re-reads every key. On the API 37 emulator, `put light_idle_to 600000`
   showed `light_idle_to=+10m0s0ms`; a following `put notification_allowlist_duration_ms 40000`
   showed the new value and `light_idle_to=+5m0s0ms` again, while `device_config list` still stored
   600000. Only the last key of a sequence of puts is live until the next full load (boot).
3. **The global string overrides `device_config` key by key and works for shell.** `Constants` uses
   `UserSettingDeviceConfigMediator.SettingsOverridesIndividualMediator(',')`
   (`services/core/java/com/android/server/utils/UserSettingDeviceConfigMediator.java`): each key
   reads `Settings.Global.device_idle_constants` first, then `device_config`, then the default. A
   content observer re-parses the whole string on every change. Shell holds `WRITE_SECURE_SETTINGS`.
   On the API 37 emulator `settings put global device_idle_constants
   inactive_to=333000,idle_to=4440000` took effect at once; writing `idle_to=5550000` alone then
   returned `inactive_to` to its default (whole-string semantics). The parser is
   `android.util.KeyValueListParser`: a value that does not parse falls back for that key only; a
   pair without `=` clears every key.
4. **It is what the owner's phone already uses.** On One UI 9 (`CP2A.260605.016`) `device_config
   list device_idle` is empty and the global string holds the owner's Tasker values, including One
   UI's `skip_sensing=true`; `dumpsys deviceidle` reports exactly those (audit §4.4).
5. **Apps cannot read the string.** `Settings.DEVICE_IDLE_CONSTANTS` is `@hide` without
   `@Readable` (`core/java/android/provider/Settings.java`, API 37), so `Settings.Global.getString`
   throws `SecurityException` for a non-system app since API 31. The worker (shell or root) reads it
   with `settings get global device_idle_constants`.

## Decision

- **Lever A writes one string.** The app builds the whole `device_idle_constants` value from the
  active profile with a strict encoder (`DeviceIdleSettings` in `:core:privilege-api`: lower snake
  case keys, numeric or boolean values, no empty or duplicate pairs) and writes it in one
  `Settings.Global.putString` call from its own process, which needs `WRITE_SECURE_SETTINGS`
  (tier T1). One write is one observer callback and one `updateConstantsLocked`, so a profile
  switch is atomic. `device_config device_idle` is never written.
- **The worker has no lever A write path.** `IDozeWorker` VERSION 2 drops
  `setDeviceIdleConstants`, `deleteDeviceIdleConstants` and `resetDeviceIdleConstants`
  (transactions 10–12, never reused) and adds two reads: `getDeviceIdleSettings` (`settings get
  global device_idle_constants`) and `listDeviceIdleConfig` (`device_config list device_idle`).
  Where a worker exists, setup grants `WRITE_SECURE_SETTINGS` with `grantSelf`. Writes therefore
  come from the app's own package, and SettingsProvider records the calling package with each
  value. The owner's Tasker writes through Shizuku as `com.android.shell`, so it can be told apart.
- **Snapshots.**
  - `previous.json` keeps the raw string as found, or "absent", plus the effective values from
    `dumpsys deviceidle`. "Return to previous setup" writes that string back, or deletes the
    setting.
  - `stock.json` holds the effective values with the setting absent. It is captured once during
    onboarding with consent and with the screen on: delete the setting, dump, then write the
    previous string back. It is for display and comparison only.
  - "Restore stock" deletes the setting. `device_config` (server values) is left as it is.
- **A profile key left out means stock.** In the SPEC 6.2 table, "stock" now means the key is not
  in the string, so it follows `device_config` or the default.
- **Vendor keys.** One UI's `skip_sensing`, `skip_locating` and `dna_request_version` are never
  written by a built-in profile. An imported or custom profile keeps them, marked "effect unknown on
  this phone", until a probe shows what they do.
- **Verification and drift.**
  - The read-back waits up to 2 s for the observer, then compares every profile key.
  - Drift compares the exact string at each screen off, read through the worker or, without one,
    through the effective values from `dumpsys deviceidle`.
  - A content observer on the setting may report changes sooner (P2 verifies that an app can
    observe it).

Second opinion (fresh read-only subagent, given the AOSP sources, the emulator results and the
options neutrally):

- It recommended the same string path, for the same reasons.
- Its strongest argument against: the setting is legacy and Google could remove it, as
  `job_scheduler_constants` was removed. It is also a single value shared by every writer, it
  survives uninstalling the app, and a malformed pair clears every key.
- It proposed the app-process writes and the no-write worker adopted above. It also proposed the
  `stock.json` capture with the setting deleted, and a guardrail for the allowlist durations.
- It flagged as unverified: root writes to `device_config` (moot now); whether an app targeting
  API 37 can read or observe the setting (reading is refused, `Settings.java` above; observing is
  checked in P2); and what One UI does with `skip_sensing`.

Kept from my own view: the encoder keeps the caller's key order instead of sorting, so
"Return to previous setup" can write the owner's string back unchanged.

## Consequences

- SPEC 1.4 (risk table), 3, 4.1, 4.3, 4.4, 5.2, 5.6, 6.2, 6.3, 6.5, 11.4, 13.3 and 16.1 are
  edited and marked "(ADR 0012)".
- **Lever A now works at T1, without Shizuku.** Levers B and C still need the worker.
- **Device safety.**
  - The string survives uninstalling the app, so the UI says "Restore stock before uninstalling".
  - Custom profiles may not lower `max_temp_app_allowlist_duration_ms`,
    `mms_temp_app_allowlist_duration_ms`, `sms_temp_app_allowlist_duration_ms` or
    `notification_allowlist_duration_ms` below stock. Those keys keep SMS, MMS and notification
    exemptions working.
- **Probe.** The 4.4 lever A probe writes a test value from the app process with the screen on,
  waits for it in `dumpsys deviceidle`, then restores the exact previous string. Persistence over
  10 minutes and a reboot is checked with the engine in P2.
- **Fallback.** If a later release drops the setting, the probe reports lever A as missing with
  that reason. A root-only `device_config` path is then weighed in a new ADR. It has to get past
  the listener problem in point 2, and it is not yet known whether a batch write avoids it.
- **Simulated foreign writer (SPEC 13.3).** Tests use `settings put global device_idle_constants
  inactive_to=1800000` over ADB, which writes as `com.android.shell`, the way Tasker does.
- **ADR 0004 follow-up.** ADR 0004 left open whether `device_config override` should replace
  re-applying on drift. It should not. `override` writes `device_config`, which this ADR stops
  doing, and server sync cannot override keys held in the string anyway. The override commands are
  removed from SPEC 16.1.
- **P1 worker contract test.** It now checks the app-written string through the worker's read, in
  place of the `device_config` round trip.
