# 0032. The owner's answers of 2026-11-16: nights at any hour

- Status: Accepted
- Date: 2026-11-16
- Phase: P5
- Spec: 7.2, 7.3, 7.6, 8.5, 13.2, 16.3; supersedes ADR 0031's clock window

## Context

On 2026-11-16 the owner answered the open questions Q2, Q4, Q7 and Q8 in `docs/questions.md`:

> Q2: "EnforceDoze" stays. Q4: every dropped feature is approved. Q7: go on with default A; if
> the ZIP changes my mind, I will write. Q8: the second and third defaults are approved. The first
> changes: I keep irregular hours and sometimes sleep by day. The night report and the N signal in
> Recommended must not look at the clock. At any hour of the day, a joined unplugged screen-off
> rest of at least 4 h in total is a "night", at most one per 24 hours (the longest). For the
> Night profile's learned window: if my start times deviate from each other by more than 3 h, do
> not offer a fixed window; say why on the "why" card and offer Night to be turned on by hand,
> from the tile or by automation.

## Decision

### Q2, Q4, Q7

- **Q2:** the name stays "EnforceDoze" (SPEC 16.3 item 1).
- **Q4:** every upstream feature dropped in `docs/upstream-audit.md` §6 stays dropped. "Wait for
  unlock" stays lever B's `untilUnlock` option (ADR 0017).
- **Q7:** direction A, the night timetable (ADR 0028). The owner may still write after seeing the
  P5 review ZIP.

### Q8: what a night is

- **No clock window.** A rest is a joined run of unplugged screen-off sessions (ADR 0031: screen-on
  gaps under 10 minutes, a charger or a rising battery ends it). A rest with 4 h of screen off in
  all is a night at whatever hour it starts. ADR 0031's 21:00–03:00 window for the first session is
  gone, from the report and from N alike.
- **At most one in 24 hours, the longest.** Of two rests that start less than 12 hours apart, only
  the longer is a night; a tie keeps the night already there. Each night is then the longest rest
  in the 24 hours around its start. A nap and the sleep on the same day are one night.
- **Why 12 hours either side, not a full day after each night.** Read literally, "no two nights
  within 24 hours" drops the next sleep whenever it starts earlier on the clock than the last one:
  00:30 one night and 23:30 the next are 23 hours apart. Under the report's rules that would also
  delete the earlier night's report. The window centred on each rest keeps both sleeps and still
  folds a nap into the day's sleep. This reading is mine, so it is Q9 in `docs/questions.md`.
- **Decided as they come.** The recorder decides at each unlock, from the night rows near a rest:
  - a new night is written;
  - a longer rest that starts less than 12 h from an earlier night takes its place: that night row
    is deleted, and its sessions keep their own session reports;
  - a shorter one is left as sessions.

  `Nights.keep` applies the same rule to the history, so N and the reports agree.
- **Available nights** (rule 3's denominator) are one a day: how many days' nights, each with its
  4 h, fit inside the history.
- **The compressed night test** (SPEC 13.2) keeps its scaled threshold and join gap, and turns the
  12-hour spacing off. Its two test nights run minutes apart and must stay two.

### Q8: the learned window

- **The spread** is the smallest stretch of the clock that holds every night's start: 24 h less
  the widest gap between starts, so a spread can cross midnight or noon.
- **Up to 3 h:** the window is learned as before (median start and end, rounded to 15 minutes,
  padded by 30 minutes). The clock is now cut at the widest gap instead of at noon, so a day sleeper
  gets a window by day.
- **Over 3 h:** no night slot. The why card says `IrregularNights` (the nights, of how many, and the
  spread), and the note `NIGHT_BY_HAND` offers Night from the tile or an automation (SPEC 11).
  - "Right away" messages still drop Night, by hand too.
  - A complaint does not withdraw the by-hand offer: the owner switches Night themselves.

## Alternatives considered

- **A full 24 h after each night** is the literal reading. It loses about every other night for
  anyone whose bedtime moves earlier now and then, and it deletes reports the owner has read.
- **Calendar-day buckets** need a boundary on the clock. For someone who sleeps at any hour, every
  boundary splits some nights, which is the problem the owner asked to remove.
- **Keep naps as nights.** The owner asked for the longest only.

## Consequences

- SPEC 7.2 (N row), 7.3 (rule 3 and the definitions), 7.6 (two new fixtures, an irregular sleeper
  and a day sleeper), 8.5 (when a report is written) and 13.2 now follow this ADR. ADR 0031's
  window is superseded; its joining, its schema and its write timing stand.
- A phone left unplugged on a desk with the screen off for 4 h now counts as a night too, when no
  longer rest is near it. Two existing tests used such desk stretches and were shortened to keep
  their intent. Q9 asks whether the owner wants that.
- The Night profile's purpose reads "your sleep, at whatever hour".
- The Recommended screen (P5) words `IrregularNights` and `NIGHT_BY_HAND`. The tile that switches
  Night by hand comes with SPEC 11 in P6.
