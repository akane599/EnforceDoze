# EnforceDoze audit and implementation report

Original source `a5b7c4a`, original APK 1.10.2/86 from the user-supplied release; development branch `codex/android16-reliability`. Source findings below are confirmed execution paths, not claims of hardware validation. User scope explicitly includes redesign and feature improvements.

## Coverage and remaining verification
- Reviewed all main Java/Kotlin activities, service, access helpers, receivers, schedules, media, persistence, parsers and feature controls; replaced obsolete implementations and removed dead helpers/layouts.
- Reviewed manifest, Gradle/dependencies/wrapper, tests, workflow, Fastlane/Gem configuration, README/website/store descriptions, licenses, resource/theme/layout/shortcut structure. Resource XML parses and lint runs. Existing binary artwork/store screenshots retained as historical assets, not current visual evidence.
- Existing translation resources were inventoried and linted; a full linguistic review is NOT complete. New explanatory UI is English. Android accessibility/layout is source-reviewed; actual Android screenshot/accessibility execution remains blocked by emulator boot.
- Baseline APK provenance/certificate/manifest verified; original source debug/release builds pass. Original JVM and instrumentation tests fail compilation; baseline lint has 2 errors/160 warnings.
- Final runtime coverage remains separate: no device tests or Android screen reviews have passed yet. See PROGRESS and docs/DELIVERY for current execution results.

## Confirmed defects and resolutions
| ID | Priority | Evidence in original/follow-up code | Implemented resolution; validation limit |
|---|---|---|---|
| A01 | P0 | Service stored undo in booleans; stop/process loss lost changes; battery saver always restored false | Write-ahead journal, original-mode/user commands, verified undo, retained failures/corrupt records, startup/reconnection recovery. Journal JVM tests pass; Android outcome pending |
| A02 | P0 | Cached Shizuku availability, overwritten listener, no death handling, concurrent/unbounded processes | Live authorization, bounded user-service RPC/processes, serialized transactions; child Binder death reconnects independently. JVM command bounds pass; Binder integration pending |
| A03 | P0 | Exported receivers concatenate unvalidated packages and accept unauthenticated/malformed settings | Opt-in local token, constant-time comparison, package and value validation, protected private alarms. Parser JVM tests pass; actual broadcasts pending |
| A04 | P1 | ENTER/IDLE recorded before command outcome; asynchronous stale/root-only reads; IDLE substring matched maintenance | Separate actual deep-state samples with screen-off checks; monitoring intervals never presented as continuous Doze proof. Parser tests pass |
| A05 | P1 | Timer work survived stop/cycles, executors leaked, up to ten-minute delay wakelock, startup missed entry gates | Event-driven serialized service, generation cancellation, alarm-based delay, bounded transaction wakelock, shared workers; charging/call/screen/schedule/stop gates. Policy tests pass; power/runtime pending |
| A06 | P1 | Hotspot guard reversed, data checked connection not user setting, hardcoded sensor Binder numbers and wrong access mode | Conservative hotspot/media guards, subscription-specific user-data control, named Binder interfaces, original-state readback. OEM execution unverified |
| A07 | P1 | Schedule rewrote manual enablement and startup used unsupported background paths | Manual Stop wins, modern foreground startup, recorded startup failures, exact/inexact alarm disclosure. Schedule policy tests pass |
| A08 | P1 | History invented EXIT without ENTER; charging looked like zero drain; wall-clock duration/shared mutable sets/malformed values | Bounded independent history, monotonic durations, interrupted/missing/charging exclusions, no invented savings. Policy tests pass; storage tests compiled |
| A09 | P1 | Media2 created executors per query and could miss completion/other players | Native session/audio callbacks; unknown state preserves access, playback changes reconcile restrictions. Runtime media behavior pending |
| A10 | P1 | Tunables omitted namespace or reset entire device_idle namespace, discarded originals and assumed success | Per-key supported constants, validated syntax, original-value journal, readback and explicit restore; no namespace reset. Syntax tests pass; actual OEM use unverified |
| A11 | P2 | README/store claimed sensor power-off/savings without evidence | Truthful sensor distinctions, local bounded diagnostics, coarse battery disclosure, rewritten guidance/site/store copy |
| A12 | P2 | JCenter/dynamic libraries, missing JUnit, removed Android test APIs, release defaulted to debug signing | Maven Central/pinned dependencies, current AndroidX test foundation, explicit signing, checksum-pinned Gradle. Debug/minified release/test APKs build |
| A13 | P1 | file:// log sharing crashes modern Android and unnecessarily reads device logs | Local observation text, intentional copy/share/clear, bounded storage, no automatic upload |
| A14 | P2 | NumberPicker ignored rejected values/null restoration; malformed shortcut XML; stale picker/tile state | Validated Material settings/dialogs, corrected shortcut/tile behavior, bounded loading errors and shared package catalog |
| A15 | P1 | Timed-out Binder write could land after undo read the old original and cleared its record | Block later transactions while RPC remains in flight; completion/death signals recovery. Live interruption suite compiled |
| A16 | P2 | Decimal timeout accepted by UI but rejected by Android long parser | Integer-only duration validation and meaningful boundary tests |
| A17 | P1 | Media only sampled on entry, missing playback starting later | Native active-session/playback callbacks and event-driven restore/reconciliation; device behavior pending |
| A18 | P2 | Fixed app-list header could hide controls/list at large fonts | Scrollable list header, bounded errors, shared responsive Material surfaces; actual Android visual review pending |
| A19 | P1 | Release workflow exposed all secrets and Fastlane could push/publish through default paths; nil/incorrect artifact paths | Manual build-only least-privilege workflow; explicit publish/tag/signing guards, no implicit branch push, exact built artifact, hidden signing command. Eight isolated script checks pass; no publishing executed |
| A20 | P1 | New device changes could begin with recovery notifications blocked | Gate new monitoring on permission/app/channel visibility; recovery remains allowed; setup routes to notification settings. Runtime permission UI pending |
| A21 | P1 | allowBackup=false alone may allow OEM device transfer of device-specific undo and tokens | Explicit Android 12+ cloud/device-transfer domain exclusions plus legacy backup disabled; resources/build/lint pass |
| A22 | P1 | Entry could observe maintenance, restore controls, then immediately reapply them | Explicit maintenance guard before optional controls; final-check.log full build passed (runtime pending) |

