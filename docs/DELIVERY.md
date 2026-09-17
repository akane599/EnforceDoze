# Local development delivery

This is a reviewable development build, not a claim of completed hardware validation. The original source and supplied v1.10.2/86 APK were treated as the baseline. Branch: `codex/android16-reliability`; original revision `a5b7c4a`. Focused commits and the next action are in [PROGRESS.md](../PROGRESS.md); findings and coverage in [AUDIT_REPORT.md](../AUDIT_REPORT.md).

## Artifacts and signing

Generated files are under ignored `artifacts/` at the repository root:

- `enforcedoze-1.11.0-dev-release.apk`: optimized, non-debuggable, signed with this work's local development key.
- `enforcedoze-1.11.0-dev-debug.apk`: debuggable, signed with this environment's Android debug key.
- `enforcedoze-androidTest.apk`: instrumentation companion for the debug build.
- `SHA256SUMS.txt`, public `*-certificate.txt`, `BUILD-INFO.txt` and `verification/`: exact identities, build/test reports and emulator failure evidence.
- `website-review/`: website screenshots and checks, explicitly **not Android application screenshots**.

Application ID `com.akylas.enforcedoze`; version 1.11.0-dev/87; minimum23, compile/target36. Baseline already targeted36; this work changes implementation behavior, not merely the target number.

| APK identity | Certificate SHA-256 |
|---|---|
| Supplied 1.10.2/86, CN=MG | `50c3301e181970be7d50b4cd51e44060db6876ebf6adb02e48336db4048e5757` |
| Local release, CN=EnforceDoze Local Development | `83753419ab026979391c9683b11cc0deaf50c0674580c59a15b36edd89ebf0a6` |
| Local debug, CN=Android Debug | `93e78296c5eb4a5a2970679e96454de0c6979918c8f248765fddc1eb87ace8dd` |

These three certificates differ. **Neither delivered APK can update the supplied original, and debug/release cannot update each other.** Same-key subsequent builds can update when Android's version rules permit. Stop/restore first before uninstalling an incompatible installation; uninstalling deletes options/history/recovery records. No publisher private key was supplied or reconstructed. The local development key is retained outside Git and is not in artifacts. CI temporary keys are unique per run and cannot update another run. See [BUILDING.md](BUILDING.md) for phone download/signing instructions.

## Final APK SHA-256

```text
13624933413caba25114f81002b851bf53aa8ba8b80a1d346f62595e32503f1c  enforcedoze-1.11.0-dev-debug.apk
3e967242ddedf9a45e92aacac59505af2607ab058f6c1f2bd9363eac73366fe7  enforcedoze-1.11.0-dev-release.apk
1010471a004c48c8b117b5545f1401398f8a05ae09d93d3a7c319f21dcd28fb6  enforcedoze-androidTest.apk
```

## Executed checks

Final full build PASSED in `artifacts/verification/delivery-verified.log` (exit0, 2m24s). APKs were freshly copied/signed afterward; signatures, alignment, version/SDK/debuggable flags and retained privileged entry points were checked. These are installable signed APKs, but installation/runtime could not be tested here.

| Check | Result |
|---|---|
| Original source debug/release builds | PASS |
| Original JVM / instrumentation | FAIL compilation: JUnit missing / removed android.test API |
| Original lint | FAIL: 2 errors, 160 warnings |
| Current completed JVM checks | 24 tests, 0 failures/errors/skips |
| Current completed lint | 0 errors, 342 warnings; no blanket suppression |
| Debug, minified release, androidTest compilation | PASS for final maintenance/efficiency/schedule follow-up |
| Fastlane isolated guard/configuration checks | PASS, 8 checks; no real publishing/build action invoked |
| Workflow actionlint, Ruby/bash syntax, resource XML | PASS |
| Website Chromium checks and screenshot review | PASS light/dark 360px, desktop 1280px, 200% text; overflow found and fixed before rerun |
| Android UI / Shizuku / sensors / repeated cycles | NOT EXECUTED: emulator could not complete boot |
| Physical Samsung, root/Magisk, legacy/older Android | NOT TESTED: no corresponding devices/environments |
| GitHub Actions workflow | Prepared and statically checked; NOT RUN (no approval/request to run) |

Warnings mostly concern retained unused resources/translations and typography. Additional categories cover localization, supported privileged reflection, deliberate token-protected exported receivers and distribution-policy-sensitive permissions. Full XML/HTML lint reports are included. No claim of zero warnings, complete localization, measured battery savings or eliminating every bug.

## Emulator failure investigation

The host has no `/dev/kvm` and no vmx/svm CPU flags. This is not a missing chmod permission. Disposable attempts:

1. Official API36 Google APIs image, emulator37.1.11: no usable activity service after about 24 minutes; own AVD removed after logs were retained.
2. Official API36 Google ATD image, emulator37.1.11: activity/package briefly appeared, but baseline installation was rejected as still booting. system_server then had a native SIGSEGV in NetworkWatchlist and watchdog/zygote restart. No EnforceDoze APK was installed.
3. Isolated official emulator35.6.11, same ATD image, one software-emulated CPU and software graphics: boot remained incomplete, then emulator session exited 139 (SIGSEGV) after about 19 minutes. Exact host crash cause is unestablished. Logs/outcome retained; no app installed.

No watchdog or test assertions were disabled. A subsequent build client also disconnected (exit 143); its daemon recorded cancellation, not a compiler defect. That build was rerun independently and its final outcome is recorded above. Neither an APK compilation nor these boot failures is counted as a passed/skipped Android test.

Prepared instrumentation currently covers storage corruption/history/evidence/automation, 14 screen launches, theme/portrait/landscape/navigation and large text, plus real UID2000 Shizuku screen cycles/Doze/readback, stable notification post time, interrupted delay/charging, accelerometer suppression/restoration, sensor privacy, manager death/restart and child-service death. Compilation does not establish those outcomes. [TESTING.md](TESTING.md) gives exact commands and prerequisites.

## Remaining risks and unverified behavior

- Android app screenshots, clipping/overlap, loading/error/recovery interactions, actual API36 Doze entry and sensor-event outcomes are unverified here. Website visual checks do not substitute for app review.
- Samsung One UI background limits/Auto Blocker, real calls/VoIP delivery, SIM/data/hotspot/Bluetooth/location, media transitions, sensor HAL and biometric behavior need runtime evidence. OEM hidden-interface compatibility can fail; app shows unavailable controls and retains pending undo.
- Root/Magisk and legacy ADB/older Android paths remain implemented but have no runtime passes in this environment.
- Force-stop blocks recovery until reopening; uninstall/clear-data deletes the journal. Unknown original values from the old app cannot be recreated. Other software changing the same setting during a session can conflict with restoring the captured original.
- Per-value tunable readback establishes stored values only. SensorService restriction establishes app-access mode, not every physical sensor's power state. Deep Doze samples are point observations, not continuous residence.
- New UI text is English; existing translated assets have not had a complete linguistic review. Historical store screenshots are retained but are not current-design evidence. No power profiling/savings percentage has been measured.

## GitHub publication state

Everything is local. No push, PR, comments, merge, releases, repository settings change or Actions run occurred. The ready description is [PULL_REQUEST.md](PULL_REQUEST.md) and the manual-only workflow is [build-apk.yml](../.github/workflows/build-apk.yml). Pushing this branch would not itself trigger the new workflow. Any requested remote publication or execution still needs explicit approval, with its exact scope identified; merging is separate from pushing a draft PR.
