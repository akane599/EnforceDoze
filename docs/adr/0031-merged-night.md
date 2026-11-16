# 0031. A night joins sessions across short wake-ups

- Status: Accepted; the clock window is superseded by ADR 0032
- Date: 2026-11-08
- Phase: P5
- Spec: 7.2, 7.3, 8.5, 13.2

## Context

SPEC 8.5 generated a night report "at the first unlock after an unplugged session of ≥ 4 h". SPEC
7.2 counted an N night as "unplugged screen-off sessions of ≥ 4 h starting between 21:00 and 03:00".
Both treated one screen-off session as one night. A glance at the clock at 3 am then split one night
into two short sessions. Neither might reach 4 h, so the night was lost from the report and from N,
and the half that remained was compared as if it were the whole night.

The owner changed the definition on 2026-11-08:

> Unplugged screen-off sessions inside the night window count as one night when the screen-on gaps
> between them are shorter than 10 minutes. The night report (8.5) and the N signal (7.2, 7.3) use
> this joined night. The 4-hour threshold applies to the joined night's total screen-off time. The
> report's timeline strip marks the short wake-ups in between.

That is the decision. The night row is a database schema and the write timing is hard to reverse,
so ADR 0023 calls for a second opinion. Both views are recorded below.

## Decision

### The rule (one place: `Nights` in `:core:profile`)

- A night is a run of unplugged screen-off sessions in start order. Each session joins the one
  before it when the screen was on for less than 10 minutes in between.
- A run ends at:
  - a gap of 10 minutes or more;
  - a session that saw the charger (plugged at screen off, or charged during it);
  - a battery level that rose during the screen-on gap. No session saw that charge, but Doze did
    not run through it. "Nights spent on the charger do not count" (SPEC 7.3).
- **The window applies to the first session only**: it starts between 21:00 and 03:00. A night that
  begins at 23:00 and goes past 03:00 after a wake-up is one night.
- **The threshold counts screen-off time only.** The wake-ups are left out of the 4 hours.
- The report and Recommended share this rule and its constants. `RecommendationPolicy` keeps its
  names (`NIGHT_LENGTH`, `NIGHT_STARTS_FROM`, `NIGHT_STARTS_UNTIL`, `NIGHT_JOIN_GAP`), and they point
  at `Nights`. The rule now gives the report the window it lacked: 8.5 named none, and N always had
  21:00–03:00. A screen-off stretch that starts at noon is not a night, however long it is.
- **The compressed night scales the whole rule** (SPEC 13.2). A 4-minute threshold makes a
  10-second join gap and drops the clock window, so the emulator test runs at any hour. Its two
  back-to-back test nights are minutes apart and stay two nights.

### The night's verdict and figures

- **Verdict:**
  - Held when every judged session held.
  - Did not doze when no judged session dozed.
  - Partly otherwise.
  - Not judged when no session was judged.
  - A session too short to judge does not count either way. The cause of Did not doze is the
    longest undozed session's.
- **Summed over the sessions:** time in deep idle, maintenance windows and the app's own wakeups.
  Own wakeups are unknown if any session's count is (ADR 0019).
- **Weighted by session length:** drain.
- **Deep share:** deep idle over the judged sessions' time.
- **Wakers:** each app and kernel reason once, its counts summed, ranked as the recorder ranks them.

### The row (second opinion adopted)

- `night.sessionId` stays the first session: a unique foreign key with cascade delete.
- New fields:
  - `start` and `end`: the first screen off and the last screen on;
  - `sessionCount`;
  - `screenOffMillis`;
  - the night's own `drainPerHour` and `deepShare`;
  - `profileId`: null when the sessions ran different profiles, so the night is a baseline for
    neither.
- New index on `start`.
- Comparisons read night rows (`nightsBefore`), not first sessions.
- A night's sessions are the stored sessions inside `start..end` (`recordsOf`).
- An export attaches a night to every session inside it. A night's Export exports all of it.
- The database stays at version 1, changed in place. The app has never been released or installed
  outside the emulator, and the emulator gate starts from a clean install.

### Write timing (second opinion adopted)

At every unlock, the recorder groups the last day's sessions into nights:

- a night with no row yet is written then;
- a row whose first session is the run's first session, but whose `sessionCount` is smaller, is
  updated in place, keeping its id;
- a row already there for part of a run, but starting at another session, is left alone: the run
  began before the lookback.

A glance at the clock at 03:00 after five hours of sleep therefore writes the night. The sessions
after it extend that row at the morning unlock.

## The second opinion (fresh read-only subagent, given the code and the options, no preference)

- **Schema:** "A" with additions, as adopted above.
- **Against a join table (night ↔ session):** two writes for every night, and retention could cut
  a night down to its later sessions without anyone noticing.
- **Against a night id on the session rows:** stored sessions would be mutated after the fact, and
  the export would carry derived data inside the raw record.
- **Write timing:** write at the first unlock once the run reaches the threshold, then extend.
  - Waiting until the night is closed delays the report past the unlock the spec ties it to.
  - It also needs a second trigger with nothing to hang it on: there is no alarm or job.
- **Pitfalls named:**
  - the recorder, Signals and the feedback loop must share one window;
  - a night can span two profiles, which breaks a Stock filter on the first session;
  - sessions under 10 minutes have no transitions, so they show as no record, not as zero deep idle;
  - a charge during a screen-on gap must end the night.

  Each is handled above.

## My view before the second opinion

- **Schema:** the same direction.
- **Write timing:** I preferred one write, once a night is closed: 10 minutes of screen on with no
  new session, or a plug-in.
  - For: the row never changes, and the comparison is taken once.
  - Against: the morning unlock is usually under 10 minutes after the last screen on, so the
    report would wait for a later unlock or a timer. Holding a wakeup for it is self-inflicted
    drain (SPEC 0.2).
- The extension rewrites only the row's own figures. The comparison is recomputed with the same
  earlier nights, so "later nights do not change it" still holds.

## Consequences

- SPEC 7.2 (N row), 7.3 (the definitions paragraph) and 8.5 (when a report is generated; strip
  item 2) now say "joined night" and point here.
- The strip has a wake-up mark: a slot of ground under an ink cap, at least 3 dp wide. The Timeline
  tab adds a "Screen on" key entry and a hollow "Screen on for N min" stop. Spoken descriptions
  count the wake-ups.
- Still open, for the `:app` mapper (P5 wiring):
  - Home's and the report's reason line for a Partly night that has no break and no late arrival.
    Its Partly comes from a session that never dozed, so the line names that part instead of "0
    breaks".
- `docs/questions.md` Q8 lists the defaults the owner may want changed.
