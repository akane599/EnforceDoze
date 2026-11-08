# 0029. UI layer: feature modules, wiring and navigation

- Status: Accepted
- Date: 2026-11-08
- Phase: P5
- Spec: 9, 10.3, 12.2, 12.3

## Context

SPEC 12.3 puts the screens in `:feature:*` modules and the navigation, DI wiring, service, tiles and
automation receiver in `:app`. SPEC 12.2 says Hilt unless an ADR picks otherwise. Three things are
open:
- what a feature module may see;
- how screens reach the engine host, which exists only while the foreground service runs;
- which navigation library carries the two-pane layouts (SPEC 9) and predictive back (SPEC 10.3).

All three are hard to reverse, so a fresh read-only subagent gave a second opinion (ADR 0023). The
options were put neutrally: features own their sources; a shared repository module; or ViewModels in
`:app`.

## Decision

**Feature modules own a source interface.**
- Each `:feature:x` holds an `XSource` interface (Flows and suspend commands), its UI state types,
  stateless screen composables and a plain `ViewModel(source)`.
- Features carry no DI annotations and no navigation types. Navigation leaves a screen as callbacks.
- `:app` implements each source over three shared app services (engine access, sessions, privilege)
  and creates the ViewModels.
- A feature compiles against `:core:design`, `:core:ui` and the pure-JVM core modules (`profile`,
  `doze`, `evidence`, `recommend`, `parse`). Nothing else.
- `:core:ui` holds the wording that more than one screen shows: verdict words and their tones, cause
  sentences, lever names and the state notices of SPEC 9.3. It is the main risk's mitigation in
  code: two screens cannot word a verdict or a lost privilege differently. It sees `:core:design`
  and the pure-JVM modules, under the same graph rule as a feature.

**Display models instead of Android-module types.**
- `SessionRow`, `NightReport`, `CapabilityReport` and the tier types live in Android modules. A
  feature defines the display model it needs, and `:app` maps to it.
- No entity moves, so the Room schema is untouched.

**Hand wiring, not Hilt.** `EngineGraph` stays the composition root and grows the app services. The
ViewModels are created with `viewModel { … }` factories.

**Engine access.**
- `EngineGraph.host` becomes a `StateFlow<EngineHost?>`. One app-wide `EngineAccess` derives Off,
  Starting or Running with its `EngineStatus` from it.
- A command returns a typed "unavailable" result when the engine is off.
- Owner actions that mean "turn it on" set `EngineSwitch`, start the service, wait for the host with a
  timeout, then send.
- No ViewModel ever holds an `EngineHost`. After `stop()` a command would sit in a queue nobody
  drains.

**Navigation 3** (1.2.1, with `adaptive-navigation3` 1.3.0's list–detail scene strategy).
- One serializable back stack. Two panes come from a tag on each entry from the medium width class up.
- `NavDisplay` gives predictive back.
- Route keys and `NavDisplay` stay in `:app`, so swapping the library later touches only `:app`.
- The bottom bar becomes a rail on wide screens through `material3-adaptive-navigation-suite`, from the
  Compose BOM.

**A convention plugin for features.** `build-logic` holds `enforcedoze.android.feature`: the library,
Compose and Roborazzi setup, and the screenshot test runner. Nine feature build files stay at a few
lines each.

**The module graph check gains the feature rules.** A feature and `:core:ui` may not reach
`:core:data`, `:core:privilege*`, `:core:doze-android`, a feature or `:app`, and must use
`:core:design`. `:core:design` may reach no other project.

### Both views

| Point | Second opinion | Decision |
| --- | --- | --- |
| Shape | Option 1, tightened: sources in features, implementations in `:app` | Same |
| Android-module types | Move them to a pure-JVM module, keeping package names; if Room's KSP cannot read entities across modules, fall back to display models | Display models from the start: no schema risk, and screens get exactly the fields they show |
| DI | Hilt works (Dagger 2.59.x supports AGP 9 and Jetifier), but its plugin follows AGP majors; with annotation-free features it would only replace about 100 lines | Hand wiring; the choice stays inside `:app` and is cheap to change |
| Engine host | `StateFlow<EngineHost?>` behind one `EngineAccess`; a typed unavailable result | Same |
| Navigation | Navigation 3 with list–detail scenes; check predictive back and the shared element on 1.2.x (an animation handoff fix is only in 1.3.0-alpha02) | Same; checked on the emulator before the P5 gate |
| Main risk | Nine source implementations drift apart (two screens decide "privilege lost" differently) and `:app` turns into option 3 | Mitigated by the three shared services; warnings and verdict wording are computed once and reused |

Not chosen:
- **A shared repository module:** it re-exports everything through `api` and recompiles all features
  on any change.
- **ViewModels in `:app`:** all logic would sit in the slowest module, which carries the upstream
  Java code, Jetifier and the lint baseline. The boundary check would guard empty shells.

## Consequences

- SPEC 12.2's Hilt line is departed from by this ADR. Revisit only if the graph outgrows hand wiring.
- `org.gradle.parallel` stays as it is. Turning it on is a separate, measured change.
- Upstream activities stay until the parity rows they serve are covered (SPEC 12.1). The new Compose
  activity becomes the launcher, and the upstream screens remain reachable only through their code
  until deleted.
