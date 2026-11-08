# EnforceDoze A17 fork — agent guide

Doze controller for Android 17 (One UI 9 on the Galaxy S25 Ultra; API 34–36 best-effort, ADR 0022) that proves it works with
its own logs, ships tuned profiles and recommends one from the owner's usage. A GPL-3.0 fork of
Akylas/EnforceDoze, rewritten in place as Kotlin modules. `SPEC.md` is the single source of truth;
this file is the short version. Change `SPEC.md` only through an ADR in `docs/adr/`.

## Operating rules (SPEC 0.2, 0.3)

1. Read `SPEC.md` before starting a phase. Asking a question the spec answers is a violation.
2. Priority when rules conflict: device safety (calls, user alarms, emergency always work; every
   change undoable) > honesty (never claim Doze worked without logs) > no self-inflicted drain >
   functional spec > `/impeccable` rules > Material 3 / Android guidance > your own preference.
3. Never invent a `dumpsys` / `cmd` flag, `device_config` key, EventLog tag, hidden API or binder
   transaction. Verify against AOSP source for API 37 (tag `android-17.0.0_r1`) or probe the
   device, and cite it in a code comment. API 34–36 are best-effort: no tests, no AVD, no AOSP
   check; failures there surface as capability probes with a reason (ADR 0022).
4. Never trust memory for versions: resolve from Maven metadata / GitHub releases at build time.
   GitHub Actions are pinned to commit SHAs in `.github/workflows/ci.yml`.
5. Work in the phases of SPEC 14; a phase ends when its gate passes. Small Conventional Commits;
   every commit builds and passes unit tests.
6. Keep GPL-3.0, upstream copyright notices, `NOTICE` and a visible credit to ForceDoze and
   EnforceDoze. New files carry an SPDX header (`GPL-3.0-or-later`).
7. No network permission, ever (CI checks the merged manifest). No `service call` transaction
   numbers, no reflection on Shizuku or framework internals in the app process.

## Autonomy (SPEC 0.5)

Never wait for "continue", at or between gates, and never end a turn with a question. Stop only for:
connecting to the phone; anything irreversible or public (releases, force-push, deleting data,
deleting branches); secrets you lack (signing key). Any other question: pick the most reasonable
option, ADR line, entry in `docs/questions.md`, continue. A non-negotiable (SPEC 1.3) change is
parked with a question, never made (ADR 0027).

| Decision | Action |
| --- | --- |
| Anything else | Decide; one-line reason in the phase's decisions ADR; own ADR only if it departs from the spec |
| Hard or costly to reverse: architecture (module boundaries, engine command model, recommender model), a data format, a database schema, a public API, deleting or reverting | Fresh read-only subagent for a second opinion, options neutral; decide; both views in an ADR (ADR 0023) |
| Stuck (3 distinct attempts or ~45 min) | Debugging subagent with evidence only; still stuck: mark blocked in `docs/progress.md`, move on |

The engine (SPEC 5) and the parsers (SPEC 8) each get one independent subagent review before their
phase gate. One short summary per phase gate. Non-blocking questions go to `docs/questions.md` with
the default in use. Spec items marked optional are not built: one line each in `BACKLOG.md`, shown
to the owner once the SPEC 15 checklist is done (ADR 0023).

## Environment (SPEC 0.4)

| Node | Role | Rule |
| --- | --- | --- |
| `build-server` | Agent, builds, JVM tests, CI pushes | Always |
| `akane-linux` | Emulator: the project's own AVD `ed-api37` (API 37 only, ADR 0021) | When `tailscale status` shows it online |
| `sm-s938b` (Galaxy S25 Ultra) | One UI behaviour, real nights | Ask first; batch into planned sessions |

- **build-server:** `~/.gradle` is a symlink to a shared 50 GB volume (`/mnt/HC_Volume_*`); check
  free space before large builds. Android SDK: `~/android-sdk`. JDKs: 17 and 21 in `/usr/lib/jvm`.
  A local adb server already listens on 5037, so do not forward 5037: run adb on akane-linux through
  `ssh akane-linux` (or forward to another port and set `ADB_SERVER_SOCKET=tcp:localhost:<port>`).
