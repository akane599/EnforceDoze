# 0009. Module boundaries: a pure-JVM parse module and a three-way privilege split

- Status: Accepted
- Date: 2026-11-08
- Phase: P1
- Spec: 4, 8, 12.3, 12.4; edits the 12.3 module table

## Context

SPEC 12.3 puts the parsers in `:core:evidence` and the whole privilege layer, including the
`IDozeWorker` UserService, in `:core:privilege`. Building P1 raised two questions the table does not
settle:

1. **Parsers.** The `dumpsys deviceidle` settings parser is needed by the capability probes
   (`:core:privilege`, SPEC 4.4: which keys exist), by the engine's read-back and drift detection
   (`:core:doze`, 5.2 and 5.6) and by sessions and verdicts (`:core:evidence`, 8). Options put to the
   reviewer: (A) all parsers in `:core:evidence`, as 12.3 implies; (B) a low-level pure Kotlin/JVM
   module; (C) each module parses what it needs; (D) probes return raw text and others interpret it.
2. **Worker.** The worker runs from the app's APK in a separate `app_process` as shell or root, and
   is the only place hidden framework APIs may be called (3.2, 12.4). Options: (A) one
   `:core:privilege`; (B) separate API, worker and client modules; (C) the worker in `:app`.

Module boundaries are costly to reverse (SPEC 0.5), so a fresh read-only subagent gave a second
opinion with the options presented neutrally.

## Second opinion

- **Q1: B.** A creates a cycle: evidence collectors read through the worker (8.2), so
  `:core:evidence` depends on `:core:privilege`, while probes would depend on evidence for the
  settings parser. C lets three copies of the duration format (`+15s0ms`) disagree, which shows up as
  false "partly applied" or false drift. D splits 4.4 in two, though probe results must already be
  interpreted for the Privileges screen and the export. A JVM module needs no `android.jar` or
  Robolectric, so fixture tests are fastest. Cheap to reverse (a package move).
  *Strongest argument against:* it departs from 12.3, and a shared module tends to become a dumping
  ground that recompiles three modules on every change. Name it "parse", not "model", and keep it to
  text in, values out.
- **Q2: B, with flat names** (`:core:privilege-api`, `:core:privilege-worker`, `:core:privilege`).
  The worker process loads the whole APK either way, but with B the worker code can only reach the
  API module, never Hilt, Room or app singletons, which would crash in an `app_process` without an
  `Application`. Hidden-API stubs as `compileOnly` in the worker alone make "no hidden call in the
  app process" a compile-time fact; the reflection grep becomes a path rule, backed by a module-graph
  check. A leaves only a package grep; C puts the worker beside upstream's reflection in `:app`.
  Nested names (`:core:privilege:worker`) would make a glob over `core/privilege/**` cover the
  worker. Check that AGP packages a `runtimeOnly` library.
  *Strongest argument against:* three Android library modules for one or two thousand lines, every
  AIDL change touches two of them, and the boundary is compile-time only.

## Decision

The agent agrees with the second opinion on both questions and takes these measures against the
counter-arguments:

- `:core:parse` (Kotlin/JVM, explicit API, no project dependencies) holds the parsers and their value
  types only: text in, values out. Verdicts stay in `:core:evidence`, drift comparison in
  `:core:doze`. Unparsed lines are kept, never dropped (12.4).
- `:core:privilege-api` holds the AIDL, `CommandOutcome`, `WorkerContract` (class names, timeouts,
  allowed values) and `CommandRunner`, which both sides use. `:core:privilege-worker` holds the
  worker, its uid checks and argument validation, and is the only module with
  `compileOnly(:core:hidden-api-stubs)`. `:core:privilege` (tiers, Shizuku, Sui and libsu clients,
  the worker session, probes) depends on the worker with `runtimeOnly` and names the worker classes
  through `WorkerContract` constants.
- AGP packages the `runtimeOnly` worker: `dexdump` of the debug and release APKs shows
  `DozeWorker(Context)` and `DozeRootService()` after R8 (keep rules ship as the worker's consumer
  rules, with `-dontwarn` for the stubbed framework classes that live on the boot classpath).
- Enforcement in CI: `tools/ci/module-graph.sh` (nothing compiles against the worker or the stubs;
  `:core:parse` has no project dependencies; a positive control fails the check if the report cannot
  be read) and `tools/ci/forbidden-calls.sh` (reflection allowed only under `core/privilege-worker/`,
  `service call` nowhere; upstream uses are frozen in a baseline).
- SPEC 12.3 gains rows for `:core:parse`, `:core:privilege-api`, `:core:privilege-worker` and
  `:core:hidden-api-stubs`.

## Consequences

- The engine's fake worker (13.1) implements the Kotlin `DozeWorkerApi` facade in `:core:privilege`,
  not the AIDL stub.
- An AIDL change touches `:core:privilege-api` and the worker; both are small.
- If `:core:parse` starts collecting logic that is not parsing, move it out rather than widen the
  module.
