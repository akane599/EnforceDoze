# Open questions

Non-blocking questions for the owner. Each states the default in use, so work continues meanwhile
(SPEC 0.5). Answered questions move to the bottom with the answer and date; numbers never change.

## Open

- **Q5. The `akane599/AkaDoZe` repository.** Q1 asked to drop every other fork effort; AkaDoZe is a
  separate public repository (last pushed 2026-10-07), not a branch of this fork, and deleting a
  repository cannot be undone. *Default:* left untouched; say "delete AkaDoZe" and it goes.

- **Q9. Nights at any hour (ADR 0032).** Two readings of the rule of 2026-11-16 use these defaults:
  - "At most one in 24 hours" is read as: of rests that start less than 12 h apart, only the
    longest. A full 24 h after each night would drop the next sleep whenever it starts earlier on
    the clock (00:30, then 23:30 the next evening), and would delete that earlier night's report.
  - A phone left unplugged on a desk with the screen off for 4 h counts as a night as well, when no
    longer rest is near it.

  Say if either should differ.

- **Q10. Disk on build-server.** The shared Gradle volume (`/mnt/HC_Volume_*`, 50 GB) was at 99–100 %
  on 2026-11-16 (288 MB free after the P5 benchmark build). Most of it is other projects' caches and
  Gradle versions, which I do not touch. *Default:* only this project's builds; no deletions in the
  shared caches beyond Gradle's own old daemon logs. Say if older Gradle caches (8.14, 9.0.0, 9.7.1) or
  `build-cache-1` may go.

## Answered

- **Q2. Name (SPEC 16.3 item 1).** *Answer 2026-11-16:* "EnforceDoze" stays (ADR 0032).
- **Q4. Upstream features the spec does not list.** *Answer 2026-11-16:* every dropped feature is
  approved; "wait for unlock" stays lever B's `untilUnlock` option (ADR 0017, 0032).
- **Q7. Design direction (SPEC 10.5).** *Answer 2026-11-16:* go on with A. The owner may still
  write after seeing the P5 review ZIP (ADR 0028, 0032).
- **Q8. Joined nights (ADR 0031).** *Answer 2026-11-16:* a rising battery ending a night, and
  writing at the first unlock and then extending, are approved. The clock window goes: a night is a
  joined rest of 4 h at any hour, at most one in 24 hours (the longest). When the starts spread
  over more than 3 h of the clock there is no fixed window: the why card says so, and Night is
  offered by hand (ADR 0032; the reading of "24 hours" is Q9).
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
