---
name: EnforceDoze
description: Doze controller with proof for Android 17. A lab notebook for the night: calm, exact, evidence first.
---

# Design

<!--
  Values below are recorded from the shipped `:core:design` code
  (core/design/src/main/kotlin/io/github/akane599/enforcedoze/design/) and checked against it by a
  unit test. Change a value in the code and here together; the test fails if they differ.
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

The built world is direction A of ADR 0028, the **night timetable**: the plan reads as a departure
board, the report as a route-line timetable with a due time and an actual one, nights compare on one
shared clock axis. Violet-black ground, a single signal yellow, Barlow Semi Condensed throughout.

## Colors (SPEC 10.2)

Defined in `Palette.kt` as `Palette.Dark` and `Palette.Light`. Values are OKLCH: lightness 0–1,
chroma, hue in degrees.

| Token | Dark | Light | Role |
| --- | --- | --- | --- |
| `ground` | oklch(0.155 0.020 285.0) | oklch(0.975 0.006 285.0) | Screen background, top bar, the gap between strip segments, text on ink |
| `raised` | oklch(0.200 0.024 285.0) | oklch(0.945 0.010 285.0) | First raised layer: timetable boards, notices, surface containers |
| `raisedHigh` | oklch(0.240 0.026 285.0) | oklch(0.920 0.012 285.0) | Second raised layer: high surface containers, error container |
| `hairline` | oklch(0.315 0.026 285.0) | oklch(0.840 0.014 285.0) | Rules between timetable rows, column rules, outline variant, hour grid, unaccented tag outline |
| `ink` | oklch(0.955 0.008 285.0) | oklch(0.220 0.030 285.0) | Primary text, figures, text actions (Material `primary`), wake-up caps and break notches on the strip |
| `quiet` | oklch(0.760 0.020 285.0) | oklch(0.450 0.025 285.0) | Secondary text, units, column heads, axis labels, outline |
| `accent` | oklch(0.880 0.150 98.0) | oklch(0.880 0.150 98.0) | The one accent: the primary action and the current item (`now` tag); Material primary/secondary container |
| `onAccent` | oklch(0.155 0.020 285.0) | oklch(0.220 0.030 285.0) | Text on the accent |
| `deep` | oklch(0.880 0.150 98.0) | oklch(0.330 0.070 285.0) | Deep idle fill, solid |
| `light` | oklch(0.660 0.110 98.0) | oklch(0.520 0.090 285.0) | Light idle fill, pinstriped |
| `active` | oklch(0.530 0.030 285.0) | oklch(0.640 0.020 285.0) | Active (not idle) fill, dotted |
| `missing` | oklch(0.760 0.020 285.0) | oklch(0.450 0.025 285.0) | Hatch lines over time the record covered but lost |
| `good` | oklch(0.820 0.120 165.0) | oklch(0.500 0.095 165.0) | Verdicts and warnings only: held |
| `warning` | oklch(0.790 0.140 62.0) | oklch(0.530 0.125 55.0) | Verdicts and warnings only: warning |
| `critical` | oklch(0.700 0.170 22.0) | oklch(0.540 0.180 25.0) | Verdicts and warnings only: failure; Material `error` |

Derived values, not tokens:

- `pattern` is `ground` at alpha 0.55 (`PATTERN_ALPHA`): the pinstripes and dots drawn over the
  light and active fills, so the pattern reads as texture, not as another colour.
- The Material scrim is `ground` at alpha 0.6.
- With the wallpaper-accent theme flag (`EdTheme(wallpaperAccent = true)`), `accent` and `onAccent`
  are replaced by the dynamic scheme's `primaryContainer` and `onPrimaryContainer`; the strip fills
  and status colours never follow the wallpaper.
- Every Material colour role is mapped onto these tokens in `Palette.scheme()` (`Theme.kt`), so no
  component falls back to the baseline purple.

**The Inverted Deep Rule.** In dark, deep idle is the accent yellow and light idle a dimmer yellow.
Yellow cannot carry a fill on a light ground (about 1.3:1), so in light deep idle is violet ink and
light idle a lighter violet, and yellow stays on accent surfaces under dark type, like a printed
departure poster (ADR 0028).

**The One Signal Rule.** Yellow marks one primary action and the current item. Text buttons are set
in ink, never in the accent, so the accent does not double up next to a `now` tag.

**The Status-Is-Verdict Rule.** `good`, `warning` and `critical` colour only verdicts and warnings
(the `Tone` enum); everything else is ink or quiet.

- `DESIGN.md` is the single source of truth for colour, type, spacing, radius, elevation and motion.
  The Compose theme is checked against it in a unit test; screens hold no hard-coded colours or
  sizes.
- Light and dark are equal citizens; dark is checked first (the app is opened at night).
- Contrast: text at least 4.5:1; graphics and strip fills at least 3:1 against their neighbours, in
  both themes; strip fills distinguishable under the common colour-vision deficiencies. Each fill
  reaches 3:1 against `ground`, and a 1 dp gap of `ground` separates strip segments so no two fills
  touch.

## Typography

