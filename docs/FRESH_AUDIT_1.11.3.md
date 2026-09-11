# Sensor, notification and efficiency audit — 1.11.3-dev

This follow-up reviews the privileged backend, monitoring lifecycle, restoration, Android 16 option support, history and controls. The user's Samsung SM-S938B/API 36 readings from 1.11.2 confirmed forced deep Doze while non-interactive and exit after wake. They did not verify sensor restriction or battery savings. Emulator validation cannot establish every One UI behavior or guarantee that all bugs have been found.

## Sensor controls

The old `dumpsys sensorservice restrict` command omitted a required argument when no exception was selected. Android 16 rejects that command. An empty argument is also unsafe: SensorService matches an exception as a substring, so an empty exception matches every package.

The new command always supplies either the selected package or an impossible package token. It reads the operating mode before changing it, verifies the resulting mode, and treats an exit-zero command without the requested state as a failure. New restoration entries carry the expected exception and refuse to overwrite a different restriction. Old journal commands are normalized through the verified implementation, but old undo records lack an exception and therefore cannot provide this ownership check. An unreadable operating mode is reported without applying a new restriction.

**SensorService restriction and Sensors Off privacy are different controls.** The former restricts sensor clients; the latter requests Android's global privacy switch. The previous `sensors get` diagnostic checked only privacy, so `false` did not establish whether motion access was restricted. Diagnostics now exposes both and retains a bounded, local sensor evidence report. Successful read-only queries do not add history. Disabling statistics also disables sensor evidence.

Evidence records the returned Android state and whether the screen was interactive when the command returned. It does not measure physical sensor power, uninterrupted suppression between observations, camera/microphone behavior, or energy saved. The app adds no sensor listener or periodic verification timer. Actual accelerometer listeners exist only in the opt-in emulator tests.

If a package exception is selected, Android initially stops active sensor connections; the package may need to register its listener again. The exception uses Android's substring matching, not exact package isolation.

## Notification and battery work

- Keep one notification identity and timestamp. By default routine Doze transitions do not repost the notification. Access errors and recovery still appear. Optional **Live notification details** restores changing state text.
- Remove command-process polling; use blocking completion with bounded timeouts and output.
- Avoid duplicate restore work and wake locks when no session or pending changes exist. Coalesce overlapping restores and preserve the screen-off delay deadline.
- Keep operation wake locks bounded and release them after cleanup, including service destruction and maintenance recovery.
- Avoid redundant self-exemption commands, obsolete queued idle observations, redundant schedule alarms, and unchanged history parsing.
- Apply sensor display workarounds only after a real sensor mutation; avoid redundant motion restriction when Sensors Off already applies.

These are reductions in work and wake-lock exposure. No battery percentage improvement is claimed without comparable physical-device measurements.

## Correctness and usability

- Restart sensor protection if the phone wakes to a secure lock screen and sleeps again without unlocking, including when restoration is still queued.
- Continue independent restoration entries after one command throws or fails. Keep failed undo entries for retry and preserve specific error details.
- Cancel interrupted Shizuku requests and discard uncertain connections instead of letting abandoned work execute later.
- Refresh histories and tiles through their actual lifecycles; confirm history deletion and clear associated evidence together.
- Report battery changes as percentage points. Zero is below whole-point resolution, and charging, missing or rising readings do not produce a misleading consumption result.
- Continue scanning active media after one malformed notification; avoid repeated automatic root authorization attempts.
- Generate Tasker documentation from supported setting keys. Put functional settings before sponsorship links.

## Android 16 feasibility

| Option | What can be established | Limitation / behavior |
| --- | --- | --- |
| Forced deep Doze | Android idle state plus shell state; already observed on the user's Samsung | Maintenance and unobserved gaps remain possible. |
| Sensor access restriction | SensorService operating mode and emulator accelerometer delivery | No guarantee of physical sensor power-off or every vendor sensor path. |
| Sensors Off privacy | Privileged permission, privacy state readback and emulator accelerometer delivery | Separate from restriction; vendor camera/microphone behavior is not inferred from an accelerometer test. |
| Location, battery saver | Android shell control with integration checks and restoration | OEM policy and charging can limit effective behavior. |
| Wi-Fi, mobile data, airplane mode, app controls | Privileged commands and existing restoration journal | Hardware, carrier, OEM and selected backend constraints remain; failures are exposed in Diagnostics. Wireless debugging can be interrupted. |
| Biometric toggle | Old setting is not a reliable Android 16 control | Ignored and marked unavailable on API 36; a saved selection can be turned off. |
| Legacy ADB grants | Limited older-Android implementation | Android 14+ setup offers Shizuku or root. |
| Doze tunables | Correct `device_idle` namespace, current key mappings, validated edited values and storage readback | Stored overrides do not prove policy effect. Retired keys are excluded. Existing legacy overrides can take precedence and are not erased. |
| Schedule boundaries | Eligibility and merged interval boundaries | Android may defer alarms while asleep; events recheck eligibility. |

## Phone acceptance check

1. Stop monitoring and finish pending restoration before updating. Start Shizuku, authorize EnforceDoze and reconnect if needed.
2. Enable **Restrict sensor access during Doze**. Leave **Sensors Off privacy switch during Doze** off for this first check, so each control is tested independently.
3. Start monitoring, turn the screen off long enough to enter Doze, then wake it.
4. Open **Diagnostics → Sensor evidence**. Look for a successful change from `NORMAL` to `RESTRICTED` while non-interactive, followed by a successful restoration to `NORMAL` on wake. A failure or unknown reading is not confirmation.
5. Repeat with the separate Sensors Off option if desired. Look for verified `false → true` and `true → false` privacy changes. Check normal rotation and the apps you use after waking.
6. Keep **Live notification details** off. Ordinary screen cycles should leave the monitor notification alone; Android/One UI can still rank notifications itself.
7. For energy comparison, use comparable nights, the same optional settings and workload, and Samsung's battery usage view. Short sessions and whole-percent readings cannot establish small savings.

## Validation

The regression suite includes pure-Java command/parser/schedule tests, Android UI and lifecycle tests, and opt-in Shizuku integration tests on a disposable API 36 emulator. Privileged sensor tests require accelerometer events before the change, no events during suppression, and resumed delivery after restoration. The screen-cycle test checks the actual posted notification's identity, time and text. A separate secure-lock-screen test checks wake/resleep restoration races.

Use the attached pull request checks for execution results. A test's presence is not a claim that it passed, and emulator success does not replace the Samsung acceptance check above.

## Platform references

- [Android 16 SensorService implementation](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/sensorservice/SensorService.cpp): operating modes, required argument, substring exception and privacy handling.
- [Android Sensors Off](https://source.android.com/docs/core/interaction/sensors/sensors-off): framework privacy behavior and vendor integration.
- [Android 16 DeviceIdleController](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/apex/jobscheduler/service/java/com/android/server/DeviceIdleController.java): DeviceConfig namespace and tunable names.
- [Android 16 BiometricService](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/services/core/java/com/android/server/biometrics/BiometricService.java): modern biometric settings and control paths.
