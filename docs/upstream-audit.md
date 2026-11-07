# Upstream audit (SPEC 2)

Baseline: tag `upstream-baseline` = `a5b7c4a` (Akylas/EnforceDoze `master`, 1.10.2, versionCode 86).
Audited 2026-11-07 from the source, a build on build-server, and runs on the API 35, 36 and 37
emulators on akane-linux. Raw captures are in `fixtures/sdk_gphone64_x86_64/`; spec statements that
did not hold are corrected in `SPEC.md` through ADR 0004.

The parity table (§6) is the living part of this file: it is updated in the same commit that ports
or drops a feature, and an upstream class is deleted only when every row it served is covered by new
code with a test (SPEC 12.1). P7 publishes it as `docs/parity.md`.

## 1. Facts (SPEC 2.1)

| Item | Spec | Found | Evidence |
| --- | --- | --- | --- |
| Version | 1.10.2 (86); last feature commit 2026-05-19, translations through 2026-07-26 | Confirmed | `app/build.gradle`; `git log` ("feat: doze periods" 2026-05-19, Weblate merge #103 2026-07-26) |
| SDK | compile 36, target 36, min 23 | Confirmed | `app/build.gradle` |
| Code | 43 Java, 2 Kotlin, `ForceDozeService.java` 1,636 lines, ~8,100 total | Confirmed: 41 main + 2 test Java files; `NotificationService.kt`, `MaterialListPreference.kt`; 8,135 lines in `main`, 8,161 with tests | `find`, `wc -l` |
| Build | Gradle 8.13, Java 17 | Confirmed; AGP 8.13.2, Kotlin plugin 2.0.0. Repositories still list `jcenter()`; `libsuperuser` uses a dynamic version (`1.1.0.+`) | `build.gradle`, wrapper properties |
| Builds unchanged | — | `assembleDebug` passes in 54 s (JDK 17, cold Gradle cache) | build log, P0 |
| Unit tests | — | Do not compile: no test dependency declared (`package org.junit does not exist`). Fixed in `2b3c43c` with JUnit 4.13.2; 1 test passes | `testDebugUnitTest` |
| Lint | — | `lintDebug` fails: 2 errors (`SuspiciousIndentation` in `DozeTunableHandler`, `UseAppTint` in a layout), 157 warnings. Frozen in `app/lint-baseline.xml` (ADR 0003) | lint report |
| Privileges | libsuperuser root, or Shizuku api 13.1.5 | Confirmed. 13.1.5 is still the newest `dev.rikka.shizuku:api` on Maven Central | Maven metadata |
| Doze control | force-idle / unforce with root or Shizuku; constants without them | Code path confirmed; the API 34+ non-root path cannot work (§4.3) | source, emulator |
| Sensors | `restrict <package>` / `enable`; `service call sensor_privacy` | Bare `restrict` by default, rejected on API 35–37; `sensor_privacy` call is root-only (§3) | fixtures, source |
| Features | List in SPEC 2.1 | Confirmed, plus features the spec does not list: airplane mode, Bluetooth, GPS and battery saver in Doze, notification blocklist, wait for unlock, ignore lock-screen timeout, auto-rotate/brightness fix, re-enter receiver, generic settings receiver, Doze tunables screen, app shortcuts (§6) | `prefs.xml`, manifest |
| UI | Activities, XML, material-dialogs 0.9.3, AppCompat | Confirmed (`material-dialogs:core:0.9.3.0`, AppCompat 1.7.0, Material 1.12.0) | `app/build.gradle` |
| License | GPL-3.0, forked from ForceDoze | Confirmed (`LICENSE.txt`; README credits @theblixguy) | — |

## 2. Weak points on Android 17 and One UI 9 (SPEC 2.2)

| Problem | Verdict | Evidence |
| --- | --- | --- |
| Shizuku `newProcess` by reflection | Confirmed: `ShizukuHandler` calls `getDeclaredMethod("newProcess", …)` + `setAccessible(true)`; `javap` on api 13.1.5 shows `private static ShizukuRemoteProcess newProcess(String[], String[], String)` | `ShizukuHandler.java:139` |
| Official Shizuku 13.6.0 cannot authorize on Android 17 (#2180) | Not tested in P0 (no Shizuku on the emulators yet); P1 decides in its ADR | — |
| `service call sensor_privacy <code>` | Mechanism confirmed and fragile; code 9 still maps to `setSensorPrivacy(boolean)` in AOSP `android-13.0.0_r1`, `android-16.0.0_r1` and `android-17.0.0_r1` (upstream uses 4 below S, 8 on S, 9 from T). It runs through `Shell.Pool.SU`, so it fails without root even in Shizuku mode | `ForceDozeService.java:1218–1240`, `ISensorPrivacyManager.aidl` |
| Forced-idle delay timed by the app | Worse than stated: a `PARTIAL_WAKE_LOCK` (`forcedoze:tempWakelock`) held up to 10 minutes plus a `java.util.Timer`; sensor restriction waits another 2 s on a `Timer` | `ForceDozeService.java:660, 1527–1590` |
| `device_config` values overwritten by server sync | Not observable on emulators (no server sync); P2 drift tests use a simulated foreign writer (SPEC 13.3) | — |
| No evidence that Doze worked | Confirmed and sharper: the service records "IDLE" and "Entering Doze" from its own intent, not from system state (§4.3) | upstream run on API 37 |
| Long single service class, legacy dialogs | Confirmed: `ForceDozeService` 1,636 lines, `MainActivity` 917, `SettingsActivity` 784 | `wc -l` |

## 3. Further findings (not in SPEC 2)

1. **Unprotected exported receivers.** `EnableForceDozeService`, `DisableForceDozeService`,
   `AddWhiteListReceiver`, `RemoveWhiteListReceiver`, `SettingsChangeReceiver` and `ReenterDoze` are
   exported with no permission: any installed app can switch the service, change settings or edit the
   allowlist. `AddWhiteListReceiver` also concatenates the package extra into a shell string. SPEC
   11.1 (permission-protected receiver, validated package names) replaces them.
2. **Bare `restrict` by default.** With no allow package chosen, upstream sends
   `dumpsys sensorservice restrict`; API 35, 36 and 37 return `INVALID_OPERATION` and the mode stays
   `NORMAL`. The default "disable motion sensing" never worked on these releases.
3. **Non-root exit resets the whole namespace.** `leaveDoze()` on API 34+ without root runs
   `device_config reset trusted_defaults device_idle`, which would erase any constants the owner set
   by other means (it also fails today, §4.3).
4. **Renamed keys.** The tunables screen writes `max_temp_app_whitelist_duration`,
   `mms_temp_app_whitelist_duration`, `sms_temp_app_whitelist_duration` and
   `notification_whitelist_duration`; API 35–37 report `*_allowlist_duration_ms` instead.
5. **More binder transaction numbers and reflection.** `service call phone <code>` (mobile data),
   `service call notification <code>` (notification blocklist), reflection on `ITelephony` and on
   `WifiManager.getWifiApState`. All are forbidden by SPEC 12.4 and are replaced or dropped (§6).
6. **Re-enable Doze dialog.** `MainActivity` offers to re-enable Doze when it is off
   (`setprop persist.sys.doze_powersave true`, `deviceidle disable/enable all`); the capability probe
   (SPEC 4.4) covers it with `cmd deviceidle enabled all`.

## 4. Emulator results

AVDs `pixel-api35`, `pixel-api36`, `pixel-api37` (Google APIs x86_64, headless). Builds:
API 35 `AE3A.240806.043`, API 36 `BE2A.250530.026.F3`, API 37 `CE2A.260420.051.A3`. Captured with
`tools/fixtures/capture.sh --walk`.

### 4.1 Commands as shell (uid 2000)

| Command | API 35 | API 36 | API 37 |
| --- | --- | --- | --- |
| `step deep` from screen off | INACTIVE → IDLE_PENDING → SENSING → LOCATING → IDLE → IDLE_MAINTENANCE → IDLE | SENSING → IDLE (LOCATING skipped) | Same as 36 |
| `remove_idle_location` flag | absent | `true` | `true` |
| `step light` | IDLE ↔ IDLE_MAINTENANCE | Same | Same |
| `step` with the screen on | — | — | Stays ACTIVE (the walk must turn the screen off first) |
| `force-idle deep` / `unforce` | Works | Works | Works; forced: `deep=IDLE light=OVERRIDE force=true` |
| `dumpsys sensorservice restrict` (bare) | `INVALID_OPERATION` | `INVALID_OPERATION` | `INVALID_OPERATION` |
| `restrict com.android.shell` | `Mode : RESTRICTED : com.android.shell` | Same | Same |
| `enable` | `Mode : NORMAL` | Same | Same |
| `device_config list device_idle` | empty | empty | empty |
| `settings get global device_idle_constants` | `null` | `null` | `null` |
| Motion sensor | `mMotionSensor=null` | Same | Same |
| Event-log tags | `device_idle`, `device_idle_light`, `device_idle_step`, `device_idle_light_step`, `device_idle_on_start`, `_on_phase`, `_on_complete`, `device_idle_off_start`, `_off_phase`, `_off_complete` | Same | Same |
| `batterystats --history` | Works; `device_idle=full/light/off` states | Same | Same |

Stock constants on the emulators are compressed and differ by level (`inactive_to` 1 min on API 35,
15 s on 36 and 37); stock is read from the device, never assumed. API 37 also offers
`cmd deviceidle motion`, `device_config override` and `batterystats --wakeups` (ADR 0004).

### 4.2 Commands from the app's uid (T1 grants, `run-as` on the debuggable build, API 37)

With `DUMP`, `WRITE_SECURE_SETTINGS` and `READ_LOGS` granted. `run-as` runs in `runas_app`, not
`untrusted_app`, so these results guide P1 but do not replace its in-app probes.

| Action | Result |
| --- | --- |
| `dumpsys deviceidle`, `dumpsys sensorservice` (read) | Works |
| `dumpsys sensorservice restrict com.android.shell` | Works (shell then reads `RESTRICTED`) |
| `dumpsys batterystats --history` | Denied: needs `PACKAGE_USAGE_STATS` |
| `cmd deviceidle force-idle deep`, `whitelist +pkg` | Denied: needs `DEVICE_POWER` (worker only) |
| `device_config put` | Not reachable (`exec failed … Permission denied`; `cmd device_config`: service not found) |
| `settings put global …` (binary) | Denied (`INTERACT_ACROSS_USERS`); in-process `Settings.Global` untested |
| `logcat -b events -d` | Works under `run-as` (it carries the `log` group, so this does not prove `READ_LOGS`) |

### 4.3 Upstream 1.10.2 on API 37

- Installs, cold-starts in 565 ms (`am start -W`), no crash; foreground service type `specialUse`.
- With T1 grants and no root or Shizuku, at screen off it logs "Entering Doze" and "Unrooted
  device, putting custom values in device_idle_constants…", then SELinux denies the shell it spawns:
  `avc: denied { getattr } for path="/system/bin/device_config" … scontext=u:r:untrusted_app_34`.
  12 s later `deep=INACTIVE force=false`, `device_config list device_idle` is empty, yet the service
  logs "Last known Doze state: IDLE" at screen on. "Disabling motion sensors" runs the bare
  `restrict`, which fails silently.
- Main screen: `docs/evidence/p0/upstream-main-api37.png`.

## 5. The earlier fork effort

The fork's former `master` held an earlier Codex effort ("android16-reliability", 11 commits, merged
as fork PR #1 on 2026-10-03) that redesigned the Java app in place. `main` starts from upstream
(ADR 0002); the old branches were deleted at the owner's request (ADR 0006). Its `AUDIT_REPORT.md` independently found several of the problems
above (unprotected receivers, root-only sensor privacy, namespace reset, renamed keys, the delay
wakelock, the non-compiling tests and the lint errors); its emulator runs failed on a host without KVM,
so no runtime result came out of it; this audit ran on akane-linux.

## 6. Feature parity

Decisions: **port**, **port (root tier)**, **drop** (with reason). Status: `upstream` until the new
code with a test replaces it. Features the spec lists in 2.1 follow SPEC 2.3's defaults; the others
are decided here and listed in `docs/questions.md` Q4 so the owner can object.

| # | Upstream feature | Where | Decision | New home | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | Force deep Doze at screen off | `ForceDozeService.enterDoze/applyDoze` | port | Lever B, Classic profile (5.3, 6.1) | upstream |
| 2 | Delay before Doze (`dozeEnterDelay`) | service + wakelock timer | port | Lever A constants; optional delay alarm (5.2, 5.3) | upstream |
| 3 | Ignore lock-screen timeout | service | drop: the delay is expressed by constants (5.2), so there is no app timer to tie to the lock screen | — | upstream |
| 4 | Wait for unlock before leaving Doze | service | port, off by default; departs from 5.3 (unforce at screen on), so P2 decides it in an ADR | Profile option (5.3) | upstream |
| 5 | Disable when charging | service | port | Charging hold-off (5.5), always on | upstream |
| 6 | Execution mode (root / Shizuku) | settings | port | Tiers T0–T3 (4) | upstream |
| 7 | Doze allowlist screen | `WhitelistAppsActivity` | port | Lever D, Allowlist screen (5, 9.1) | upstream |
| 8 | Add / remove allowlist broadcasts | `Add/RemoveWhiteListReceiver` | port | Automation API with permission (11.1) | upstream |
| 9 | Motion sensing off, with an allowed package | service, `PackageChooserActivity` | port | Lever C restricted mode (5.4) | upstream |
| 10 | All sensors off in Doze | `setAllSensorsState` | port | Lever C `allSensorsOff`, Strict, "Sensors off" tile (5.4, 9.2) | upstream |
| 11 | Biometrics off in Doze | service | port (root tier) | T3 (1.2) | upstream |
| 12 | Wi-Fi off in Doze | service | port (root tier) | T3 (1.2) | upstream |
| 13 | Mobile data off in Doze | `service call phone` | port (root tier), without transaction numbers | T3 (1.2, 12.4) | upstream |
| 14 | App suspension during Doze | `BlockAppsActivity` | port (root tier) | T3 (1.2) | upstream |
| 15 | Airplane mode in Doze | service | drop: blocks calls and emergency alerts (SPEC 0.2 rule 1, 1.3) | — | upstream |
| 16 | Bluetooth off in Doze | service | drop: cuts the Galaxy Watch link and Android Auto that SPEC 3.3 and 5.5 protect | — | upstream |
| 17 | GPS off in Doze | service | drop: deep idle already stops app location; switching the location setting touches emergency location (0.2 rule 1) | — | upstream |
| 18 | Battery saver on in Doze | service | drop: One UI power saving runs its own quick Doze; the report labels it instead (3.3), and the app does not become a second manager of it | — | upstream |
| 19 | Notification blocklist during Doze | `BlockNotificationsActivity`, `service call notification` | drop: binder transaction numbers (12.4) and outside scope (1.2); Doze already defers normal-priority notifications | — | upstream |
| 20 | Hotspot exception | service | port | Hotspot hold-off (5.5) | upstream |
| 21 | Music exception | service, `NotificationService` | port | Audio hold-off via `AudioManager` playback configurations (5.5) | upstream |
| 22 | Keep the current foreground app awake | service | port | "Keep awake" app hold-off (5.5) | upstream |
| 23 | Custom Doze periods | `CustomDozePeriodReceiver` | port | Plans and the night window (6, 6.5) | upstream |
| 24 | Auto-rotate and brightness fix | service | drop: a workaround for the removed non-root sensor hack; sensors return with `enable` at screen on | — | upstream |
| 25 | Persistent / disabled notification | service | port | Foreground-service notification (9.2) | upstream |
| 26 | Doze stats and battery stats screens | `DozeStatsActivity`, `DozeBatteryStatsActivity` | port (replaced) | Sessions, verdicts, night report (8) | upstream |
| 27 | Battery consumption list | `BatteryConsumptionAdapter` | drop: Coulomb covers per-app attribution (SPEC 1.2, 2.3) | — | upstream |
| 28 | Log screen and logcat toggle | `LogActivity` | port (replaced) | Log screen, action log (8, 9.1) | upstream |
| 29 | Reset EnforceDoze, reset stats | settings | port | Restore stock, reset app (5.6, 9.1) | upstream |
| 30 | Tasker broadcasts screen and receivers | `TaskerBroadcastsActivity`, `Enable/DisableForceDozeService`, `SettingsChangeReceiver` | port | Automation API (11.1), `docs/automation.md` | upstream |
| 31 | Re-enter Doze broadcast | `ReenterDoze` | port | Automation `APPLY_PLAN` / `RESUME` (11.1) | upstream |
| 32 | Doze tile | `ForceDozeTileService` | port | "Doze plan" tile (9.2, 11.2) | upstream |
| 33 | Airplane-mode tile | `AirplaneTileService` | drop: the system tile covers it (SPEC 2.3) | — | upstream |
| 34 | Doze tunables screen | `DozeTunablesActivity` | port | Custom profiles with guardrails (6.3) | upstream |
| 35 | Self-allowlist and battery-optimisation request | `RequestIgnoreBatteryActivity` | port | Onboarding, One UI "Never sleeping apps" (3.3, 9.1) | upstream |
| 36 | Start at boot and after update | `BootCompleteReceiver`, `AutoRestartOnUpdate` | port | Foreground service start (12.4) | upstream |
| 37 | Re-enable Doze when disabled | `MainActivity` dialog | port | Capability probe and onboarding (4.4) | upstream |
| 38 | About, sponsor link, Custom Tabs | `AboutAppActivity`, `CustomTabs` | port About with GPL notice and credits; drop the in-app Custom Tabs browser (links open the default browser) | Settings → About (9.1, 12.1) | upstream |
| 39 | App shortcuts (Settings, Battery stats) | `res/xml/shortcuts.xml` | drop for v1: Home and the tile cover the entry points; revisit after P5 | — | upstream |
| 40 | Upstream translations (Weblate) | `res/values-*` | drop: all copy is rewritten; English and Turkish only (9); translators stay credited in `NOTICE` | — | upstream |
| 41 | Upstream website, store metadata, release workflow | `docs/index.html`, `fastlane/`, `.github/workflows/release.yml` | drop: the fork ships through GitHub Releases (12.6); removed in P7 | — | upstream |
