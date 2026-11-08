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
  translations; "wait for unlock" is kept as lever B's option `untilUnlock`, off by default (ADR 0017).
  *Default:* as decided; say if any of them matters to you.
- **Q5. The `akane599/AkaDoZe` repository.** Q1 asked to drop every other fork effort; AkaDoZe is a
  separate public repository (last pushed 2026-10-07), not a branch of this fork, and deleting a
  repository cannot be undone. *Default:* left untouched; say "delete AkaDoZe" and it goes.

- **Q7. Design direction (SPEC 10.5).** Three directions for Home and the night report are in
  `docs/design/directions/` and in the P5 review ZIP: A night timetable, B sleep lab (hypnogram),
  C seismograph drum (stacked nights). *Default:* A, with C's stacked nights for comparisons
  (ADR 0028). Say "B" or "C" and the tokens change in `:core:design`.

## Answered

- **Q1. The earlier fork work and the default branch.** *Answer 2026-11-08:* delete every other
  branch, commit and fork effort; keep only the current work and the original repository. Done:
  `main` is the default branch; `Base`, `master` and `codex/android16-reliability` deleted
  (ADR 0006).
- **Q3. Signing key.** *Answer 2026-11-08:* the same key as Coulomb. Wired in P7; the
  evidence provider uses a signature permission (ADR 0006).
- **Q6. Android 16 in the non-negotiables (SPEC 1.3).** *Answer 2026-11-08:* Android 16 is not a
  non-negotiable; 1.3 names Android 17 (API 37) only. `minSdk` stays 34; API 36 and below are
  best-effort with no tests, AVD or AOSP check, and a failed capability probe shows the reason
  (ADR 0022).
