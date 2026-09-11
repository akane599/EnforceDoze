# Android 16, Shizuku and UX review

Review date: 2026-09-10. Baseline: `a5b7c4acc7a9d8b9f48b906be66c4792dd2cd77b` on `akane599/EnforceDoze/master`.

This is a substantial development revision, not a certification that every bug has been eliminated. The baseline already targeted SDK 36; the changes address execution, lifecycle and interface behavior behind that target. Physical Samsung testing and battery measurements remain necessary before a stable release.

## Findings and changes

| Area | Problem found | Resulting behavior |
| --- | --- | --- |
| Privileged commands | Deprecated reflective Shizuku process API; sequential output reads could block; commands lacked consistent timeouts and result handling | A Shizuku UserService executes commands through AIDL. One application queue orders mutations and restoration; output is bounded and failures retain exit codes. |
| Access lifecycle | Cached authorization, overwritten availability listener, root commands in Shizuku paths | Binder death and permission changes update all registered listeners. The chosen backend is explicit; Shizuku does not fall back to root. |
| Device restoration | Original radio, sensor, notification and battery-saver states could be lost or overwritten | An undo journal is committed before each mutation. Restoration uses the original backend, proceeds in reverse order, and removes successful undo entries individually. Failed undo remains available for retry. |
| Rapid screen changes | Entry timers and asynchronous commands could act after screen-on or service stop | Generation checks invalidate stale entry work; entry and restoration share one queue. Delays use alarms rather than long wake locks. |
| Unlock and maintenance | Optional device controls could interfere with unlocking; maintenance detection used stale/ambiguous state | Enhancements restore at screen-on, including when waiting for unlock. Maintenance uses the exact deep-idle state, restores enhancements, and preserves the core session undo. |
| Charging and schedules | Schedule transitions overwrote manual enablement; history could misrepresent charging | Manual enablement is independent of schedule eligibility. Charging suspends automation when configured, and charging sessions do not report battery consumption. |
| Foreground service | Incorrect API thresholds and incomplete special-use declaration | Foreground startup is used from API 26; the API 34 special-use type has a declared subtype. Rejected background starts produce an actionable state instead of reporting success. |
| Samsung-sensitive controls | Hidden API and transaction assumptions; uncertain hotspot state could cause connection changes | Sensor privacy and notification methods are resolved by name in the privileged process. Unknown hotspot state preserves connectivity. Denied mutations appear in diagnostics. |
| Settings | Preference veto ignored, fragile number-picker state, permission responses affected unrelated controls, runtime writes triggered reloads | Preference veto and recreation are handled. Notification access affects the requesting option. Only actual setting keys trigger service reload. |
| App lists | Package shell interpolation, UI-thread work, stale lists and ambiguous removal | Package names are validated, loading runs in the background, lists are searchable, removal is explicit and undoable, and failures remain visible. Essential control packages cannot be suspended. |
| Media | Media lookups leaked executors and could miss completion | Framework media sessions are inspected without creating per-query executors; every query completes. Missing listener access preserves network when music protection is selected. |
| History | Fractional, malformed and incomplete records could crash or mislead; data grew indefinitely | A dedicated parser rejects invalid records and pairs completed sessions. History is capped at 500 events as it is written. |
| External automation | Any installed app could invoke exported control broadcasts without a user-facing opt-in | Tasker broadcasts require the new **Allow Tasker broadcasts** setting. In-app notification controls use a separate private receiver. Package and setting arguments are validated. |
| Log export | `file://` sharing, unbounded logs, missing toolbar and null assumptions | Bounded logs are shared through a narrowly scoped FileProvider URI with temporary access. |
| Interface | Setup and enablement were difficult to distinguish; dated screens and system-bar overlap | A Material 3 dashboard separates access, live status, history and controls. Searchable lists, clear empty/error states, day/night colors, large controls and common system-bar/keyboard insets improve navigation. |
| Build and release | Dynamic/obsolete dependencies, deprecated repositories, missing regression CI, release-script errors | Dependencies are pinned; unused legacy libraries and Jetifier are removed. CI builds debug, minified release and instrumentation APKs, runs unit tests/lint, and defines Android 16 UI checks. Release env accumulation and Fastlane path/precedence issues are corrected. |

## Compatibility scope

| Configuration | Implementation | Device validation |
| --- | --- | --- |
| Android 16 / API 36 | SDK 36 compile/target; edge-to-edge insets, back dispatcher, special-use foreground service, UserService command path | Build checks and six Android 16 emulator instrumentation tests passed. Privileged behavior requires a test phone. |
| Samsung / One UI, Shizuku started through ADB | Named privileged operations, conservative hotspot handling, explicit access/recovery states and Samsung setup guidance | Not certified on any Samsung model or One UI build in this environment. |
| Shizuku started with root | Same UserService protocol; actual privileges come from Shizuku | Requires a rooted test device. |
| Direct root | Explicit user-triggered root check, bounded `su` commands and the same journal | Requires a rooted test device. |
| Legacy ADB grants | Retained for Android 6–13; changed tunables restore their exact prior value | Older-device regression testing remains. Android 14+ directs users to Shizuku or root. |