- **akane-linux** (the owner's daily PC, KVM): SDK at `~/Android/Sdk`, adb at
  `~/Android/Sdk/platform-tools/adb`. Use only `ed-api37`; the `pixel-api*` AVDs belong to other
  projects: never start, change or wipe them. One headless emulator at a time, shut down after
  (`adb emu kill`). Start it with `nohup ~/Android/Sdk/emulator/emulator -avd ed-api37 -no-window
  -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect &`. Its login shell is zsh: send scripts with
  `ssh akane-linux 'bash -s' <<'EOF'` and give every `adb shell` call `</dev/null`.
- **Phone:** it runs the owner's own Doze setup (Tasker + Shizuku). Never change Doze or sensor
  state on it without saying so first, and restore it afterwards. Fixture captures there are
  read-only (`tools/fixtures/capture.sh` without `--walk`).

## Commands

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # JDK 21, Gradle 9.9, AGP 9.5 (ADR 0007)
./gradlew assembleDebug testDebugUnitTest lintDebug detekt ktlintCheck :app:licensee   # CI
tools/ci/forbidden-calls.sh && tools/ci/module-graph.sh                               # CI scripts
./gradlew ktlintFormat                                 # fix style before committing
./gradlew updateLintBaseline                           # only when deleting upstream code shrinks it
tools/ci/forbidden-calls.sh --update                   # same rule for its baseline
tools/fixtures/capture.sh [-s SERIAL] [--walk] fixtures   # on akane-linux; --walk is emulator-only
gh run list -R akane599/EnforceDoze --limit 5          # CI status
```

## Repository map

| Path | What |
| --- | --- |
| `app/` | Upstream 1.10.2 Java app (package `com.akylas.enforcedoze`, app id `io.github.akane599.enforcedoze`), replaced module by module (SPEC 12.1, 12.3) |
| `core/parse` | Pure-JVM parsers for `dumpsys` and the event log, one per source (SPEC 8, 12.4) |
| `core/profile`, `core/doze` | Pure-JVM profiles, plan and guardrails; the engine actor behind ports (SPEC 5, 6, ADR 0015) |
| `core/doze-android` | The engine's Android side: device port over the privilege layer, hold-off sources, host |
| `core/data` | The engine's JSON files (`state`, `previous`, `stock`) and the Room evidence log (SPEC 8.3) |
| `core/evidence` | Pure-JVM session verdicts from the records (SPEC 8.4) |
| `core/privilege-api`, `core/privilege-worker`, `core/privilege` | `IDozeWorker` contract; the worker (only place for hidden APIs, runtime-only); tiers, clients, session, probes (SPEC 4, ADR 0009) |
| `core/hidden-api-stubs`, `testing/fixtures`, `testing/silent-audio` | compileOnly framework stubs for the worker; fixture access for JVM tests; a silent player for the audio hold-off test |
| `config/detekt`, `.editorconfig`, `tools/ci/` | detekt overrides, ktlint style, CI scripts and the forbidden-call baseline |
| `gradle/libs.versions.toml` | Version catalog; versions resolved from Maven metadata, never from memory |
| `fixtures/` | Raw dumps per device and build for parser tests (`fixtures/README.md`) |
| `tools/fixtures/`, `tools/emulator/` | Capture script; the phase gate runs on the emulator (`p1-privilege.sh`, `p2-engine.sh`) |
| `docs/adr/` | Decisions (`0000-template.md`) |
| `docs/upstream-audit.md` | SPEC 2 audit and the feature parity table (kept current) |
| `docs/progress.md`, `docs/questions.md` | Phase log, open questions with defaults |
| `BACKLOG.md` | Optional spec items, deferred until the SPEC 15 checklist is done (ADR 0023) |
| `docs/evidence/` | Screenshots and logs cited by phase gates |
| `PRODUCT.md`, `DESIGN.md` | `/impeccable` context (ADR 0005); `DESIGN.md` is the token source from P5 |

Git: `origin` = `akane599/EnforceDoze`, `upstream` = `Akylas/EnforceDoze`; integrate on `main`;
`upstream-baseline` tags the fork point. `main` is the default and only branch (ADR 0006).

## SPEC section index

| § | Topic | § | Topic |
| --- | --- | --- | --- |
| 0 | How to use, rules, environment, autonomy | 8 | Evidence: logs, verdicts, night report, quick proof |
| 1 | Goal, principles, non-negotiables, success | 9 | Screens and states |
| 2 | Upstream starting point and parity rule | 10 | Design and `/impeccable` |
| 3 | Platform requirements, One UI specifics | 11 | Automation, tiles, Coulomb, Tasker switch-over |
| 4 | Privilege tiers, Shizuku worker, probes | 12 | Engineering: modules, stack, rules, CI, releases |
| 5 | Doze engine: levers A–D, hold-offs, drift | 13 | Fast testing, compressed night |
| 6 | Profiles and constants | 14 | Phases and gates |
| 7 | Recommended | 15–16 | Acceptance checklist; appendix (commands, risks) |
