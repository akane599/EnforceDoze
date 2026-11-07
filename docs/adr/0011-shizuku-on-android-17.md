# 0011. Shizuku on Android 17: a fork-agnostic client, Sui, and ADB grants as the fallback

- Status: Accepted
- Date: 2026-11-08
- Phase: P1
- Spec: 3.2, 4.1, 4.2, 4.3

## Context

SPEC 4.3 asks the UI to explain "the Android 17 Shizuku fix" when the worker does not connect. On
2026-11-08 the newest official Shizuku release is still v13.6.0 (2025-05-25). Issue
RikkaApps/Shizuku#2180 "Android 17" (open since 2026-06-17) reports that authorising new apps fails
on Android 17. Commenters use forks that keep Shizuku's binder protocol: thedjchi/Shizuku
(v13.7.0-thedjchi, 2026-07-02, same application id), kerneldroid/Nightzuku, Shizuku+ and
HmnDev-Tech/shevery. One commenter (2026-07-22) tested app listing and management against all four
on Android 17. The client library is still `dev.rikka.shizuku:api` 13.1.5 (SPEC 12.2).

Two facts from the Shizuku sources shape the fallback: the server hands its binder through the
app's `ShizukuProvider` to apps that request `moe.shizuku.manager.permission.API_V23`, and it treats
that runtime permission, once granted, as the app's authorisation. So
`pm grant <app> moe.shizuku.manager.permission.API_V23` over ADB, followed by a server restart,
authorises the app without the manager's dialog.

## Decision

- The client is fork-agnostic. It uses only the public Shizuku API (binder from the provider,
  permission check, typed UserService) and never checks the manager's package name or signature, so
  any server that keeps the binder protocol works. `Sui.init` runs first, so Sui on rooted phones
  uses the same code path (`WorkerSource.SUI`).
- The bind timeout (5 s) and "unavailable for the session" (4.3) cover a server that accepts the
  bind and never connects. A binder that comes back clears the failure.
- The UI copy (P5) offers, in this order: a maintained fork that keeps Shizuku's protocol (named
  from the list above as checked at release time, none endorsed); Sui on rooted phones; the ADB
  `pm grant … API_V23` workaround for people who already use ADB; and durable ADB grants (tier T1)
  when no worker is wanted at all.
- The emulator scenarios use the `pm grant` route, so P1's contract and tier tests do not depend on
  a dialog.
- The capability report records the server's uid and version, so a phone session shows which
  server ran.

## Consequences

- Re-check issue #2180 and the forks before P7 and update the copy; drop the workaround text if an
  official fix ships.
- The owner's phone runs its own Shizuku setup for Tasker; the phone session reads its version
  without changing it.
