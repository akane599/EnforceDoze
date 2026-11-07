# 0007. Toolchain, SDK levels and the fork's application id

- Status: Accepted
- Date: 2026-11-08
- Phase: P1
- Spec: 3.1, 12.2, 12.5; follows ADR 0002 and ADR 0003

## Context

P0 built upstream exactly as released: Gradle 8.13, AGP 8.13.2, the `kotlin-android` plugin 2.0.0,
JDK 17, `compileSdk`/`targetSdk` 36, `minSdk` 23, application id `com.akylas.enforcedoze`, Groovy
build files with `jcenter()`. SPEC 3.1 and 12.2 ask for JDK 21, a Gradle version catalog,
`compileSdk` ≥ 37, `targetSdk` 37, `minSdk` 34 and the application id
`io.github.akane599.enforcedoze`.

Versions resolved on 2026-11-08 from Maven metadata and services.gradle.org: AGP 9.5.1 (supports
API 37, needs Gradle ≥ 9.6.0 and build tools 36.0.0 by default), Gradle 9.9.0, Kotlin 2.4.21.
Gradle 9 removed `jcenter()`; every upstream dependency resolves from Maven Central except
`libsuperuser`, which is only on JitPack (as is libsu).

## Decision

- Gradle 9.9.0 wrapper with a pinned `distributionSha256Sum`; `gradle/gradle-daemon-jvm.properties`
  requests a Java 21 daemon; Java source and target 21.
- AGP 9.5.1 with built-in Kotlin: the `kotlin-android` plugin is removed, and the root build declares
  `org.jetbrains.kotlin.jvm` 2.4.21 (`apply false`) so built-in Kotlin and pure-JVM modules use the
  same Kotlin Gradle plugin.
- Kotlin DSL build files and `gradle/libs.versions.toml`. Upstream libraries keep their versions
  under `upstream-*` keys until the classes that use them are deleted.
- Repositories are declared once in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`); JitPack is
  restricted to the `eu.chainfire` and `com.github.topjohnwu.libsu` groups.
- `compileSdk` 37 (`platforms;android-37.0`), `targetSdk` 37, `minSdk` 34, application id
  `io.github.akane599.enforcedoze`, version 0.1.0 (versionCode 1). The namespace stays
  `com.akylas.enforcedoze`, the package of the upstream Java classes and their `R`, until those
  classes are replaced; new modules use `io.github.akane599.enforcedoze.*` namespaces.
- Upstream's `pm grant` / `pm revoke` commands named the old package literally; they now use
  `BuildConfig.APPLICATION_ID`, so the upstream grant flow still targets this app until the worker
  replaces it. Translated help strings that mention the old id are left as upstream text; they go
  with the screens that show them.
- AGP 9.5's lint flags `UsageStatsManager.queryUsageStats` without `PACKAGE_USAGE_STATS` as an
  error. The permission is declared: SPEC 4.1 needs usage access anyway, and the declaration is
  what lists the app under Settings > Usage access.
- Release builds are debug-signed until P7 wires Coulomb's key (ADR 0006); upstream's environment
  variable signing block is removed. `proguard-android.txt` becomes `proguard-android-optimize.txt`.
- CI uses JDK 21 and installs `platforms;android-37.0` and `build-tools;36.0.0`.

## Consequences

- The fork installs beside upstream. The upstream code keeps its `Build.VERSION.SDK_INT` branches
  for API < 34; lint reports them as `ObsoleteSdkInt` warnings, which do not fail the build and
  disappear with the classes.
- Jetifier stays on for `material-dialogs` 0.9.3 and `media2-session` until they are removed.
- Bumping any version means re-resolving it from metadata and updating the catalog comment.
