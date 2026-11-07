# 0005. /impeccable init answered from the spec; DESIGN.md starts as a seed

- Status: Accepted
- Date: 2026-11-07
- Phase: P0
- Spec: 0.1, 0.2, 0.3, 10.5

## Context

SPEC 0.1 lists `PRODUCT.md` and `DESIGN.md` "from `/impeccable init`, aligned with sections 1 and
10" among the P0 files. Impeccable 4.7.1's `init` runs an interview with at least one answer round
and writes only `PRODUCT.md`; `DESIGN.md` comes later from its new-work or document flows. SPEC 0.3
rule 1 forbids asking questions the spec answers, and SPEC 0.2 ranks the spec's rules above
`/impeccable` rules. SPEC 10.5 places token and type decisions at P5 (`init` at P5 start, `shape`
before screens).

## Decision

- `PRODUCT.md` follows Impeccable's product schema 1 and is condensed from SPEC 1, 3.3, 7–11; its
  header says so. No interview: every answer it would ask for (users, purpose, positioning,
  constraints, platform `android`) is in the spec.
- `DESIGN.md` is a seed holding only what SPEC 10 pins (direction, avoid list, token roles, contrast,
  motion and accessibility rules). It carries no colour, type, spacing or motion values; the P5
  `shape` step chooses them, records them in `DESIGN.md` and in an ADR, and the Compose theme is then
  generated from it (SPEC 10.2).
- Impeccable's build-path and live-mode setup steps do not apply: app screens are built code-first
  and the project is native Android.

## Consequences

When P5 starts, `/impeccable` runs against these files; anything the shape step changes in
`PRODUCT.md` goes through an ADR because it restates the spec.
