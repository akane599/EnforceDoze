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

- **Q11. The Tasker grant in SPEC 11.1 cannot work.** `pm grant` silently grants nothing to a package
  that does not request the permission (AOSP lines in ADR 0033), and Tasker cannot request it.
  *Default:* the receiver checks a token per client, bound to a package; "Allow Tasker" becomes
  "Automation clients" (ADR 0033, with a second opinion). The Locale plugin goes to the backlog.
- **Q12. The Tasker task XML in `docs/automation.md`.** It follows Tasker's export format (Send Intent,
  action 877) but has not been imported on a phone: there is no Tasker on the emulator. *Default:* the
  page says so and gives the plain Send Intent fields too; the import is checked in the P7 phone session.
- **Q13. Allowlist calls from Tasker (parity row 8).** Upstream's `ADD_WHITELIST` and `REMOVE_WHITELIST`
  broadcasts were open to any app and pasted the package into a shell command, so they are gone
  (ADR 0036). SPEC 11.1's action list has no allowlist action. *Default, after a second opinion:* the
  row is dropped. Unlike every other automation action, an allowlist change is not undone by Restore
  stock, it clashes with Strict's trimmed list, and a leaked token would become lasting exemptions. The
  Allowlist screen and the report's "Add to allowlist" cover it by hand. Say "port" and it is built as
  two token-checked actions, under the conditions in ADR 0036, with a per-client switch that is off by
  default.
- **Q14. One UI's "Never sleeping apps" path.** Onboarding asks for the battery-optimisation exemption
  through the system's own dialog and reads it back with public calls. One UI's sleeping lists have no
  public reading or documented deep link. The step therefore names the path instead: Settings › Battery
  › Background usage limits, "Hiç uyumayan uygulamalar" in Turkish. This comes from One UI 6–8 and has
  not been checked on One UI 9. *Default:* the text stays as it is and is checked read-only in the next
  phone session (`cmd package resolve-activity` and a look at the menu). Say if the S22 may be used for
  it.
- **Q15. The root-tier extras (ADR 0038).** Wi-Fi off, mobile data off and app suspension are engine
  levers at T3, off by default. Four points are the owner's to weigh:
  - **Biometrics off in Doze is dropped.** On API 37 upstream's setting does nothing while a
    platform flag is on, and no power saving is measured. *Default:* dropped; say if you want it
    back.
  - **T3 only.** AOSP lets shell (T2, the owner's Shizuku) run the same commands. *Default:* T3, as
    SPEC 1.2 says; say if they should open to T2.
  - **A VoLTE call with mobile data off, and One UI's behaviour, are unchecked on the phone.** AOSP
    keeps IMS up when data is off. *Default:* the Settings warning says this is unchecked until a
    planned phone session.
  - **The recommender does not tell nights with extras apart.** *Default:* left as is, since every
    profile gets the same help.

- **Q16. Disk space on akane-linux for the emulator suite.**
  - The emulator refuses to boot `ed-api37` unless the disk has as much free space as its 10 GB data
    partition. On 2026-11-16 at 19:14, akane-linux had 9.2 GiB free (97% used).
  - The first suite run at 18:41 still booted. The second run, which checks the P3 fix (ADR 0039), did
    not.
  - This project's own files there are only about 0.6 GB (`~/.cache/enforcedoze-*`). Deleting them
    would not be enough, and everything else on that disk is the owner's.
  - *Default:* the second run waits until about 1 GB more is free. Then it is one command:
    `tools/emulator/suite.sh`. Publishing 1.0.0 waits for that green run too.

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
