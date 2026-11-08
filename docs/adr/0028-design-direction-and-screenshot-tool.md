# 0028. Design direction and screenshot tool

- Status: Accepted
- Date: 2026-11-08
- Phase: P5
- Spec: 10.1, 10.2, 10.5, 10.6

## Context

SPEC 10.5 asks for 2–3 directions for Home and the night report as Compose preview screenshots, one
chosen in an ADR. SPEC 10.6 asks for Roborazzi or Paparazzi, chosen in an ADR. The /impeccable shape
step normally ends with an interview and a decision page for the owner; ADR 0027 replaces both with
"pick the most reasonable option, record it, continue". The owner sees the result in the P5 review ZIP.

## Decision

### Screenshot tool: Roborazzi

Roborazzi 1.77.0 on Robolectric 4.17 with native graphics, run as JVM unit tests (`:core:design`,
`recordRoborazziDebug`). Paparazzi 2.0 is still alpha and lags compileSdk 37; Roborazzi uses
Robolectric's real Android framework, the same runner the other JVM tests use, and captures
Compose and View screens alike. Robolectric runs at `sdk=36` until it ships an API 37 image; the
screens use no API 37 framework call. On JDK 21 its FileDescriptor interceptor needs
`--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` (set in the module's test task).

### Direction study

One synthetic night (screen off 23:41, deep idle 00:24 against 00:23 due, a motion break at 03:40,
five maintenance windows) drawn by three directions on the same Material 3 shell, 384 × 832 dp,
dark: `docs/design/directions/{a,b,c}-*.png`, source in `core/design/src/test/.../directions`.

| | Direction | Type | Signature | Verdict |
| --- | --- | --- | --- | --- |
| A | Night timetable (assigned) | Barlow Semi Condensed | Plan as a departure board; the report as a route-line timetable with Due and Late | **Chosen** |
| B | Sleep lab | Atkinson Hyperlegible Next | A stepped hypnogram above the strip | Declined |
| C | Seismograph drum | Overpass | Seven nights stacked on clock time on Home; the report in hour rows | Declined, its stack adopted |

Why A:
- It is the only direction where the world explains the product. A plan has departure times and a
  night has a due time and an actual one, so "deep idle due 00:23, +1 min" is native to the form. It
  is not decoration.
- It answers Home's SPEC 9.1 content in reading order: what runs now and next, then whether last
  night held.
- The strip stays the hero (SPEC 10.1). B's hypnogram competes with it, and it reads like a
  sleep-tracker app (Samsung Health on the same phone).
- C's palette is battery green, which SPEC 10.1 rules out, and its Home pushes the plan below the
  fold.
- The colours are clear of the siblings: Coulomb is hue 255 blue with Hanken Grotesk; Chinagraph is
  warm graphite with safelight orange.

What A takes from the others and from the challengers in the shape round:
- **Stacked comparison (C, plus the doujin density raise):** nights compared on one shared clock
  axis with a visible hour grid (the Crouwel raise). This is the strip "stacked for comparisons" in
  the report and in Log.
- **One plot owns the field (Saville):** the strip is the only chart on any screen.
- **Hierarchy by scale contrast (specimen):** the verdict word is set at display size, and
  everything else is set at title size or smaller.
- **Warnings hold their alert colour until seen (gate board).**
- **Destructive actions** (restore stock, all sensors off, reset) are isolated by space and outlined
  until focused (dev console).

Fixes the study showed, carried into the build:
- The figure row clipped "0.40 %/h". Units are set smaller and quieter (SPEC 10.1), and the row is
  one ruled timetable row, not four tiles.
- Hour labels sit at their true positions on the strip.
- The navigation bar's selected label fell back to the default purple. The theme sets every role.
- The yellow "Change" text button next to the yellow "now" chip doubles the accent. Text buttons go
  to ink.

Light theme: yellow cannot carry a deep-idle fill on white (contrast under 3:1). Light uses violet
ink for deep idle and keeps yellow for accent surfaces with dark type, like the printed departure
poster. This is settled with the tokens and checked in the audit (SPEC 10.2).

### Tokens and DESIGN.md

/impeccable writes DESIGN.md at the finish, from the built world. SPEC 10.2 makes DESIGN.md the
token source, "generated from it or checked against it in a unit test". The two are reconciled:
- The tokens are written in Kotlin in `:core:design` while the build runs.
- The documenter writes DESIGN.md at the finish.
- From then on, a unit test checks every theme value against DESIGN.md, so neither can drift.

The direction contract lives in `.impeccable/surfaces/app.md` (seed reference
`seed:1b1434f9a0ed4298`) and stays out of source.

## Consequences

- `:core:design` holds the theme, type, the strip and the shared components. Screens live in
  feature modules and hold no colours or sizes.
- The study's test sources stay as the record of the choice until the P5 gate. They are not part of
  the screenshot set.
- If the owner prefers B or C after the review ZIP, the tokens change in one place (`:core:design`).
  The screens keep their structure, except the Home order for C.
