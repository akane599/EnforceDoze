# Repository audit

Baseline: `a5b7c4a`; reviewed findings below are source-path evidence, not claims of runtime verification. User explicitly authorizes UX/features as well as defects.

## Coverage
- [x] Inventory: 8,135 Java/Kotlin lines, Android resources, Gradle, manifest, two placeholder tests.
- [x] Initial privileged access/service/restoration/media/schedule/history review.
- [x] Full activity, picker, tunables, adapters and receiver source review (UI runtime pending).
- [ ] All resources/locales, accessibility, documentation and release scripts.
- [ ] Baseline build/APK/runtime; regression tests and final validation.

## Prioritized findings
| ID | Priority | Confirmed evidence | Status |
|---|---|---|---|
| A01 | P0 | ForceDozeService stores restoration state in booleans only; exitDoze/onDestroy omit leaveDozeHandleNetwork; battery saver restoration always false. Process loss/stop can leave changes active. | Implemented; runtime validation pending |
| A02 | P0 | ShizukuHandler caches binder/authorization, one listener can overwrite another, no binder death/receive handling, concurrent commands and unbounded pipe reads/wait. | Implemented; runtime validation pending |
| A03 | P0 | Exported whitelist receivers concatenate arbitrary packageName into shell command; settings receiver accepts malformed integers; automation has no opt-in or authorization. | Implemented; runtime validation pending |
| A04 | P1 | enterDoze marks IDLE and records ENTER before command outcome. getDeviceIdleState asynchronously returns stale state and only queries a root session in Shizuku mode; IDLE substring shadows IDLE_MAINTENANCE. | Implemented; runtime validation pending |
| A05 | P1 | Delayed Timer work survives stop/overlapping cycles, repeated Timer/executor leaks, delay holds up to 10-minute wakelock. Startup bypasses charging/call gates. | Implemented; runtime validation pending |
| A06 | P1 | Network guard `(ignoreIfHotspot || !wasHotSpotTurnedOn)` reverses protection. Mobile data tests connection state, not user enablement. Sensor privacy uses hardcoded Binder transaction and SU even in Shizuku mode. | Implemented; runtime validation pending |
| A07 | P1 | applyForceDozeSchedule ignores serviceEnabled and rewrites manual intent; startService used on API 26–30; background FGS starts can throw. | Implemented; runtime validation pending |
| A08 | P1 | Stats invent EXIT without ENTER, charging represented as 0%, wall-clock duration, malformed values crash; data mutated as shared preference Set, trim only on screen visit. | Implemented; runtime validation pending |
| A09 | P1 | Media2 creates executor per query; can never call completion, inspects only last media notification. | Implemented; runtime validation pending |
| A10 | P1 | Nonroot Android 14+ path resets entire device_idle namespace; loses original tunables and assumes local shell can write. | Implemented; runtime validation pending |
| A11 | P2 | README claims motion sensors disabled and battery savings without evidence; no local persisted observations. | Open |
| A12 | P2 | Build uses jcenter and dynamic libsuperuser; tests placeholders, missing JUnit; release defaults to debug signing. | Open |

## Validation
- Baseline unit compilation FAILED (4 errors: missing org.junit); debug assembled; release/lint/androidTest follow-up running; log `/tmp/enforcedoze-79dd/baseline-build.log`.
- Baseline APK provenance confirmed from the user-supplied GitHub release; current actual test results follow below.
- SDK 36/emulator installed. No KVM or Samsung hardware. Disposable AVD boot under investigation.

## Decisions and remaining queue
Prioritize durable write-ahead restoration, bounded serialized commands, actual observed state and safe cancellation before UI redesign. Preserve supported root/nonroot modes with capability checks. Investigate modern replacements for Binder-number hacks; disclose unsupported OEM functions. Initial baseline stage complete. Next: stage 2 access/journal/test foundation, collect pending baseline checks. Detailed activity/resource review continues during relevant stages.

Additional confirmed: tunable commands omit device_idle namespace, whitelist UI bypasses Shizuku, release/Fastlane can publish/push even through surprising branches. No remote work executed.

## Stage 2 verification
- 14 meaningful JVM tests passed, debug and androidTest APKs built (no skipped tests). Test XML in app/build/test-results/testDebugUnitTest. Integration not yet exercised.
- A01: journal implementation complete, service integration pending. A02: live user-service transport replaces cached/reflected process access; runtime pending. A12: dependencies/test runners repaired, pinned library and Maven Central; external signing now explicit. Two baseline lint errors fixed; broader lint pending.
- Additional A06 evidence: Android 16 SensorService.cpp `changeOperatingMode` rejects restrict without a second argument. Controller supplies safe nonempty exemption sentinel.
- Baseline actual: debug/release assemble passed; JVM tests failed compilation (JUnit absent); instrumentation failed compilation (removed android.test); lint 2 errors/160 warnings.
- Starting APK freshly downloaded from user release link; identical SHA-256 to candidate, publisher certificate confirmed.

