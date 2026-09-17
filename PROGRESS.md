# EnforceDoze development handoff

## Agreement and baseline
- Goal: reliable Android 16/API 36 application, Shizuku primary, safe restoration, truthful evidence/history, practical modern accessible UI, tested debug and release APKs and phone-started build workflow.
- Starting source: `a5b7c4a` (1.10.2/86), clean detached checkout. Development branch: `codex/android16-reliability`.
- Candidate starting APK: `/tmp/enforcedoze-baseline/app-release.apk`, 1.10.2/86, API 36; SHA-256 `7fad1a82ee0f5a6af86b7cb20879c4d4ad2ba26bf462b52cb10b6360719a4a75`. User asked to confirm provenance asynchronously; no supplied attachment in this turn. Existing outside-worktree builds/logs are NOT evidence of this work.
- Local changes, dependencies, emulators and commits authorized. All GitHub writes, pushes and Actions runs require explicit approval after local validation. None requested or performed.
- No physical Samsung available; document hardware limits, do not request phone testing. No claims of sensor power-off or battery-saving percentages.

## Current stage
1. Baseline/audit underway. Major sources, preferences, manifest, initial tests and build reviewed; findings in AUDIT_REPORT.md. Original tests are placeholders and JUnit dependency is absent.
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
Begin stage 2: implement bounded access and test foundation plus durable restoration/evidence. Collect pending baseline `assembleDebug assembleRelease lintDebug assembleDebugAndroidTest --continue` results from `/tmp/enforcedoze-79dd/baseline-checks.log`; API 36 AVD still booting offline. Continue detailed activity/resource review alongside affected stages.

## Baseline stage result
- Source debug APK built. Unit suite cannot compile (missing JUnit). Remaining release/lint/androidTest results pending.
- Candidate APK certificate SHA-256: `50c3301e181970be7d50b4cd51e44060db6876ebf6adb02e48336db4048e5757` (CN=MG). No publisher key provided or sought.
- Confirmed additional issues: Shizuku bypassed in whitelist UI; DozeTunableHandler device_config commands omit namespace; tunables report success without results.
- Reviewed release workflow/Fastlane: manual workflow has publishing and push paths and exposes all secrets to environment; do not execute. Prepare separate least-privilege build-only workflow.
