# 0003. CI skeleton and the order in which quality gates join

- Status: Accepted
- Date: 2026-11-07
- Phase: P0
- Spec: 0.3, 12.5, 13, 14 (P0)

## Context

SPEC 12.5 lists the full gate: `assembleDebug`, `lint`, `detekt`, `ktlintCheck`, unit tests,
screenshot verification, `npx impeccable detect` and a licence check. At P0 the repository is
upstream 1.10.2: Java, no Kotlin modules, no screenshot tests, no Compose screens. Two upstream
facts block a green gate as-is (`docs/upstream-audit.md` §1): `ExampleUnitTest` never compiled (no
test dependency), and `lintDebug` fails on 2 errors and 157 warnings.

## Decision

- CI is GitHub Actions on the fork (`.github/workflows/ci.yml`), triggered on pushes to `main`, pull
  requests and by hand. Actions are pinned to commit SHAs resolved from each action's latest release
  on 2026-11-07 (tag in a trailing comment).
- P0 steps: `assembleDebug`, `testDebugUnitTest`, `lintDebug` against `app/lint-baseline.xml`, and a
  merged-manifest check that `android.permission.INTERNET` is absent (SPEC 1.3), plus the debug APK
  as an artifact.
- The lint baseline freezes the 2 errors and 157 warnings upstream ships with; new issues fail CI.
  Entries disappear as upstream classes are deleted (SPEC 12.1); the baseline is never regenerated to
  hide a new finding.
- JDK 17 matches upstream's toolchain; JDK 21 arrives with the Kotlin modules in P1 (SPEC 12.2).
- Gates join when their subject exists: detekt, ktlint, the licence check and the
  "no `service call`, no Shizuku reflection" grep (SPEC 15) in P1; screenshot verification and
  `npx impeccable detect` in P5.
- Emulator suites (SPEC 13) do not run on GitHub: they run from build-server against akane-linux
  over Tailscale, and their logs are linked from `docs/progress.md`.
- Upstream's `release.yml` (manual dispatch only, publishes with upstream's Fastlane setup) is left in
  place for now and replaced in P7 (SPEC 12.6); it cannot run without upstream's secrets.

## Consequences

Each phase gate lists which CI steps it added. Revisit the JDK and the SHA pins when P1 bumps the
toolchain.
