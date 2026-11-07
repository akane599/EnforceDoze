# Open questions

Non-blocking questions for the owner. Each states the default in use, so work continues meanwhile
(SPEC 0.5). Answered questions move to the bottom with the answer and date; numbers never change.

## Open

- **Q2. Name (SPEC 16.3 item 1).** *Default:* "EnforceDoze", as an honest, credited fork with its own app
  id. If you want a name of your own, three collision-checked proposals follow on request.
- **Q4. Upstream features the spec does not list (`docs/upstream-audit.md` §6).** Decided so far:
  drop airplane mode in Doze (blocks calls), Bluetooth off (cuts the watch and Android Auto), GPS off
  (emergency location), battery saver on (One UI runs its own), the notification blocklist (binder
  transaction numbers), the auto-rotate fix (obsolete), app shortcuts (v1) and upstream's other
  translations; "wait for unlock" is kept as an option, off by default, pending an ADR in P2.
  *Default:* as decided; say if any of them matters to you.

## Answered

- **Q1. The earlier fork work and the default branch.** *Answer 2026-11-08:* delete every other
  branch, commit and fork effort; keep only the current work and the original repository. Done:
  `main` is the default branch; `Base`, `master` and `codex/android16-reliability` deleted
  (ADR 0006).
- **Q3. Signing key.** *Answer 2026-11-08:* the same key as Coulomb. Wired in P7; the
  evidence provider uses a signature permission (ADR 0006).
