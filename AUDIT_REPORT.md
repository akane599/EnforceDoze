# Repository audit

Baseline: `a5b7c4a`; reviewed findings below are source-path evidence, not claims of runtime verification. User explicitly authorizes UX/features as well as defects.

## Coverage
- [x] Inventory: 8,135 Java/Kotlin lines, Android resources, Gradle, manifest, two placeholder tests.
- [x] Initial privileged access/service/restoration/media/schedule/history review.
- [ ] Full activity, picker, tunables, adapters and receiver review.
- [ ] All resources/locales, accessibility, documentation and release scripts.
- [ ] Baseline build/APK/runtime; regression tests and final validation.

## Prioritized findings
| ID | Priority | Confirmed evidence | Status |
|---|---|---|---|
| A01 | P0 | ForceDozeService stores restoration state in booleans only; exitDoze/onDestroy omit leaveDozeHandleNetwork; battery saver restoration always false. Process loss/stop can leave changes active. | Open |
| A02 | P0 | ShizukuHandler caches binder/authorization, one listener can overwrite another, no binder death/receive handling, concurrent commands and unbounded pipe reads/wait. | Open |
| A03 | P0 | Exported whitelist receivers concatenate arbitrary packageName into shell command; settings receiver accepts malformed integers; automation has no opt-in or authorization. | Open |
| A04 | P1 | enterDoze marks IDLE and records ENTER before command outcome. getDeviceIdleState asynchronously returns stale state and only queries a root session in Shizuku mode; IDLE substring shadows IDLE_MAINTENANCE. | Open |
| A05 | P1 | Delayed Timer work survives stop/overlapping cycles, repeated Timer/executor leaks, delay holds up to 10-minute wakelock. Startup bypasses charging/call gates. | Open |
| A06 | P1 | Network guard `(ignoreIfHotspot || !wasHotSpotTurnedOn)` reverses protection. Mobile data tests connection state, not user enablement. Sensor privacy uses hardcoded Binder transaction and SU even in Shizuku mode. | Open |
| A07 | P1 | applyForceDozeSchedule ignores serviceEnabled and rewrites manual intent; startService used on API 26–30; background FGS starts can throw. | Open |
| A08 | P1 | Stats invent EXIT without ENTER, charging represented as 0%, wall-clock duration, malformed values crash; data mutated as shared preference Set, trim only on screen visit. | Open |
| A09 | P1 | Media2 creates executor per query; can never call completion, inspects only last media notification. | Open |
| A10 | P1 | Nonroot Android 14+ path resets entire device_idle namespace; loses original tunables and assumes local shell can write. | Open |
| A11 | P2 | README claims motion sensors disabled and battery savings without evidence; no local persisted observations. | Open |
| A12 | P2 | Build uses jcenter and dynamic libsuperuser; tests placeholders, missing JUnit; release defaults to debug signing. | Open |

## Validation
- Baseline unit compilation FAILED (4 errors: missing org.junit); debug assembled; release/lint/androidTest follow-up running; log `/tmp/enforcedoze-79dd/baseline-build.log`.
- No tests declared passed yet. Candidate APK identity recorded in PROGRESS.md; provenance pending.
- SDK 36/emulator installed. No KVM or Samsung hardware. Disposable AVD boot under investigation.

## Decisions and remaining queue
Prioritize durable write-ahead restoration, bounded serialized commands, actual observed state and safe cancellation before UI redesign. Preserve supported root/nonroot modes with capability checks. Investigate modern replacements for Binder-number hacks; disclose unsupported OEM functions. Initial baseline stage complete. Next: stage 2 access/journal/test foundation, collect pending baseline checks. Detailed activity/resource review continues during relevant stages.

Additional confirmed: tunable commands omit device_idle namespace, whitelist UI bypasses Shizuku, release/Fastlane can publish/push even through surprising branches. No remote work executed.