Shizuku started with wireless debugging has shell privileges, not unrestricted root privileges. One UI can deny individual operations even when Shizuku is connected. A successful core Doze session does not guarantee that every optional sensor, biometric, radio or notification control is supported. Reconnect the original execution mode and use **Retry / restore changes** if restoration remains pending.

Recovery cannot execute while the app is force-stopped or its privileged backend is unavailable. A process death between a system operation and acknowledging its result is ambiguous; retained undo commands intentionally favor restoration. Avoid using multiple automation apps to change the same controls during one session. App suspension and global hardware settings are particularly sensitive to other profiles and device policies; secondary-user and managed-device behavior needs separate validation.

## Setup and behavior changes

1. Start Shizuku, select **Shizuku** in EnforceDoze, and grant EnforceDoze access.
2. Allow EnforceDoze and Shizuku to run in the background. On Samsung, review their battery settings and Sleeping/Deep sleeping app lists; labels vary by One UI version.
3. Enable EnforceDoze while its dashboard is visible. Allow notifications for status/recovery guidance and phone-state access for cellular call detection.
4. Start with optional radio and sensor controls disabled, then test the controls needed on the actual phone. Wi-Fi/airplane changes can interrupt wireless-debugging access on some devices.
5. After reboot, restart an ADB-started Shizuku and return to EnforceDoze. This app cannot silently restart Shizuku or bypass One UI debugging restrictions.
6. Existing Tasker users must enable **Settings → Allow Tasker broadcasts**. Broadcast action names and supported legacy setting aliases remain available. Opting in allows other installed apps to use those broadcasts too.

Other migration details: existing execution mode is preserved; a fresh install defaults to Shizuku. The old `ignoreIfHotspot` choice migrates to the clearer `respectHotspot` preference with equivalent behavior. New UI copy currently falls back to English where translations have not been supplied. Inexact alarms may defer delays and schedule boundaries; the app does not promise second-accurate background scheduling.

## Validation

Local validation succeeded with JDK 17 and the Android 36 SDK, including a clean build:

| Check | Local result |
| --- | --- |
| JVM regressions | 9 tests passed; 0 failures or errors |
| Android lint | 0 errors; warnings remain, including untranslated development UI copy, unused legacy resources and private API compatibility cautions |
| Debug APK | Built successfully |
| Minified release APK | Built successfully; unsigned |
| Instrumentation APK | Compiled and packaged successfully; six tests passed on the GitHub Android 16 emulator at `b04be6b` |
| Native libraries | No `.so` libraries packaged in either app APK; no app-native 16 KB page alignment issue |
| Resource/manifest XML and workflow YAML | Parsed successfully |
| Git diff whitespace check | Passed |

Reproduce the build checks with:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

