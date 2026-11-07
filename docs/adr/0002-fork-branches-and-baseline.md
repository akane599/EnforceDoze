# 0002. Fork branches, remotes and the upstream baseline

- Status: Accepted; the branch decisions are superseded by ADR 0006
- Date: 2026-11-07
- Phase: P0
- Spec: 0.4, 12.1, 12.5, 14 (P0)

## Context

SPEC 12.1 asks for a fork of `Akylas/EnforceDoze` in the owner's account with full history, an
`upstream` remote and an `upstream-baseline` tag; SPEC 12.5 names `main` as the integration branch.

The fork `akane599/EnforceDoze` already existed (created 2026-09-17) with three branches:

| Branch | Head | Content |
| --- | --- | --- |
| `Base` (default) | `a5b7c4a` | Identical to upstream `master` on 2026-11-07 |
| `master` | `47057bb` | Upstream plus 11 commits of an earlier Codex effort ("android16-reliability", merged as fork PR #1 on 2026-10-03): a Java redesign touching 138 files |
| `codex/android16-reliability` | `a51a5bd` | The same work before the merge |

SPEC 2 audits upstream 1.10.2 itself (43 Java files, `ForceDozeService` at 1,636 lines), not the
fork's `master`, and SPEC 12.1 rewrites upstream in place as Kotlin modules. The earlier effort
changed the same Java classes in a different direction (Java, upstream app id, a write-ahead journal
instead of `stock.json`/`previous.json`).

## Decision

- `origin` is `akane599/EnforceDoze`; `upstream` is `Akylas/EnforceDoze`.
- `upstream-baseline` is an annotated tag on `a5b7c4a` (upstream `master`, 1.10.2, versionCode 86).
- `main` starts at `upstream-baseline` and is the only integration branch; CI runs on pushes to it.
- The fork's `master`, `codex/android16-reliability` and `Base` are left untouched. Nothing is
  deleted or force-pushed (SPEC 0.5). The earlier work is read as reference only: its audit
  (`AUDIT_REPORT.md` on `master`) reached several of the same findings, credited in
  `docs/upstream-audit.md` §5.
- The fork's default branch stays `Base` until the owner answers `docs/questions.md` Q1.
- The application id stays `com.akylas.enforcedoze` until P1 changes it to
  `io.github.akane599.enforcedoze` together with the SDK levels (SPEC 3.1), so P0 builds upstream
  exactly as released.

## Consequences

Upstream fixes are fetched from `upstream/master` and cherry-picked onto `main` while upstream
classes still exist. If the owner wants the earlier work merged or the old branches removed, that is
a new ADR and an explicit owner action.
