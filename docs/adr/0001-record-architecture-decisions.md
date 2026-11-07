# 0001. Record architecture decisions

- Status: Accepted
- Date: 2026-11-07
- Phase: P0
- Spec: 0.1, 0.3, 0.5

## Context

SPEC 0.1 says `SPEC.md` changes only through an ADR. SPEC 0.3 rule 2 asks that every command, flag,
key, tag and hidden API be verified and recorded, and SPEC 0.5 asks that costly-to-reverse decisions
record the second opinion next to the final call.

## Decision

ADRs live in `docs/adr/NNNN-kebab-title.md`, numbered in order, written from `0000-template.md`. An
accepted ADR is never edited except to set "Superseded by NNNN". Decisions that stay inside the spec
and are cheap to reverse are not recorded. An ADR that changes `SPEC.md` names the sections it
changes, and the edited spec text points back to the ADR.

## Consequences

`docs/questions.md` holds non-blocking questions with the default in use; when an answer changes a
decision, a new ADR supersedes the old one.
