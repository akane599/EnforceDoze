---
name: EnforceDoze
description: Doze controller with proof for Android 16 and 17. A lab notebook for the night: calm, exact, evidence first.
---

# Design

<!--
  Seed, P0. Holds only what SPEC 10 pins. Tokens (OKLCH values), the type pairing, spacing, radius,
  elevation and motion durations are chosen in the P5 /impeccable shape step (SPEC 10.5) and
  recorded here with an ADR; until then nothing below may be read as a token value.
-->

## Direction (SPEC 10.1, pinned)

- **Character:** a lab notebook for the night. Calm, exact, evidence first. The session verdict and
  the timeline strip are the heroes; settings are secondary.
- **Signature element: the timeline strip.** One horizontal band per session: active, light and deep
  idle as three distinct fills *and* patterns (never colour alone), maintenance windows as thin
  ticks, breaks as labelled notches, missing data hatched. The same component appears small on Home,
  full-width in the report and stacked for comparisons.
- **Numbers are typography.** Tabular figures wherever a number can change; units smaller and
  quieter; a monospace face only for raw dump lines.
- **Avoid:** battery-green branding, moon and star clichés, gradient hero cards, glass blur, emoji in
  the UI, dashboards of equal-weight tiles. Do not reuse Coulomb's palette or type pairing.
- The upstream UI (preference screens, material-dialogs 0.9.3) is replaced, not restyled.

## Token roles (SPEC 10.2; values chosen in P5)

| Role | Use |
| --- | --- |
| Surface levels | Backgrounds and raised layers |
| Ink, quiet ink | Primary and secondary text |
| Accent | One accent only |
| Doze fills: active, light, deep | The timeline strip and anything that encodes a Doze state; each with its own pattern |
| Status: good, warning, critical | Verdicts and warnings only |

- `DESIGN.md` is the single source of truth for colour, type, spacing, radius, elevation and motion.
  The Compose theme is generated from it or checked against it in a unit test; screens hold no
  hard-coded colours or sizes.
- Colour in OKLCH. Light and dark are equal citizens; dark is checked first (the app is opened at
  night).
- Dynamic colour off by default so verdict colours stay stable; an option lets the accent follow the
  wallpaper.
- Contrast: text at least 4.5:1; graphics and strip fills at least 3:1 against their neighbours, in
  both themes; strip fills distinguishable under the common colour-vision deficiencies.

## Motion (SPEC 10.3)

- Motion explains state, nothing else: applying a profile shows each lever checking in with its
  read-back result; the timeline draws once on first view.
- Predictive back on every screen; a shared-element transition from the Home verdict to the report.
- Respect reduce-motion and the animator-duration setting.
- The one quiet moment: the morning "Held" verdict settles into place. Nothing else celebrates.

## Layout and accessibility (SPEC 9, 10.4)

- Edge-to-edge; two panes from the medium width class up; no orientation lock; DeX and foldables are
  adaptive.
- 48 dp targets; font scale 200% without clipping; English and Turkish both fit.
- TalkBack: the timeline strip reads as a sentence ("Deep idle from 00:12 to 06:58, 7 maintenance
  windows, one break at 03:40 for motion"); every chip and tile label includes its state.
- Jargon: only "Doze", "deep idle" and "maintenance window", each explained once in place.