Defined in `Type.kt`. One family: **Barlow Semi Condensed** (SIL OFL 1.1, bundled; regular 400,
medium 500, semibold 600), the timetable's condensed grotesk. Every style sets the OpenType feature
`tnum` (tabular figures). The only other face is the platform monospace, for raw dump lines.

| Style | Size (sp) | Weight | Line height (sp) | Letter spacing (em) |
| --- | --- | --- | --- | --- |
| `displayLarge` | 64 | SemiBold | 66 | -0.01 |
| `displayMedium` | 52 | SemiBold | 56 | -0.01 |
| `displaySmall` | 40 | SemiBold | 44 | -0.005 |
| `headlineLarge` | 32 | SemiBold | 38 | 0.005 |
| `headlineMedium` | 28 | SemiBold | 34 | 0.005 |
| `headlineSmall` | 24 | Medium | 30 | 0.005 |
| `titleLarge` | 22 | Medium | 28 | 0.005 |
| `titleMedium` | 17 | Medium | 24 | 0.005 |
| `titleSmall` | 15 | Medium | 20 | 0.005 |
| `bodyLarge` | 16 | Normal | 23 | 0.005 |
| `bodyMedium` | 15 | Normal | 21 | 0.005 |
| `bodySmall` | 13 | Normal | 18 | 0.005 |
| `labelLarge` | 15 | Medium | 20 | 0.01 |
| `labelMedium` | 13 | Medium | 17 | 0.02 |
| `labelSmall` | 12 | Medium | 16 | 0.03 |
| `RawLine` | 12 | (default) | 17 | (default) |

`RawLine` is `FontFamily.Monospace`, used only for raw dump lines (for example a notice's raw body).

**The Scale Contrast Rule.** Hierarchy comes from scale: the verdict word is set at display size
(`displayMedium`, or `displaySmall` in its compact form) in its status colour, and everything else at
title size or below. Section headings carry their own weight; there is no kicker above them.

**The Quiet Unit Rule.** A figure's number is in ink; its unit is set at 0.7 em in `quiet`, joined
to the number by a narrow no-break space, with no break after a unit's slash (`Formats.kt`,
`Figure.text()`). Percent goes after the number in English and before it in Turkish.

## Layout

Defined in `Dimens.kt` as `Space` and `Sizes`.

| Spacing | dp | Use |
| --- | --- | --- |
| `Space.hair` | 1 | Rules, column rules, 1 dp outlines |
| `Space.xs` | 4 | Below a section heading, above the strip axis, tight gaps in a group |
| `Space.s` | 8 | Vertical padding of rows |
| `Space.m` | 12 | Between an icon or switch and its text; figure cell padding |
| `Space.l` | 16 | Notice padding, legend entry gap |
| `Space.xl` | 24 | Above a section heading |
| `Space.xxl` | 32 | Largest step |
| `Space.gutter` | 16 | The side gutter of every screen |
| `Space.column` | 640 | Maximum width of the reading column on large screens |

| Size | dp | Use |
| --- | --- | --- |
| `Sizes.touch` | 48 | Minimum touch target and row height |
| `Sizes.stripSmall` | 28 | Small strip (Home) and the emphasised row of a stack |
| `Sizes.stripLarge` | 40 | Full-width strip (report) |
| `Sizes.stripRow` | 16 | Base height of a row in a stacked comparison (plus 4 dp) |
| `Sizes.icon` | 22 | Icons in notices |
| `Sizes.mark` | 22 | A profile's lever mark (A–D); grows with the font up to 1.5× |
| `Sizes.markStroke` | 1.5 | Ink outline of a lever in use; a lever not in use is struck through in `quiet`, unboxed |

Spacing rhythm: tight inside a group, generous between groups, more space above a heading than below
it. Every screen is one scrolling column (`ScreenColumn`) with the 16 dp gutter, centred and capped
at 640 dp; the top bar aligns with that column on wide screens.

## Elevation & Depth

Flat. No shadow or tonal elevation is set anywhere in `:core:design` or the feature screens. Depth
comes from tonal layers only: `ground`, then `raised`, then `raisedHigh`, separated by `hairline`
rules. `surfaceTint` is mapped to `ground`, so Material surfaces gain no tint with elevation.

## Shapes

Defined in `Dimens.kt` as `EdShapes`. Printed matter has small radii: boards and notices are cut
sheets, not pills. Material controls (buttons, switches) keep their own shapes.

| Shape | Radius (dp) | Use |
| --- | --- | --- |
| `extraSmall` | 3 | Tags (`now`) |
| `small` | 6 | |
| `medium` | 8 | |
| `large` | 12 | |
| `extraLarge` | 16 | |

## Components

Shared components live in `:core:design`; screens compose them and hold no colours or sizes.

### Timeline strip (signature)

`TimelineStrip.kt`. One band per session or night on a time axis.

- **Fills and patterns:** deep idle solid `deep`; light idle `light` with vertical pinstripes every
  3 dp (1 dp lines in `pattern`); active `active` with dots on a 4 dp grid (0.9 dp radius in
  `pattern`); missing data hatched with 1 dp diagonal `missing` lines every 5 dp. Holes the record
  did not cover stay blank.
