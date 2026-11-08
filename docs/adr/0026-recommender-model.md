# 0026. The recommender's input model

- Status: Accepted
- Date: 2026-11-08
- Phase: P4
- Spec: 7.2, 7.3, 7.4, 7.5, 7.6, 12.1

## Context

SPEC 7.6 asks for a pure `recommend(history, answers) -> Recommendation`, tested with at least 12
fixture histories that read as real owners. It does not say what `history` is or where the signals
of 7.2 are computed. Day zero has only the phone's own history (UsageStats screen events,
`batterystats` since the last full charge, the event-log buffer). Later the app's own session
records arrive. D and R are defined "under stock settings". The input model shapes the tests, the
module graph and a public API, so ADR 0023 calls for a second opinion.

The options put to a fresh read-only subagent, in no order:

1. `history` is a list of normalized screen-off sessions, and the policy computes S, D, M, N and R
   itself.
2. `history` is a precomputed signal record, and extraction lives elsewhere with its own tests.
3. Two pure steps inside `:core:recommend`: `Signals.from(sessions)`, then the rules on the
   signals. Fixtures are at the session level; signal tests and rule tests are separate.

No view of my own was recorded before asking. That is a process gap: the subagent's view is
below, and my decisions follow it.

## The second opinion

- **Option 3**, with `:core:recommend` defining the session type and the code that builds sessions
  living outside it.
  - Scenario fixtures read as usage. Boundary tests (exactly 40%, exactly 0.3 %/h) work on the
    signals.
  - The 7.2 definitions are thresholds, and 7.3 wants every threshold in `RecommendationPolicy.kt`.
    That rules out option 2.
  - Option 2 leaks anyway: the night window needs the nights, and the 7.5 gain needs drain per night.
  - `Recommendation` should carry the signals, each measured with its count or unknown with a
    reason, for the why card.
- **Provenance.** Each session is labelled Stock, Tuned or Unknown. D and R use Stock sessions only.
  - M needs a filter too. Lever C and the owner's Tasker setup restrict the sensors, and a session
    shorter than the walk never reaches deep idle without any motion. So count M only on sessions
    with the sensors free that outlast the expected walk by 2 minutes.
  - The owner's own phone has never run stock: its saved settings are tuned, and its history starts
    at a recent full charge. D and R are then unknown, rule 4 is skipped, and the app offers one
    Stock night to measure them. Do not call that history "stock".
- **Feedback** is a third input of dated events: the plan applied and since when, and late arrivals.
  "Picked something else" is card gating, not a rule input. If the two-argument signature must
  stay, nest it in `history`.
- **The night window** belongs inside the pure function: it needs a `ZoneId`, a median around noon
  and correct DST handling. **The Strict card** stays outside `recommend()`, because it follows live
  battery state; it is a separate pure check in the same module.
- **Risks.** The adapters that build sessions are where the bugs will be, for example assuming the
  settings seen at first run held across earlier history. The two layers can drift apart.
- **Spec gaps it found:**
  - confidence by span alone would give High on day zero without D or R;
  - "daytime" is undefined;
  - "available nights" is undefined;
  - rule 1 does not say which source counts;
  - the 7.5 ladder skips Pocket and custom profiles, and Strict → Classic can break the Right-away
    cap;
  - how long a step lasts, and whether it applies to the plan in use or the next recommendation, is
    unstated;
  - `SessionRecorder` counts stock nights by profile id alone.

## Decision

Option 3, as advised. `:core:recommend` depends on `:core:profile` only (CI checks the module
graph).

- **Input.** `History(from, to, zone, sessions, feedback)` and `Answers(urgency)`.
  - A `ScreenOffSession` has its start, end and provenance. Every other fact is nullable, so a fact
    a source did not give is unknown, not zero.
  - `Signals.from(history)` is public, and each signal is `Known(value, sessions)` or
    `Unknown(reason)`.
- **Feedback is nested in `History`**, which keeps the spec's `recommend(history, answers)`.
  - "Picked something else" is `Cards.showChange(recommended, current, lastDeclined)`.
  - The Strict card is `Cards.strictCard(batteryPercent, plugged)`.
- **Provenance and M:** as advised.
  - Day-zero sessions are Stock only when the phone's settings were stock at first run (no string,
    sensors normal, no other manager); otherwise they are Unknown.
  - D and R count only long unplugged Stock sessions.
- **Definitions the spec leaves open:**
  - A daytime session is any session that is not an N night.
  - The short share counts unplugged time only, because Doze does not run on the charger, and a
    charging night would hide a desk worker's glances.
  - An available night is one where an N session could start inside its window, at or after the
    history's start, with 4 h still inside the history.
  - Rule 1 counts screen-off time from any source.
- **Confidence (departs from 7.4):**
  - Low is rule 1.
  - High needs at least 7 days of history and both D and R measured.
  - Everything else is Medium.
- **Feedback loop (fills 7.5):**
  - A step makes the plan in use one notch gentler: Strict → Classic; else the night off; else a day
    profile other than Everyday or Stock → Everyday.
  - The Right-away cap is applied after the step.
  - Only feedback since the plan was applied counts, and for 30 days at most. Applying the gentler
    plan therefore ends the step instead of stepping again.
  - The gain is compared as drain against `R × (1 − 10%)`, so exactly 10% saved is not a step.
- **Rule 4 drops the day-slot reasons** (motion, short sessions) when it caps Pocket or Classic.
  They no longer explain the plan.
- **The night window** is learned as follows:
  - Minutes are counted from noon, and an end is its start plus the clock time to the end. A
    daylight-saving night still ends at 07:00, not 06:00 or 08:00.
  - The median is rounded half up to 15 minutes and padded by 30 minutes.
  - A window is never longer than 23 h 45 min.
- **Later:** the adapters (day-zero sources to sessions, `SessionRow` to sessions) and one shared
  stock test with `SessionRecorder` arrive when the Recommended screen is wired (P5). The P4 gate
  covers the policy.

## Consequences

- SPEC 7.3, 7.4, 7.5 and 7.6 carry `(ADR 0026)` marks for the definitions, the confidence and the
  ladder.
- The owner's phone gets Medium confidence and "Stock not measured" until one Stock night runs. That
  is the honest answer, not a defect.
- Revisit the thresholds once real nights from the owner's phone exist (SPEC 13.4).
