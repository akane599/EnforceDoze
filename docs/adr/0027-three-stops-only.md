# 0027. Three stops only

- Status: Accepted
- Date: 2026-11-08
- Phase: P4
- Spec: 0.5, 10.6, 14

## Context

The owner's instruction of 2026-11-08: no waiting at or between phase gates through to the last
phase; never end a turn with a question; stop only to connect to the phone, for something
irreversible or public, or for a secret the agent lacks. Every other question gets the most
reasonable choice, an ADR line and an entry in the questions file. A needed change to a
non-negotiable is parked, not made. The "P3 design review" stop in SPEC 0.5 no longer applies: the
design review is the P5 review ZIP, sent without waiting.

## Decision

- SPEC 0.5 and `AGENTS.md` list three stops: connecting to the phone; anything irreversible or
  public (releases, force-push, deleting data or branches); a secret the agent lacks.
- Any other question: the agent picks the most reasonable option, records it in an ADR, adds the
  question to `docs/questions.md` (the owner wrote QUESTIONS.md; this is the project's existing
  questions file, kept so links stay valid) and continues. A different answer later means the ADR
  is updated and the work corrected.
- A change to a non-negotiable (SPEC 1.3) is never made by the agent: the item is parked with a
  question and other work goes on.
- The "P3 design review" stop is gone. At the P5 gate the review ZIP goes to the owner and work
  continues (SPEC 10.6, 14).
- A gate gets one short status note, and the next work starts in the same turn. No turn ends with a
  question to the owner.

## Consequences

Supersedes the stop list of SPEC 0.5; ADR 0023's second-opinion rule still holds.
