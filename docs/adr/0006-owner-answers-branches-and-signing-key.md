# 0006. Owner answers: old fork branches removed, Coulomb's signing key

- Status: Accepted
- Date: 2026-11-08
- Phase: P1
- Spec: 0.5, 11.3, 12.1, 12.6, 16.3; supersedes the branch part of ADR 0002

## Context

ADR 0002 left the fork's earlier branches (`Base`, `master`, `codex/android16-reliability`)
untouched and kept `Base` as the default branch until the owner answered `docs/questions.md` Q1.
Q3 asked which key signs release APKs (SPEC 12.6 recommends Coulomb's, so the evidence provider in
SPEC 11.3 can use a signature permission).

On 2026-11-08 the owner answered both: drop every other old or new branch, commit and fork effort,
so only the current work and the original repository remain; and sign with the same key as Coulomb.

## Decision

- `main` is the default branch of `akane599/EnforceDoze`. The branches `Base`, `master` and
  `codex/android16-reliability` were deleted from `origin` on 2026-11-08, with the stale local
  remote-tracking refs of the removed `fork` remote. Their heads were `a5b7c4a` (equal to
  `upstream-baseline`), `47057bb` and `a51a5bd`. The merged fork PR #1 stays as GitHub history; it
  cannot be deleted.
- The repository now holds `main` and the tag `upstream-baseline`; `upstream` (Akylas/EnforceDoze)
  stays as the only other remote.
- Release APKs are signed with Coulomb's release key, so the evidence provider is protected by a
  signature permission (SPEC 11.3), not a dangerous one. Coulomb's repository has no release
  signing config yet; the keystore and its password stay outside the repository on build-server
  and are read at build time from local properties or environment variables that are never
  committed. P7 wires the `release` signing config and checks that the certificate digest matches
  the one Coulomb ships with.

## Consequences

- `docs/upstream-audit.md` §5 keeps its credit to the earlier effort's audit, which is now history
  only.
- SPEC 11.3 work in P6 uses the signature-permission branch.
- If the key is not reachable on build-server at P7, that is a hard stop (SPEC 0.5: secrets the
  agent lacks).
