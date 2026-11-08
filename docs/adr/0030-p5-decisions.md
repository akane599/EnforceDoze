# 0030. P5 decisions

- Status: Accepted
- Date: 2026-11-08
- Phase: P5
- Spec: 9, 10, 12.5

## Context

ADR 0023: choices that are cheap to reverse are made by the agent, each with a one-line reason here.
The direction is ADR 0028 and the UI layer is ADR 0029.

## Decision

- **The screenshot matrix is a half fraction of the 16 cells.** Every screen is shot in light and
  dark, English and Turkish, font scale 1.0 and 2.0, phone and tablet width (SPEC 10.6). Each
  screen gets 8 of the 16 combinations: the tablet column is the parity of the other three. Every
  value of every dimension then shows four times and every pair of values twice. Turkish at font
  2.0 is in it on both widths, which is what the acceptance item on fit needs. 16 cells per screen
  would double the goldens, roughly 40 MB more, and show nothing a pair does not.
- **Secondary states are shot in one cell**: dark, English, font 1.0, phone. Dark is checked first
  (SPEC 10.2). A state that only changes content does not need eight copies.
- **Shots render at hdpi**: phone 384 × 832 dp, tablet 840 × 1100 dp, 1500 dp tall for states that
  run past the first screen. hdpi keeps a golden near 70 KB and still shows a 1 dp line.
- **Goldens live next to their module** in `<module>/screenshots/<screen>/<variant>.png`. A
  change to a screen and the change to its pictures land in the same commit.
- **Shots use the owner's clock**: Europe/Istanbul and a 24-hour clock, with one shot on the
  12-hour clock. A run on another machine renders the same text.
- **Espresso is pinned to 3.7.0 in tests.** Robolectric 4.17 needs it: 3.5.1, which comes in
  transitively, calls `InputManager.getInstance()`, removed in API 36.
- **Robolectric runs with two JDK 21 flags**: `--add-exports java.base/jdk.internal.access` and
  `--add-opens java.base/java.io`. Native graphics opens raw file descriptors, and without them
  every shot fails.
- **`:core:design` knows no domain.** It holds the theme, the strip, its axis, stacked strips and
  the shared parts. Strings that features share, such as duration and percentage formats, are its
  own resources. The graph check forbids it any other project.
- **Units are set in 0.7 em quiet ink after the number**, with a thin space before a letter or
  `%`. The figure stays the thing the eye lands on, which is the specimen raise of ADR 0028.
- **Turkish puts `%` before the number** ("%62") and English after it ("62%"). These are the two
  languages' rules, kept as string resources rather than in code.