| A23 | P2 | Hotspot/focused-app queries ran with unused controls; schedule alarms were treated as settings edits and cycled unchanged restrictions | Query only selected controls; preserve active intervals across overlapping schedule boundaries; delivery-verified.log full build PASS; runtime pending |

Earlier running notes reused A13/A14 for later findings. This final table preserves their original log-sharing/picker meanings and assigns those later findings A15/A16.

## Intentional feature changes and tradeoffs
- SensorService restricted app access and developer sensor privacy are separate controls. Android 16 requires a second restrict argument; empty substring would exempt every client. A nonmatching sentinel handles no exemption. Neither control proves physical power-off.
- Hardcoded notification-manager transactions replaced by notification-listener dismissal. Ongoing/call/alarm/media notifications protected; already dismissed notifications cannot be recreated. Disabled radios/app suspension can intentionally prevent delivery.
- Obsolete rotation/brightness manipulation removed after direct SensorService restoration replaced its workaround; avoids changing unrelated preferences.
- Root preserved; legacy ADB mode retained with explicit modern Android limitations. Experimental biometric setting only changes an exposed existing value; hardware effects unverified. Tunable storage verification does not prove OEM behavior.
- Old versions did not persist all original values. Upgrade pauses monitoring for review; missing old originals cannot be reconstructed. Force-stop/uninstall/data deletion limits are disclosed.

## Actual checks and unresolved limits
- Latest completed full check: `backup-rules-build.log`: 24 JVM tests, 0 failures/errors/skips; debug, release and androidTest build PASS; lint 0 errors / 342 warnings. Maintenance/unused-query/UI matrix follow-up also passed in final-check.log. Final schedule-boundary correction also PASSED in delivery-verified.log: 24 JVM tests, lint, debug/release/androidTest. No runtime tests have executed.
- Lint warnings reviewed by class: retained unused translations/artwork and typography dominate; English programmatic UI needs localization, hidden privileged APIs require runtime validation, automation intentionally uses token-authenticated exported receivers, Application-only static context is process lifetime, synchronous journal commits are necessary before mutation. No global lint suppression added to obtain a pass. Existing protected-permission declarations are intentional. Store-policy review of privileged/battery/package-access permissions remains outside installation testing.
- Ruby syntax, eight isolated Fastlane guard/configuration checks, bash syntax, actionlint v1.7.12 and resource XML parsing PASS. Publishing is not exercised; workflow only local, not run on GitHub.
- Website Chromium checks: light/dark 360px, 200% text and 1280px desktop; first large-text run exposed overflow, fixed wrapping, all four reruns PASS. Inspected saved viewport screenshots. These are website evidence, NOT Android app screenshots.
- No KVM/virtualization CPU flags. API 36 Google APIs boot failed to reach app services; emulator37 ATD crashed system_server before any APK install. Emulator35 ATD fallback also failed before installation, exiting139 (SIGSEGV); exact host crash cause unestablished. No failures hidden and no tests skipped to produce a pass.
- **Unverified:** actual API36 Doze entry, sensor-event suppression/restoration, Shizuku Binder/process recovery, Android UI/screenshots, quiet-notification ordering, calls/SIM/hotspot/media, root/legacy modes/older Android, OEM tunables/biometrics, Samsung background management and every physical Samsung behavior. Platform source/documentation informs implementation but does not substitute for those tests.
- No measured battery-saving percentage or claim of continuous monitoring/physical sensor power-off. No remaining confirmed defect is knowingly presented as fixed without source changes; runtime compatibility suspicions remain open until execution.

## Next action
Final build passed. Commit app/test follow-up, refresh signed APKs/reports, finalize DELIVERY/PR and PROGRESS records, then commit delivery. All remote actions still require explicit approval.

## Primary platform basis
- [Android Doze](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Android Sensors off](https://source.android.com/docs/core/interaction/sensors/sensors-off)
- [Backup and OEM device-transfer behavior](https://developer.android.com/identity/data/autobackup)
- [Shizuku setup](https://shizuku.rikka.app/guide/setup/)
- [Samsung application management](https://developer.samsung.com/mobile/app-management.html)
- AOSP Android 16 SensorService, DeviceIdleController, ISensorPrivacyManager, ITelephony and TelephonyShellCommand sources inspected; task-local copies retained in verification workspace.

## Enhancements not required for this delivery
Full localization, on-device power profiling, broader OEM/device coverage and future dependency/API modernization are follow-up opportunities. They must not be confused with the runtime validation still required above.
