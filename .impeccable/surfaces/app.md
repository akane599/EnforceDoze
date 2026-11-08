---
version: 1
slug: "app"
primary_target: "app"
related_targets: ["core/design"]
---

# EnforceDoze app

Mode: Operate (native Android, Compose, Material 3). Code-led. Dark checked first; light is an equal
citizen. Visitor: the owner, often at night or first thing in the morning, checking whether Doze held
and what runs now. Job: read the verdict, trust the evidence, change the plan rarely.

## Direction contract

THESIS: A night is a scheduled service run against its timetable. The plan is a departure board, the
night report is the run's timetable with due and actual times. This replaces the category default of
battery-app gauges and tile dashboards.

OWN-WORLD: Rail timetable print. Violet-black ground (hue 285), departure-poster yellow as the one
accent and the deep-idle fill, condensed grotesk (Barlow Semi Condensed) with tabular figures, ruled
columns with quiet column heads, hairline rules, a route line with stop marks. No cards for decoration;
rules and columns carry structure.

STORY: The owner sees at once whether last night held and what runs now and next, believes it because
every number has a time and a source, and opens the report or runs Quick proof when in doubt.

FIRST VIEWPORT: Home: the Plan board (From / Profile / Until; current row marked "now" in yellow), then
Last night: the verdict word in the status colour at display size beside its one-line reason, the strip
full width with hour labels at true positions, one ruled row of four figures with small quiet units,
then Open report (filled) and Quick proof (outlined). Warnings sit under the actions, holding their
colour until seen. Report: verdict, full-width strip, the route-line event timetable with Due and Late
columns, then the nights compared on one stacked clock axis.

FORM: Timetable, position 3 on my ordered list (assigned). seed:1b1434f9a0ed4298. Raises adopted:
stacked nights on a shared clock grid (helicorder, doujin density, Crouwel grid); one plot owns the
field (Saville); scale contrast for hierarchy (specimen); warnings keep their alert colour until
noticed (gate board); destructive actions isolated by space, outlined (dev console).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Open

- Light theme: yellow cannot carry deep idle on white; deep goes to violet ink, yellow keeps the
  accent surfaces (the departure poster: black type on yellow). Settled with the tokens.