The first command covers JVM regressions, Android lint, Java/Kotlin/AIDL compilation, debug packaging, release shrinking, and instrumentation-test compilation. The second requires a device or emulator; it passed in [GitHub CI for `b04be6b`](https://github.com/akane599/EnforceDoze/actions/runs/34499273466) but has not been run locally. No connected Android device or KVM emulator is available in this environment.

JVM regressions cover command timeouts, large stderr, bounded output, exit codes, shell quoting/package validation, exact maintenance-state parsing, overnight schedules and malformed/fractional history. Instrumentation tests cover dashboard recreation and screen launch without privileged access, plus persistent recovery, reverse undo order, maintenance restoration and retry after partial failure.

The Fastlane publish lane and signed release workflow were reviewed but not executed: this environment has no Ruby runtime or release signing credentials. The revision is published as [draft PR #1](https://github.com/akane599/EnforceDoze/pull/1). The first GitHub build and six emulator tests passed. A follow-up fixes screenshot collection before emulator shutdown and ensures charging/calls override waiting for unlock; current results are recorded in the PR checks. Physical Samsung validation remains pending. Local debug APKs use a development signing key and generally cannot replace an installed upstream/F-Droid build with a different signature. The local release APK is unsigned unless release credentials are explicitly supplied.

### Phone acceptance checks before stable release

- Android 16 Samsung: authorize, deny and revoke Shizuku; stop/restart Shizuku during entry and restoration; reboot with EnforceDoze enabled. Confirm truthful status and eventual restoration.
- Rapid screen-off/on, lock/unlock, charger connect/disconnect and incoming cellular/VoIP calls, including while waiting for unlock. Confirm no delayed command re-disables a restored control.
- Each optional control independently: begin with it on, begin with it off, enter Doze, exit, enter maintenance, and disconnect access. Verify exact restoration and failure reporting.
- Test active hotspot, music playback, dual SIM, Bluetooth audio, GPS use and work profiles. Verify the relevant protection choices against actual firmware behavior.
- Test overnight and overlapping periods, timezone/clock changes and manual disable. Confirm schedules never enable a manually disabled app.
- Large fonts, TalkBack, light/dark theme, gesture/three-button navigation, landscape and split screen. Inspect all screens and dialogs for clipping or unreachable controls.
- Run an overnight comparison under similar signal, apps and battery conditions. No battery-saving percentage or performance benchmark is asserted by this revision.

## Follow-up: 1.11.1 development revision

- Package metadata now loads on a separate background worker. Opening an app list cannot hold the command queue needed to restore radios and device settings. Search updates the adapter once per query, survives recreation, and shows a retry action when package loading fails.
- App-list headers scroll with their rows, keeping controls and app entries reachable with large text and in landscape. Lists refresh when returning from Android settings, invalid manual package input stays editable, and removal actions identify the app to screen readers.
- Diagnostics yield between reads so screen-on restoration can proceed. Completed reports survive recreation, and permission buttons disappear after permission is granted.
- Android 12+ audio-mode callbacks now trigger evaluation for VoIP calls, including calls starting after screen-off. Call screening and redirected communication modes also pause automation. Samsung dialer and in-call packages are protected from suspension.
- Missing root authorization and unknown execution modes fail explicitly rather than running a command in the app's unprivileged shell.
- CI runs once per PR revision instead of duplicating branch and PR builds. A pinned, checksum-verified Shizuku 13.6.0 is installed only on the disposable Android 16 emulator. Opt-in integration tests cover shell identity, named system API reads, actual settings restoration, forced Doze, server death/reconnection, and foreground monitor screen-off/wake behavior. UI regressions also exercise large text, dark mode, landscape, app loading during a blocked command queue and audio-mode callbacks.

The follow-up's current results are recorded in PR #1. These emulator checks do not establish Samsung firmware compatibility or battery savings; the physical-device acceptance checklist above still applies.

The downloadable 1.11.1 development APK has a new debug certificate because the previous temporary signing key is no longer available. It cannot update the earlier 1.11.0 development APK in place. Before replacing that build, turn EnforceDoze off and resolve any pending restoration while its original privileged backend is connected. Record settings you want to keep, uninstall the old development build, then install and configure this one. Uninstalling clears app data. This is a development-package limitation, not a required migration for a future release signed with the upstream key.

### References for the follow-up

- [Audio-mode callbacks (Android 12+)](https://developer.android.com/reference/android/media/AudioManager.OnModeChangedListener)
- [Pinned Shizuku release](https://github.com/RikkaApps/Shizuku/releases/tag/v13.6.0)
- [Shizuku server runtime-permission and connection implementation](https://github.com/RikkaApps/Shizuku/blob/v13.6.0/server/src/main/java/rikka/shizuku/server/ShizukuService.java)

## Follow-up: saved screen-off evidence (1.11.2 development revision)

A diagnostic run after unlocking reports the current awake state. The old history records a session only after the privileged entry path reads `mState=IDLE`, but its displayed duration measures the automation session, including unsampled time and maintenance. It is not continuous verified Doze time or CPU deep-sleep time.

Open **Doze history → Screen-off evidence**, or the same button in Diagnostics, to inspect new saved observations. A confirmed sample requires a successful `dumpsys deviceidle` read with exact deep state `IDLE`, Android's `PowerManager.isDeviceIdleMode()` true before and after that read, a non-interactive phone at both checks, and no intervening session cancellation. PowerManager observations received during idle-mode broadcasts and after restoration are labelled separately. Records include timestamps, monotonic reading intervals, the session identifier, backend, device, app version and a small subset of the exact system output. Package allowlists are excluded.

Collection reuses existing Doze entry and idle-change reads. It adds no polling alarms, recurring timers, shell queries before restoration, or long-lived wake locks. The latest 120 observations are kept locally; disabling statistics pauses new records. Clear evidence removes the readings, and clearing the main history clears them too. Copy evidence and the diagnostic report include saved readings. Old sessions are not backfilled. Missed broadcasts, process death and gaps between observations remain unobserved; the UI does not infer an idle percentage from those gaps.

The 1.11.2 downloadable APK retains the 1.11.1 development signing certificate and is intended to update that build in place. Current validation is recorded in PR #1, including a live Shizuku screen-off/wake test that checks persisted confirmation and restoration observations, opens the evidence screen after waking and captures its real device readings.

### Android evidence references

- [PowerManager idle and interactive state APIs](https://developer.android.com/reference/android/os/PowerManager#isDeviceIdleMode())
- [Doze maintenance and wake behavior](https://developer.android.com/training/monitoring-device-state/doze-standby)

## Primary references

- [Android 16 behavior changes for apps targeting API 36](https://developer.android.com/about/versions/16/behavior-changes-16)
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Shizuku API and UserService developer guide](https://github.com/RikkaApps/Shizuku-API/blob/master/README.md)
- [Shizuku setup and manufacturer troubleshooting](https://shizuku.rikka.app/guide/setup/)