- **Marks:** maintenance windows are thin ticks that overhang the band (default overhang 4 dp, 2 dp
  in stacks); breaks are 4 dp notches of `ground` with a 1.5 dp `ink` line through; a wake-up inside
  a night is a slot of `ground` at least 3 dp wide under a 2 dp `ink` cap.
- **Separation:** a 1 dp gap of `ground` between segments.
- **Axis:** `StripAxis` sets time labels under the strip at their true positions in `quiet`, dropping
  a label that would overlap the previous one (6 dp minimum gap) except the last.
- **Stacked:** `StackedStrips` lays nights on one clock axis with a `hairline` hour grid, a 52 dp
  label column and a 56 dp value column; the emphasised night is in ink at `stripSmall` height.
- **Legend:** `StripLegend` shows an 18 × 12 dp swatch per state with its pattern.
- **Accessibility:** the strip's semantics carry a sentence summary for TalkBack.

### Verdict

`Verdict` in `Parts.kt`: the verdict word at display size in its tone colour, with a one-line reason
in `titleMedium` ink.

### Figure row

`FigureRow`: key numbers as one ruled timetable row, not tiles. Hairline column rules between
cells, 12 dp cell padding, columns at least 80 dp wide; when they do not fit, the row breaks into
balanced rows. Number in ink, label in `labelMedium` quiet.

### Notice

`Notice`: a warning or note on `raised` with 16 dp padding, a 22 dp icon and `titleSmall` title.
An unseen warning carries a 1 dp outline in its tone colour; once seen, the outline goes and the icon
turns quiet (ADR 0028, warnings hold their alert colour until seen).

### Tag

`Tag`: a timetable service mark in `labelLarge`, `extraSmall` shape, padding 7 × 1 dp. Accented: the
accent fill with `onAccent` text, for the current item. Otherwise a 1 dp `hairline` outline with
`quiet` text.

### Rows and headings

- `SectionHeader`: heading with optional quiet detail and one text action; 24 dp above (none for
  the first), 4 dp below, at least 48 dp tall.
- `ColumnHead`: quiet `labelMedium` over ruled timetable columns; `Rule`: a 1 dp hairline divider.
- `SwitchRow`: title in `bodyLarge` (or `titleLarge` as a section heading) with a quiet detail line;
  the whole row toggles, at least 48 dp tall.
- `EdTopBar`: Material top app bar on `ground` with `ink` title, aligned with the reading column.

### Buttons

Material 3 buttons on the mapped scheme: the one primary action is filled with the accent and
`onAccent` text; text actions are in ink; secondary actions are outlined.

## Motion (SPEC 10.3)

- Motion explains state, nothing else: applying a profile shows each lever checking in with its
  read-back result; the timeline draws once on first view.
- Predictive back on every screen; a shared-element transition from the Home verdict to the report.
- Respect reduce-motion and the animator-duration setting.
- The one quiet moment: the morning "Held" verdict settles into place. Nothing else celebrates.

Recorded from `Motion` in `Theme.kt`:

| Token | Value | Use |
| --- | --- | --- |
| `Motion.SHORT_MS` | 150 ms | Short transitions |
| `Motion.MEDIUM_MS` | 300 ms | Medium transitions |
| `Motion.STRIP_DRAW_MS` | 700 ms | The strip drawing itself once on first view |
| `Motion.VERDICT_SETTLE_MS` | 450 ms | The verdict settling into place |
| `Motion.Settle` | cubic-bezier(0.16, 1, 0.3, 1) | Exponential ease-out: things arrive and settle |

- The strip multiplies its draw duration by the system animator-duration scale and is drawn at once
  when the scale is 0.
- The verdict starts visible 6 dp above its place and eases down, so a disabled animation loses
  nothing.

## Layout and accessibility (SPEC 9, 10.4)

- Edge-to-edge; two panes from the medium width class up; no orientation lock; DeX and foldables are
  adaptive.
- 48 dp targets; font scale 200% without clipping; English and Turkish both fit.
- TalkBack: the timeline strip reads as a sentence ("Deep idle from 00:12 to 06:58, 7 maintenance
  windows, one break at 03:40 for motion"); every chip and tile label includes its state.
- Jargon: only "Doze", "deep idle" and "maintenance window", each explained once in place.

From the code: the 48 dp target is `Sizes.touch`; the reading column caps at `Space.column`
(640 dp) on wide screens.

## Not in the code yet

- **Wallpaper accent option (SPEC 10.2):** `EdTheme` has a `wallpaperAccent` parameter, but no
  caller passes it and no setting exposes it, so the option is not reachable in the app.
- **Shared-element transition from the Home verdict to the report (SPEC 10.3):** no shared-element
  code exists.
- **`Motion.SHORT_MS` and `Motion.MEDIUM_MS`:** defined, but nothing references them yet.
- **Usage for the `small`, `medium`, `large` and `extraLarge` shapes and `Space.xxl`:** defined;
  only the Material components that read the theme shapes use them, so no dedicated rule is recorded.
