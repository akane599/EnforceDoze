# EnforceDoze development handoff

## Agreement and baseline
- Goal: reliable Android 16/API 36 application, Shizuku primary, safe restoration, truthful evidence/history, practical modern accessible UI, tested debug and release APKs and phone-started build workflow.
- Starting source: `a5b7c4a` (1.10.2/86), clean detached checkout. Development branch: `codex/android16-reliability`.
- Starting APK confirmed from user-provided release https://github.com/Akylas/EnforceDoze/releases/tag/v1.10.2%2F86; freshly downloaded to `/tmp/enforcedoze-79dd/baseline-artifacts/provided-app-release.apk` and identical to candidate. Candidate path: `/tmp/enforcedoze-baseline/app-release.apk`, 1.10.2/86, API 36; SHA-256 `7fad1a82ee0f5a6af86b7cb20879c4d4ad2ba26bf462b52cb10b6360719a4a75`. Hash and publisher certificate verified against fresh release download. Existing outside-worktree builds/logs are NOT evidence of this work.
- Local changes, dependencies, emulators and commits authorized. All GitHub writes, pushes and Actions runs require explicit approval after local validation. None requested or performed.
- No physical Samsung available; document hardware limits, do not request phone testing. No claims of sensor power-off or battery-saving percentages.

## Current stage
1. Baseline stage committed as `6820491`; stage 2 foundation complete (commit below). Major sources, preferences, manifest, initial tests and build reviewed; findings in AUDIT_REPORT.md. Original tests are placeholders and JUnit dependency is absent.
2. Baseline command FAILED: missing `org.junit` (4 compile errors). Command: `ANDROID_HOME=/home/dev/android-sdk ./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug --console=plain`; log `/tmp/enforcedoze-79dd/baseline-build.log`.
3. Disposable API 36 AVD `enforcedoze_79dd_api36` launched on port 5580, software emulation (no `/dev/kvm`). No device tests passed yet.

## Planned focused stages
1. Complete baseline evidence and initial prioritized audit; commit record.
2. Build/test foundation, access executor, durable restoration and evidence; verify and commit.
3. Event-driven service, optional features, safe scheduling/automation and accurate history; verify and commit.
4. Dashboard/settings/diagnostics redesign and accessibility; verify and commit.
5. API 36 integration, interruption/sensor/UI tests, remaining audit and fixes; commit.
6. Final APKs, manual build workflow, delivery/PR documentation, final report; commit. Ask only for concrete optional remote publication.

## Exact next action
Begin stage 3: replace ForceDozeService timers/booleans with event-driven serialized state transitions and the recovery journal; integrate truthful sessions and optional feature controls. Re-read this record before implementation. API 36 AVD is still offline under software emulation; investigate startup without blocking local work.

## Baseline stage result
- Source debug APK built. Unit suite cannot compile (missing JUnit). Original release and debug assemble succeeded. Lint failed: 2 errors/160 warnings (suspicious indentation, app:tint). androidTest compilation failed: removed android.test.ApplicationTestCase. Original logs retained.
- Candidate APK certificate SHA-256: `50c3301e181970be7d50b4cd51e44060db6876ebf6adb02e48336db4048e5757` (CN=MG). No publisher key provided or sought.
- Confirmed additional issues: Shizuku bypassed in whitelist UI; DozeTunableHandler device_config commands omit namespace; tunables report success without results.
- Reviewed release workflow/Fastlane: manual workflow has publishing and push paths and exposes all secrets to environment; do not execute. Prepare separate least-privilege build-only workflow.

## Stage 2 result
- Added Shizuku user service with live binder/permission listeners, bounded process output/deadline and shared command queue; explicit mode selection without silent fallback.
- Added write-ahead journal, verified restoration, bounded local observations, state parsers and device-controller primitives. Service integration remains stage 3; do not yet claim application recovery works end to end.
- Added proper JUnit/AndroidX test foundation, pinned libsuperuser, replaced JCenter, explicit unsigned release unless external signing. Fixed two baseline lint errors.
- Validation: `testDebugUnitTest assembleDebug assembleDebugAndroidTest --max-workers=2` PASSED; 14 unit tests, zero failures/skips. `/tmp/enforcedoze-79dd/stage2-build.log`. No device tests run yet.
- AOSP Android 16 SensorService source confirms `restrict` REQUIRES a second argument; an empty whitelist substring matches every client. New controller uses a nonmatching sentinel and checks `Mode : RESTRICTED`.
- Remaining: service integration, legacy screens/access callers, full feature review, user-service runtime tests, RPC queue bounding, release signing/artifacts, UI/CI/docs.
