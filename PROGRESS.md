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
| `659a318` | Exclude device-specific recovery/token data from cloud/device transfer | Full backup-rules-build.log PASS: 24 JVM tests, debug/release/androidTest; lint 0 errors/342 warnings |
| `8a946be` | Recovery-notification gating; explicit child Binder death/rebind; feature-list disclosures | Full build PASS in `/tmp/enforcedoze-79dd/delivery-build.log`: 24 JVM tests (0 failures/errors/skips), lint zero errors, debug/release/androidTest APKs |

## Current state and decisions
- Version 1.11.0-dev/87, min 23 / target+compile 36. Release unsigned until external signing; no implicit debug signing. Temporary settings use durable original-value journal and readback. Unknown/failed access retains pending recovery. Deep Doze evidence is sampled actual IDLE with screen-off checks, not session duration.
- SensorService restriction uses Android 16's required second argument with nonmatching exemption sentinel. Sensor privacy is a separate named Binder API; no hardcoded transaction IDs. Both report capability/readback failure. Root and legacy ADB remain with documented limitations.
- New UI text is English; retained translations have not received a full linguistic audit. No runtime UI/sensor/Shizuku passes or screenshots yet.
- Delivery stage prepared and validated: manual-only build workflow (old unsafe release workflow removed), signing helper, README/website/store copy, BUILDING/TESTING docs, Gradle checksum/repository cleanup, Fastlane fixes. Final Fastlane changes passed eight isolated guard/configuration checks (no remote effects), Ruby syntax and actionlint/bash checks. No publishing lane executed.
- Local testing signing key generated outside repository at `/home/dev/.local/share/enforcedoze-79dd/signing/local-testing.jks`; passwords private beside it. Never commit/copy keys or passwords into artifacts. Final APKs/certificates refreshed after delivery-verified build; signature, alignment, SDK/version/debug flags and R8 entry points verified. Release local cert SHA256 83753419ab026979391c9683b11cc0deaf50c0674580c59a15b36edd89ebf0a6; debug cert 93e78296c5eb4a5a2970679e96454de0c6979918c8f248765fddc1eb87ace8dd. Neither matches the original publisher.

## Emulator execution blocker (attempts ended)
- Host has no KVM and CPU exposes no vmx/svm; not a permission problem. Software emulation only, 3.7GB RAM.
- First API 36 Google APIs AVD never reached activity service after ~24 minutes; logs saved, only own AVD deleted.
- Official API 36 ATD on emulator 37.1.11 reached activity/package but refused baseline install as still booting, then system_server SIGSEGV in NetworkWatchlist and watchdog/zygote restart. This happened BEFORE any APK installed. Logs: `/tmp/enforcedoze-79dd/{emulator-atd.log,atd-crash.txt,baseline-install.log}`. No tests passed/skipped. Stopped that emulator.
- Isolated official emulator 35.6.11 was tried with own ATD AVD `enforcedoze_79dd_atd36`, port5580, one core/software graphics. Log `/tmp/enforcedoze-79dd/emulator35-atd.log`. Fallback exited 139 (SIGSEGV) before boot completion after approximately 19 minutes. system_server appeared, but no usable activity/package service or installed app. Exact crash cause unestablished; outcome saved in emulator35-outcome.txt. No emulator now running. Archive SHA256 verified against official archive; SDK installation unchanged.
- `adb unroot` REQUIRED before Shizuku; integration requires shell UID2000. Own `tools/prepare-shizuku.sh` refuses root/non-emulator. Official Shizuku13.6 APK `/tmp/enforcedoze-79dd/shizuku.apk`, SHA256 `6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f`.

## Final local delivery state
- `6f6b35b`: maintenance guard, conditional hotspot/focus queries, schedule/delay reconciliation, 7 UI/storage +6 Shizuku tests compiled. Final delivery-verified.log exit0: 24 JVM tests, no failures/errors/skips, lint0errors/342warnings, debug/minified release/androidTest builds PASS.
- Delivery commit (this stage): manual-only workflow, explicit signing helper, eight passing isolated release-script checks, wrapper checksum/dependency-source cleanup, README/site/store/build/test/delivery/PR docs. Actionlint, Ruby/bash syntax, 45 resource XMLs +manifest and diff checks PASS. No real Fastlane publication executed.
- Final APKs: `artifacts/enforcedoze-1.11.0-dev-{release,debug}.apk`; test companion, SHA256SUMS, public certificates, BUILD-INFO and verification reports alongside. Release SHA256 `3e967242ddedf9a45e92aacac59505af2607ab058f6c1f2bd9363eac73366fe7`; debug `13624933413caba25114f81002b851bf53aa8ba8b80a1d346f62595e32503f1c`. Artifacts ignored, private keys outside repo.
- Website Chromium light/dark360px, 200%text and desktop1280px checks PASS; saved screenshots inspected after overflow fix. ZERO Android device tests executed; no Android app screenshots. Emulator logs/cancellation investigation preserved under artifacts/verification. Last transient Gradle client cancellation was followed by two passing full builds.
- Signing differs from supplied publisher and between debug/release; cannot update each other. Restore before uninstall/data deletion. See docs/BUILDING.md and docs/DELIVERY.md.

## Exact next action / outstanding approvals
Local delivery is ready for review. First re-read this file, git status/recent commits/diff before any resumed implementation. On a working accelerated disposable API36 emulator, execute the 13 prepared device tests and inspect app light/dark/large-font/landscape/loading/error/recovery screens. Investigate failures without weakening tests. Samsung hardware, root/legacy modes and real modem/biometric/sensor-HAL behavior remain unverified; do not request user's phone.

No GitHub writes or Actions runs requested/authorized/performed and no approval pending. Prepared PR description: docs/PULL_REQUEST.md. Workflow: .github/workflows/build-apk.yml, workflow_dispatch only (push/PR does not trigger it). Any publication needs explicit approval for exact scope. Workflow must reach default branch master through approved publication/merge before phone Run workflow UI; merging and Actions execution are separate approvals. Do not call the app runtime-validated until the blocked checks run.
