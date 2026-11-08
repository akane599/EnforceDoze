# 0023. Optional work goes last; second opinions only for hard-to-reverse decisions

- Status: Accepted
- Date: 2026-11-08
- Phase: P4
- Spec: 0.5, 14

## Context

The owner set two working rules on 2026-11-08, effective at once:

1. Leave every spec item marked "optional", "isteğe bağlı" or "if time allows" for now. List each
   one in `BACKLOG.md` with a single line: what it is and where it sits in the spec. When the
   required work and the acceptance checklist (SPEC 15) are done, show the backlog to the owner, who
   decides which items get built.
2. Ask a subagent for a second opinion only on decisions that are hard or costly to reverse:
   architecture, a data format, a database schema, a public API, deleting or reverting something.
   Every other choice is made by the agent, with a one-line reason in an ADR, and work moves on.

## Decision

- **Backlog.** `BACKLOG.md` at the repository root holds the optional items, one line each, with
  their SPEC sections. Optional items are not built, and no gate waits for them. A required item
  that is unbuilt because its optional part is deferred shows a visible reason in the app (for
  example lever B's non-zero delay is "waiting: needs an alarm, which this version does not set").
  The backlog goes to the owner after the SPEC 15 checklist is done and before the 1.0.0 release;
  the release itself still waits for the owner (SPEC 0.5).
- **Second opinions.** A fresh read-only subagent is asked only for:
  - architecture: module boundaries, the engine's command model;
  - a data format others read or that outlives a release: the profile file, export ZIPs, the
    provider's columns;
  - a database schema: Room tables and migrations;
  - a public API: the automation intents, the evidence provider;
  - deleting or reverting something.

  The recommender model counts as architecture. Its second opinion was taken before this rule and is
  recorded with it.
- **Smaller choices.** The agent decides and adds a one-line reason to the phase's decision ADR
  ("NNNN. Pn decisions"). A choice that departs from SPEC text still gets its own ADR, as before.
- **Unchanged.**
  - The engine (SPEC 5) and the parsers (SPEC 8) still get their one independent review before their
    gate. That is a review, not a second opinion, and the owner did not change it.
  - The stuck rule still applies.

## Consequences

- SPEC 0.5 and the `AGENTS.md` autonomy table say this, marked "(ADR 0023)".
- The forced-idle delay alarm (SPEC 1.3, 3.2, 5.3, 8.2) and the owner-phone check (SPEC 13.4, 14
  P7) move to `BACKLOG.md`. The acceptance item on the exact-alarm permission holds trivially: the
  app never asks for one.
