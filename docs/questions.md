# Open questions

Non-blocking questions for the owner. Each states the default in use, so work continues meanwhile
(SPEC 0.5). Answered questions move to the bottom with the answer and date.

## Open

1. **The earlier fork work and the default branch (ADR 0002).** `akane599/EnforceDoze` already had
   an earlier Codex effort on `master` (11 commits, fork PR #1, a Java redesign of upstream) and a
   default branch `Base` equal to upstream. SPEC 2 and 12.1 start from upstream, so `main` was
   created from `upstream-baseline`.
   *Default:* the old branches stay untouched and serve only as reference; the default branch stays
   `Base`. Say if `main` should become the default branch, or if the old branches may be archived or
   deleted (deleting is yours to do or to approve).
2. **Name (SPEC 16.3 item 1).** *Default:* "EnforceDoze", as an honest, credited fork with its own app
   id. If you want a name of your own, three collision-checked proposals follow on request.
3. **Signing key (SPEC 16.3 item 2, 12.6).** Release APKs need a key on build-server; the same key as
   Coulomb is recommended so the evidence provider can use a signature permission (SPEC 11.3).
   *Default:* debug signing until P7; P7 stops and asks if no key is available then.
4. **Upstream features the spec does not list (`docs/upstream-audit.md` §6).** Decided so far:
   drop airplane mode in Doze (blocks calls), Bluetooth off (cuts the watch and Android Auto), GPS off
   (emergency location), battery saver on (One UI runs its own), the notification blocklist (binder
   transaction numbers), the auto-rotate fix (obsolete), app shortcuts (v1) and upstream's other
   translations; "wait for unlock" is kept as an option, off by default, pending an ADR in P2.
   *Default:* as decided; say if any of them matters to you.

## Answered
