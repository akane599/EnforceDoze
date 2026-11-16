# 0035. The evidence provider's contract for Coulomb (v1)

- Status: Accepted
- Date: 2026-11-16
- Phase: P6
- Spec: 11.3, 16.4; amends both

## Context

SPEC 11.3 asks for a read-only `ContentProvider` (`…enforcedoze.evidence`, with `status` and
`sessions`) that the owner's Coulomb app (`io.github.akane599.coulomb`) reads to show "Doze is managed
by EnforceDoze" with the last verdict and a link to the report. It must be protected by a signature
permission when both apps share a signing key, or otherwise by a dangerous permission granted through
the worker. Coulomb ships against this contract, so changing it later is costly (ADR 0023: a public
API gets a second opinion).

## Options and the second opinion

My first sketch had four parts:
- `sessions` as raw rows;
- `status` mirroring the automation API's `GET_STATUS` extras;
- an intent URI column for the report link;
- the signature permission alone.

A fresh read-only reviewer weighed every open choice without a preferred answer. They recommended:
- **Rows:** raw sessions only, with the night-aware "last verdict" in `status`. A session's night can
  change later (ADR 0032), and the report opens on the night around any session.
- **Columns:** a mapper with fixed column constants, never `SELECT *`. Leave out `tier` (wrong in a
  cold process), the plan as a display line, and internal warning names.
- **Versioning:** an `api_version` column in `status`; changes add, never rename or remove.
- **Report link:** a permission-protected `<activity-alias>` with action `OPEN_REPORT` and a long
  extra `session_id`. Coulomb starting an intent string it read from another app's data would be
  intent redirection.
- **Permission:** `signature`, plus a check of the calling package. A signature permission alone lets
  in every app signed with the owner's key (Chinagraph too). No dangerous permission: any app could
  ask the user for it, and ADR 0033 gave the worker no grant command.
- **Paging:** `QUERY_ARG_LIMIT`, reported in `EXTRA_HONORED_ARGS`; selection and sort arguments are
  refused.
- **Change notification:** `notifyChange` on both paths. Coulomb observes only while its screen is
  visible and catches `SecurityException`.
- **Threading:** an `onCreate` that does nothing, and blocking DAO reads on the binder thread.

They also found two bugs in the shipped `GET_STATUS`:
- Its "last verdict" was the raw last session, while Home shows the night around it.
- It sent `deep_share = 0.0` for a session that was never judged, a made-up zero (ADR 0019).

I agreed with all of it.

## Decision

- **Access.** Permission `io.github.akane599.enforcedoze.permission.READ_EVIDENCE`, protection level
  `signature`, set as the provider's `android:permission`, so it covers reading and writing.
  - Insert, update, delete and `call` are not supported.
  - The provider also checks `getCallingPackage()`: Coulomb only. Debug builds also accept the app's
    own instrumentation package, for the emulator test.
- **`content://io.github.akane599.enforcedoze.evidence/status`**: exactly one row.

  | Column | Type | Value |
  | --- | --- | --- |
  | `api_version` | INTEGER | 1 |
  | `state` | TEXT | `MANAGED`, `PAUSED`, `HANDS_OFF`, or `OFF` when the engine is switched off |
  | `profile` | TEXT | the profile id the engine runs, or null |
  | `profile_name` | TEXT | that profile's name as the owner sees it |
  | `report_session_id` | INTEGER | the session the report opens on, or null |
  | `start_ms`, `end_ms` | INTEGER | the last session, or the night it belongs to; epoch ms |
  | `verdict` | TEXT | `HELD`, `PARTLY`, `DID_NOT_DOZE`, or null when not judged |
  | `deep_share` | REAL | 0–1, null when not judged |
  | `drain` | REAL | %/h, null when charging or not measured |

- **`content://io.github.akane599.enforcedoze.evidence/sessions`**: one row per screen-off session
  kept, newest first.
  - Columns: `_id`, `start_ms`, `end_ms`, `verdict`, `deep_share`, `drain`, `profile`, `plugged`
    (0/1).
  - `_id` is the session id, which `OPEN_REPORT` takes.
- **Query rules.**
  - `QUERY_ARG_LIMIT` is honoured.
  - A selection, sort order or unknown projection column is an `IllegalArgumentException`.
  - Unknown enum values map to a neutral display on Coulomb's side.
- **Report link.** The activity alias `io.github.akane599.enforcedoze.EvidenceReport` targets
  `MainActivity` and is protected by the same permission.
  - It takes action `io.github.akane599.enforcedoze.action.OPEN_REPORT` with the long extra
    `session_id`.
  - A session that no longer exists opens the Log instead.
- **Changes.** EnforceDoze calls `notifyChange` on both paths when a session is stored or the engine's
  mode or profile changes.
- **One "last verdict".** `GET_STATUS` and the provider use the same function: the last session, or its
  night when it has one, with the night verdict Home shows. `deep_share` is null when there is no
  verdict.
- **SPEC.** 11.3 and the 16.4 note are amended to match. The dangerous-permission fallback is gone.
  11.1 now points at ADR 0033.

## Consequences

- Coulomb declares `<uses-permission android:name="io.github.akane599.enforcedoze.permission.READ_EVIDENCE"/>`
  and `<queries><package android:name="io.github.akane599.enforcedoze"/></queries>`.
- Coulomb queries off its main thread and reads each cursor fully. It treats a null cursor,
  `SecurityException` or `IllegalArgumentException` as "unavailable".
- Coulomb's wording follows `state`: "managed" only while it is `MANAGED`.
- On Android 14 and later, a signature permission defined after the requesting app was installed
  should be granted when the defining app arrives. The emulator test installs in both orders and reads
  the grant, rather than trusting this.
- Debug builds of both apps are signed with the build server's debug key, so the signature matches
  there too. Release builds share the Coulomb key (Q3).