## Stage 3 review notes
- Replaced service timers and speculative state with serialized, invalidatable event handling and durable undo. Added actual Deep Doze samples, bounded session history, native media state reads, conservative hotspot guard and subscription-specific data control.
- Removed unsafe notification-manager Binder-number calls; replacement uses notification-listener suppression, preserving ongoing/call/alarm/media notifications. Cancelled notifications cannot be recreated; disclosure required in redesigned settings.
- Automation now requires opt-in and token; malformed inputs and package injection rejected. Restart/schedule paths preserve manual enabled state and catch Android background-start restrictions.
- First stage 3 run: 18 unit tests and debug assembly passed; lint caught three API-23 compatibility errors. Fixed through API-23-compatible local callback interfaces and SDK guard. Final rerun PASSED: 18 tests, debug assembly, lint zero errors.
- Further confirmed UX issues queued for stage 4: Settings probes root even in Shizuku mode, resets options on transient access loss, can clear state/revoke privileges before recovery; quick tile delayed callbacks can overwrite a newer toggle; package pickers/blocklists have indefinite loading/empty-state errors; whitelist mutates on UI thread and assumes valid command output.

- New confirmed A13 (P1, queued): LogActivity uses file:// sharing (FileUriExposedException on modern Android), reads full device log unnecessarily, null log can crash share. Replace with local evidence + explicit text sharing.
- New confirmed A14 (P2, queued): NumberPicker ignores rejected preference change and restores into null picker; shortcut XML has stray text. Replace/fix in UI stage.

## Stage 4 verification
- Material dashboard/access/settings/evidence/history and all feature screens replaced; originals retained for tunables; reset restores both journals. Legacy notification Binder functionality replaced with documented listener filtering; obsolete display toggle removed with explanation.
- Final stage 4 unit suite (18 tests), debug build and lint PASS, zero lint errors; initial orphan XML-handler errors fixed by deleting unused layouts. UI has not yet been visually reviewed.
- Emulator limitation: first Google APIs API 36 boot did not reach activity service after ~24 minutes without KVM. Trying official Google ATD API 36; no device tests passed yet.
- Remaining: real emulator UI/Shizuku/sensor outcomes, remaining helper cleanup, resource/documentation/release audit, CI workflow and final APKs.

## Stage 5 findings and work in progress
- A13 (confirmed): timed-out Binder writes could still land after recovery reads the original value. Shizuku now blocks subsequent transactions while the prior call remains in flight, and signals recovery when it completes.
- A14 (confirmed): a decimal timeout such as `1.0` passed numeric UI validation but is not accepted by Android's long parser. Fixed integer syntax; regression tests added.
- A15 (confirmed): entry-only media snapshots miss playback starting while screen is off. Replaced with native active-session/playback callbacks, conservative unknown state and event-driven service reconciliation. Runtime pending.
- A16 (confirmed): large-font app-list actions and long access errors could consume the fixed header and hide the list. Actions now scroll in a ListView header; errors bounded. Visual review pending.
- Latest completed check: 24 JVM tests, debug/androidTest APK builds and lint PASS, `/tmp/enforcedoze-79dd/stage5-followup.log`. Latest stage5-current.log also PASS for 24 tests, both APK builds and lint. No emulator tests have executed.
- Resource review: stale layouts/styles removed; old unused translation resources and historical screenshots retained. Lint largely reports unused resources/typography, plus documented privileged APIs. New UI explanatory copy is English; linguistic completeness of translations is not claimed.
- Remaining audit/release queue: website claims, safe manual build workflow, Fastlane publishing guards, final manifest/resource checks and runtime results; actual visual review and Samsung hardware limitations must remain separate.

## Latest validation / environment finding
- Full build including minified unsigned release PASSED in final-build.log; 24 JVM tests and lint pass. Latest notification/child-Binder follow-up also PASSED the full build in delivery-build.log (24 JVM tests, zero failures/errors/skips; debug/release/androidTest; lint zero errors).
- API 36 ATD failed before any app installation: system_server native SIGSEGV (NetworkWatchlist) and watchdog/zygote restart under software emulation; baseline install explicitly rejected as still booting. This is an environment/platform failure, not a passing or skipped EnforceDoze test. Investigating official older emulator fallback; no physical Samsung available.
- Manual APK workflow passes actionlint; Ruby/scripts and resource XML parse checks pass. Workflow remains local/unrun and final signing is pending.

- Latest follow-up fixes gate new automatic changes on visible recovery notifications and explicitly reconnect a dead child user service even while the Shizuku manager remains alive. Sixth Shizuku regression test compiles. Actual device execution remains blocked by emulator boot.
