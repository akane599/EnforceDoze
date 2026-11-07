# 0004. Spec corrections from the P0 audit

- Status: Accepted
- Date: 2026-11-07
- Phase: P0
- Spec: 2.1, 2.2, 4.1, 5.1, 5.4, 12.2, 13.3, 16.1, 16.3

## Context

The P0 gate asks that section 2 be confirmed or corrected with real results. The audit
(`docs/upstream-audit.md`) read the upstream source at `upstream-baseline` and ran upstream 1.10.2
and the raw commands on the API 35, 36 and 37 emulators (captures in `fixtures/`). Most of
section 2 holds; the statements below did not, and some of them reach into later sections.

## Decision

`SPEC.md` is corrected where a statement is factually wrong; each edit is marked "(ADR 0004)".

| § | Spec said | Found | Evidence |
| --- | --- | --- | --- |
| 2.1 Doze control | Without root or Shizuku, constants go through `device_config put device_idle` on API 34+ | That code path exists but cannot work: it execs `device_config` from the app process, which SELinux denies (`avc: denied { getattr } for path="/system/bin/device_config" … scontext=u:r:untrusted_app_34`). Nothing is applied, yet the service logs "Entering Doze" and records the state as IDLE | Upstream run on API 37, audit §4.3 |
| 2.1 Sensors | `dumpsys sensorservice restrict <package>` | Upstream sends `restrict` with no package unless the owner picked one; API 35, 36 and 37 answer `INVALID_OPERATION` and stay `NORMAL`, so the default "motion sensing off" does nothing. With a package it works (`Mode : RESTRICTED : com.android.shell`) | `walk-sensor-restrict*.txt` in each fixture |
| 2.1 Sensors | All-sensors toggle through `service call sensor_privacy <code>` | Runs through `Shell.Pool.SU` only, so it fails in Shizuku mode | `ForceDozeService.setAllSensorsState` |
| 2.2 | The forced-idle delay needs a wakeup alarm | Upstream holds a `PARTIAL_WAKE_LOCK` for up to 10 minutes and counts the delay with `java.util.Timer`; the phone stays awake for the whole delay. Sensor restriction waits a further 2 s on another `Timer` | `ForceDozeService` lines 1527–1590, 660 |
| 2.2 | Transaction numbers change between releases | True across releases (upstream uses 4, 8, 9), but 9 is still `setSensorPrivacy(boolean)` in the AOSP `android-16.0.0_r1` and `android-17.0.0_r1` tags. The fix stands; the immediate failure is the root-only shell | `ISensorPrivacyManager.aidl` at each tag |
| 4.1 T1 | `DUMP` reads `batterystats` | `dumpsys batterystats` also needs `PACKAGE_USAGE_STATS` ("Permission Denial: can't dump BatteryStatsService … missing android.permission.PACKAGE_USAGE_STATS"); T1 already lists the usage-stats appop. `DUMP` alone is enough for `dumpsys sensorservice restrict` and `enable` from the app's uid | `run-as` probes on API 37, audit §4.2 |
| 5.1 | Deep path goes SENSING → LOCATING → IDLE | On API 36 and 37 the flag `com.android.server.deviceidle.remove_idle_location=true` skips LOCATING: `step deep` goes SENSING → IDLE and the event log shows state 4 and 5 in the same millisecond. API 35 still walks LOCATING | `walk-deep-steps.txt`, `events.txt` |
| 5.4 | `restrict <allowPackage?>` (optional) | The allow package is mandatory; with nothing to exempt, pass the app's own package (it holds no sensor connections) | As 2.1 Sensors |
| 12.2 | Shizuku API "current release, not upstream's 13.1.5" | 13.1.5 is still the newest `dev.rikka.shizuku:api` on Maven Central (metadata `lastUpdated` 2023-09-21). Upstream's problem is calling the private `newProcess` by reflection (`javap`: `private static … newProcess(String[], String[], String)`), not the version | Maven metadata, `javap` |
| 13.3 Motion | `adb emu sensor set acceleration …` | The emulators have no motion sensor (`mUseMotionSensor=true mMotionSensor=null`), so injected acceleration cannot reach Doze. API 37 has `cmd deviceidle motion` ("Simulate a motion event to bring the device out of deep doze"); P2 verifies it and uses it | `deviceidle.txt`, `help-deviceidle.txt` |
| 16.1 | Command reference | Adds what the API 37 help texts offer: `cmd deviceidle motion`, `get offbody`, `force-modemanager-quickdoze`; `device_config override` / `clear_override` / `list_local_overrides` (a sticky local value that "ignores server-updates for this flag", a candidate answer to drift in P2); `dumpsys batterystats --history-start <num>` and `--wakeups` | `help-*.txt` in the API 37 fixture |
| 16.3 Q5 | Can the emulator simulate motion? | Answered above: not through sensors; through `cmd deviceidle motion` | — |

## Consequences

- P2 writes its key ADR against the AOSP `android-17.0.0_r1` `DeviceIdleController` and decides
  whether `locating_to` stays in the profiles (it is harmless where LOCATING is skipped) and whether
  `device_config override` replaces re-applying on drift.
- Emulator "stock" constants are compressed and differ by API level (`inactive_to` 1 min on API 35,
  15 s on 36 and 37). Tests read stock from the device, never from AOSP defaults (SPEC 5.2 already
  says so).
