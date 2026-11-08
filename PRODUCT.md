# Product

<!-- impeccable:product-schema 1 -->

<!--
  Source: SPEC.md, written by the owner (Tuğberk Özgen), sections 1, 3.3, 7, 8, 9, 10 and 11.
  SPEC 0.1 asks for PRODUCT.md from /impeccable init "aligned with sections 1 and 10", and SPEC 0.3
  rule 1 forbids asking questions the spec answers (it ranks above /impeccable rules in SPEC 0.2),
  so no separate interview was held: every fact below is quoted or condensed from the spec
  (ADR 0005). Change it only together with SPEC.md, through an ADR.
-->

## Platform

android

## Users

The owner first: a Galaxy S25 Ultra user on One UI 9 (Android 17) who already tunes Doze by
hand, with a Tasker setup ("dozectl") that runs a shell script through Shizuku at screen off and on.
They want the phone to reach deep Doze sooner and stay there longer while the screen is off, and to
see each morning what actually happened, without taking anything on trust. They read numbers
comfortably, often open the app at night or first thing in the morning, carry a Galaxy Watch and use
Android Auto.

More broadly: Android power users with Shizuku, Sui or root who want Doze control with proof.

## Product Purpose

EnforceDoze is a Doze controller that gets the phone into deep idle sooner and keeps it there longer
while the screen is off, using Android's own Doze timers, forced idle, sensor restriction and the
Doze allowlist. Every morning it can show, from system records, exactly what happened: when deep
sleep started, what interrupted it, which sensors were on and who woke the phone. It ships six tuned
profiles and recommends a plan from the owner's own usage history, from day one.

Success (SPEC 1.4): deep idle within a minute of the profile's target on a still phone; at least 90%
of screen-off time in deep idle on a still night; zero wakeups caused by the app itself; reported
deep-idle minutes within 2% of `batterystats`; "Restore stock" returns the exact pre-install state.

## Positioning

Proof over promises. Other Doze tools change settings and assume they worked; EnforceDoze judges
every profile by measured deep-sleep time read from system records (event log, `batterystats`,
`deviceidle`, `sensorservice`), names what broke a night, and audits its own wakeups in the same
report. It never claims Doze worked unless its logs show it. It is an honest, credited GPL-3.0 fork
of EnforceDoze and ForceDoze with its own app id.

## Operating Context

- Glanced at in the morning (the night report and its verdict) and opened at night; set up once,
  then left alone. The timeline strip of a session is the thing people look at.
- Privilege comes from Shizuku (or a compatible fork on Android 17), Sui or root; without any, the
  app runs read-only.
- Lives beside other tools that touch Doze: the owner's Tasker profiles, Coulomb (per-app battery
  attribution, which reads EnforceDoze's evidence), upstream EnforceDoze, Battery Guru. EnforceDoze
  detects them and never fights them silently.
- Control from outside the app: Quick Settings tiles ("Doze plan", "Sensors off"), Tasker broadcasts
  protected by a permission, a silent foreground-service notification.
- One UI specifics: "Never sleeping apps", Samsung power saving, lift-to-wake and pocket detection,
  Galaxy Watch notifications relayed through the phone.

## Capabilities and Constraints

- Four levers: (A) Doze constants, (B) forced deep idle, (C) sensor restriction or all sensors off,
  (D) the Doze allowlist. Each change is applied, read back and logged; one tap restores stock.
- Profiles: Stock, Everyday, Pocket, Night, Classic, Strict, plus custom clones. A plan is a day
  profile plus an optional night profile with a window. Recommended outputs a plan with reasons,
  numbers, a confidence level and its cost, and never switches plans without a tap.
- Hold-offs always protect calls, user alarms, emergency alerts, navigation, Android Auto, music
  and hotspot.
- Non-negotiable: Android 17 (API 37) on the S25 Ultra with One UI 9 (ADR 0022); no polling or
  alarms while the screen is off (one optional, labelled delay alarm); restores stock on disable and
  on request; no network permission; calls, user alarms and emergency alerts are never blocked.
- Out of scope: per-app battery attribution (Coulomb's job), kernel or governor tuning.
- Terminology shown to people: "Doze", "deep idle" and "maintenance window", each explained once in
  place. No other internal terms in the UI.

## Brand Commitments

- Name: EnforceDoze, kept for now as a credited fork (SPEC 16.3); app id
  `io.github.akane599.enforcedoze`.
- Character: "a lab notebook for the night" — calm, exact, evidence first (SPEC 10.1).
- A sibling of Coulomb and Chinagraph, held to the same /impeccable bar, but not a clone: it does not
  reuse Coulomb's palette or type pairing.
- Avoid: battery-green branding, moon and star clichés, gradient hero cards, glass blur, emoji in the
  UI, dashboards of equal-weight tiles.
- Visible credit to ForceDoze (Suyash Srijan) and EnforceDoze (Martin Guillon / Akylas); GPL-3.0.

## Evidence on Hand

- Raw dumps from the API 35, 36 and 37 emulators in `fixtures/` (Doze walks, sensor modes, event
  log, battery history).
- Upstream 1.10.2 audit with emulator results: `docs/upstream-audit.md`.
- No measured battery savings, no night reports from a real phone, no user quotes yet. Nothing may
  claim savings until the app's own reports show them.

## Product Principles

1. Proof over promises: judge everything by measured deep-sleep time, never by settings.
2. System timers first: the controller must not become the wakeup it fights.
3. Reversible always: record every previous value; one tap restores stock.
4. Never break the essentials: calls, alarms, navigation, car, music and hotspot win over Doze.
5. Explain the recommendation, and keep one owner of Doze on the phone.

## Accessibility & Inclusion

English first, full Turkish translation checked for length on every screen. TalkBack summaries for
the timeline strip and every stateful chip and tile; 48 dp targets; font scale up to 200% without
clipping; Doze states told apart by pattern as well as colour, readable under common colour-vision
deficiencies; contrast 4.5:1 for text and 3:1 for graphics in both themes; reduce-motion respected;
two-pane layouts on large screens and foldables, no orientation lock (SPEC 9, 10.2, 10.4).
