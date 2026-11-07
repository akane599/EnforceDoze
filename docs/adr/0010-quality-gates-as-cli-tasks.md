# 0010. Quality gates: detekt and ktlint as CLI tasks, licensee, forbidden-call and graph checks

- Status: Accepted
- Date: 2026-11-08
- Phase: P1
- Spec: 12.4, 12.5, 15; follows ADR 0003

## Context

ADR 0003 deferred detekt, ktlint, the licence check and the "no `service call`, no reflection" check
to P1, when Kotlin modules arrive. Versions resolved from Maven metadata on 2026-11-08: detekt
1.23.8 (February 2025; 2.0.0-alpha.6 is the newest, still alpha), ktlint 1.8.0, licensee 1.14.1.
detekt 1.23.8's Gradle plugin predates AGP 9's built-in Kotlin, which replaces the
`kotlin-android` plugin those integrations hook into. Upstream's Java code in `:app` still uses
reflection and `service call` until its classes are deleted (SPEC 12.1).

## Decision

- **detekt** 1.23.8 and **ktlint** 1.8.0 run from their CLI artifacts as root tasks `detekt`,
  `ktlintCheck` and `ktlintFormat`, over `core/`, `feature/` and `testing/` sources and all
  `*.gradle.kts` files. No per-module plugin is needed and nothing depends on AGP's Kotlin
  integration. `ktlint-cli` resolves to its shadowed variant, the only one that bundles its CLI
  parser. detekt builds on its default configuration with overrides in `config/detekt/detekt.yml`
  (140 columns, enum codes and constants are not magic numbers). Intentional broad catches at process
  boundaries carry a `@Suppress` with the reason.
- `.editorconfig` selects ktlint's `intellij_idea` style with 140 columns. `ktlint_official` was
  tried and flagged 482 layout-only findings in new code; it is a matter of taste, not correctness.
  Upstream sources under `app/src` are excluded: they are replaced, not restyled.
- **licensee** 1.14.1 on `:app` allows Apache-2.0, MIT, BSD-2/3-Clause, GPL-3.0, LGPL-2.1+/3.0+ and
  MPL-2.0. Two entries were verified by hand on 2026-11-08: Shizuku-API names only its LICENSE URL,
  which is MIT, and libsu's JitPack POMs declare no licence while the repository is Apache-2.0.
- **`tools/ci/forbidden-calls.sh`** fails on any `service call` and on reflection
  (`Class.forName`, `getDeclared*`, `setAccessible`, `java.lang.reflect`, `kotlin.reflect.full`,
  `HiddenApiBypass`, `newProcess`) outside `core/privilege-worker/`. Upstream's current uses are
  frozen per file and match in `tools/ci/forbidden-calls-baseline.txt`; `--update` is run only when
  deleting upstream code shrinks it, the same rule as the lint baseline.
- **`tools/ci/module-graph.sh`** enforces ADR 0009.
- Pure-JVM modules register `testDebugUnitTest` as an alias for `test`, so the CI command runs their
  tests too.

## Consequences

- When detekt 2 is stable, compare it with this setup before switching; the CLI keeps working
  meanwhile.
- A new dependency with an unlisted licence fails CI until its licence is checked and allowed here.
- Screenshot verification and `npx impeccable detect` join in P5 (ADR 0003).
