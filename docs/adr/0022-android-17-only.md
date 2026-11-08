# 0022. Android 17 is the only promise; 36 and below are best-effort

- Status: Accepted
- Date: 2026-11-08
- Phase: P4
- Spec: intro, 0.3 rule 2, 0.4, 1.3, 3; partly supersedes ADR 0021

## Context

ADR 0021 dropped every test below API 37. SPEC 1.3 still named Android 16 (API 36) as a
non-negotiable, which only the owner can change (SPEC 0.5), so `docs/questions.md` Q6 asked.

The owner answered on 2026-11-08:

- Android 16 is not a non-negotiable; 1.3 reads "Works on Android 17 (API 37) on the Galaxy S25
  Ultra with One UI 9."
- `minSdk` stays 34. API 36 and below are best-effort:
  - no tests;
  - no AVD;
  - no AOSP check.
- If something does not work there, a capability probe shows a visible reason, and that is all.
- SPEC 0.3's "verify against AOSP for API 36 and 37" and the environment table's API 36 AVD row
  now apply to API 37 only.

## Decision

- **SPEC 1.3** reads: "Works on Android 17 (API 37) on the Galaxy S25 Ultra with One UI 9."
- **SPEC 0.3 rule 2 and AGENTS.md rule 3:** a command, flag, key, tag or hidden API is checked
  against AOSP `android-17.0.0_r1`, or probed on the device. Nothing is checked for 36 or below.
- **SPEC 0.4:** the environment table already lists only `ed-api37` (ADR 0021). No AVD below 37
  is created or used.
- **API 34–36 are best-effort.** Code is not written or held back for them. A lever or source that
  does not work there is a failed capability probe (SPEC 4.4), and that failure:
  - appears on the Privileges screen with its reason;
  - makes the lever show as "waiting" or "partly applied" with that reason.
  A failed probe never crashes the app and never fails silently.
- **Kept as they are:**
  - Existing code comments and ADRs that cite `android-16.0.0_r1` (ADR 0012, 0013, the upstream
    audit). They record what was checked at the time.
  - The API 35 and 36 fixtures, on ADR 0021's terms: they stay while they pass and may be dropped
    when an API 37 change disagrees with them.
- **Summaries updated** to name Android 17: the SPEC intro, SPEC 3's opening line, `AGENTS.md`,
  `PRODUCT.md` and `DESIGN.md`.

## Consequences

- ADR 0021's line "reading AOSP source, still for both `android-16.0.0_r1` and
  `android-17.0.0_r1`" is superseded: only 17 is read.
- Q6 is answered and moves to the bottom of `docs/questions.md`.
- No acceptance item, gate or review may ask for Android 16 behaviour.
