# EnforceDoze development handoff

## Agreement and original baseline
- Goal: Android 16/API 36, Shizuku primary, safe restoration, accurate evidence/history, modern accessible UI, tested debug/release APKs and phone-started build workflow. Preserve supported root/ADB modes and disclose limits.
- Original source: clean detached `a5b7c4a`, version 1.10.2/86. Current branch: `codex/android16-reliability`.
- User-provided APK: https://github.com/Akylas/EnforceDoze/releases/tag/v1.10.2%2F86. Fresh download `/tmp/enforcedoze-79dd/baseline-artifacts/provided-app-release.apk`; SHA256 `7fad1a82ee0f5a6af86b7cb20879c4d4ad2ba26bf462b52cb10b6360719a4a75`. Certificate SHA256 `50c3301e181970be7d50b4cd51e44060db6876ebf6adb02e48336db4048e5757` (CN=MG). Identical to previously found candidate; identity verified rather than assumed.
- Task-related local changes, dependencies, disposable emulators and commits authorized. Every GitHub write/push/Actions run requires explicit approval after concrete local validation. NONE requested or performed. No publisher key supplied.
- No Samsung hardware available; do not request phone testing or claim sensor power-off/battery savings. Existing unrelated /tmp artifacts are not this run's evidence.

## Completed focused stages
| Commit | Work | Actual verification |
|---|---|---|
| `6820491` | Original baseline/audit | Original debug/release assemble pass; JVM tests fail compilation (missing JUnit); androidTest fails compilation (removed android.test); lint 2 errors/160 warnings |
| `9d591ae` | Bounded Shizuku user service, serialized commands, write-ahead restoration, evidence/test foundation | 14 JVM tests; debug/androidTest assemble pass |
| `5763658` | Event-driven Doze lifecycle, truthful observations/history, optional features, secure automation | 18 JVM tests, debug, lint pass |
| `94098c7` | Material dashboard/settings/setup/diagnostics, safe tunables/reset, feature disclosures | 18 JVM tests, debug, lint pass; visual runtime still pending |
| `832d577` | Late-RPC recovery, upgrade review, media callbacks, native integration/UI suites | 24 JVM tests, debug/androidTest, lint pass; device tests compile only |
| Current stage | Recovery-notification gating; explicit child Binder death/rebind; feature-list disclosures | Full build PASS in `/tmp/enforcedoze-79dd/delivery-build.log`: 24 JVM tests (0 failures/errors/skips), lint zero errors, debug/release/androidTest APKs |

## Current state and decisions
- Version 1.11.0-dev/87, min 23 / target+compile 36. Release unsigned until external signing; no implicit debug signing. Temporary settings use durable original-value journal and readback. Unknown/failed access retains pending recovery. Deep Doze evidence is sampled actual IDLE with screen-off checks, not session duration.
- SensorService restriction uses Android 16's required second argument with nonmatching exemption sentinel. Sensor privacy is a separate named Binder API; no hardcoded transaction IDs. Both report capability/readback failure. Root and legacy ADB remain with documented limitations.
- New UI text is English; retained translations have not received a full linguistic audit. No runtime UI/sensor/Shizuku passes or screenshots yet.
- Uncommitted delivery work: manual-only build workflow (old unsafe release workflow removed), signing helper, README/website/store copy, BUILDING/TESTING docs, Gradle checksum/repository cleanup, Fastlane fixes. Actionlint/Ruby/bash/XML checks passed before final Fastlane follow-up. No publishing lane executed.
- Local testing signing key generated outside repository at `/home/dev/.local/share/enforcedoze-79dd/signing/local-testing.jks`; passwords private beside it. Never commit/copy keys or passwords into artifacts. Signing/copy of final APKs still pending.

## Emulator execution blocker and active fallback
- Host has no KVM and CPU exposes no vmx/svm; not a permission problem. Software emulation only, 3.7GB RAM.
- First API 36 Google APIs AVD never reached activity service after ~24 minutes; logs saved, only own AVD deleted.
- Official API 36 ATD on emulator 37.1.11 reached activity/package but refused baseline install as still booting, then system_server SIGSEGV in NetworkWatchlist and watchdog/zygote restart. This happened BEFORE any APK installed. Logs: `/tmp/enforcedoze-79dd/{emulator-atd.log,atd-crash.txt,baseline-install.log}`. No tests passed/skipped. Stopped that emulator.
- Now trying isolated official emulator 35.6.11, same own ATD AVD `enforcedoze_79dd_atd36`, port 5580, PID 58301 (verify), one core from launch, software graphics. Log `/tmp/enforcedoze-79dd/emulator35-atd.log`. Still booting at last check (~7 minutes). Archive SHA256 verified against official archive; SDK installation unchanged.
- `adb unroot` REQUIRED before Shizuku; integration requires shell UID2000. Own `tools/prepare-shizuku.sh` refuses root/non-emulator. Official Shizuku13.6 APK `/tmp/enforcedoze-79dd/shizuku.apk`, SHA256 `6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f`.

## Exact next action
Commit the verified notification/Binder follow-up with this record, then re-read this file. Finish bounded Fastlane fixes and delivery validation while monitoring the fallback boot. If it boots, smoke/install supplied baseline first, then own debug/test APKs and run UI + real shell-Shizuku suites; inspect screenshots (light/dark/large font/landscape/error/recovery). Investigate failures without weakening assertions. If boot remains impossible, preserve exact environment failure and explicitly mark all runtime/visual checks unexecuted.

Then sign/copy debug and non-debug APKs to ignored `artifacts/`, verify manifests/certificates/hashes/R8 entry points, prepare final verification/PR docs, finalize AUDIT_REPORT and focused local delivery commit. GitHub workflow must reach default branch `master` via approved publication/merge before phone Run workflow UI. No remote approval outstanding; no remote action authorized yet.
