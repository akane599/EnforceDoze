# 0033. The automation API checks a per-client token, not a granted permission

- Status: Accepted
- Date: 2026-11-16
- Phase: P6
- Spec: 11.1, 9.1 (Settings), 4.3

## Context

SPEC 11.1 protects the automation receiver with the custom permission
`io.github.akane599.enforcedoze.permission.CONTROL` (protection level dangerous). An "Allow Tasker"
switch has the worker run `pm grant <tasker package> <permission>`.

This cannot work, because a runtime permission can only be granted to a package whose manifest
requests it. At `android-17.0.0_r1`, `frameworks/base/services/permission/java/com/android/server/permission/access/permission/PermissionService.kt`
lines 1080–1087 handle a permission the package did not request: when there are no old flags, the
service logs `Permission … isn't requested by package …` and returns. It does not throw.
`PackageManagerShellCommand` fails only on an exception, so `pm grant` exits 0 and grants nothing.

Tasker, MacroDroid and Automate cannot declare this app's permission. Built as written, the switch
would report "Tasker allowed" while nothing changed, which breaks the honesty rule (SPEC 0.3) as well
as the feature. No other protection level helps a package that does not request the permission:
- the same check guards flag updates (line 1625);
- a permission that is neither runtime nor development is not changeable (line 1066).

## Options

1. **An exported receiver with no permission, checked by a token per client.** The owner adds a
   client, such as Tasker, and gets a random token bound to that package. The token goes into the
   intent as an extra.
2. **The Locale/Tasker plugin protocol**, with a token minted in the plugin's edit activity. Hosts
   open that activity with `startActivityForResult`, so `getCallingPackage()` names the host.
3. **A different protection level.** Not possible, as shown above.
4. **Sender identity** (`BroadcastReceiver.getSentFromUid`, API 34+). It is filled in only when
   the sender opts in with `BroadcastOptions.setShareIdentityEnabled`
   (`BroadcastQueueImpl.java` L1305–1333, `BroadcastRecord.java` L511, `BroadcastReceiver.java`
   L729–741). Automation apps do not opt in.
5. **A `ContentProvider.call()` entry point**, where the binder caller is reliable. As far as we know,
   none of the common automation apps can make that call from an ordinary action.
6. **Keep the permission** for tools that can declare it. None of the common ones can.

## Second opinion (ADR 0023)

A fresh read-only reviewer was given the spec, the AOSP files and the options without a preferred
answer.
- **Premise:** they verified the AOSP lines and corrected one point. The grant does not throw; it
  silently does nothing. Any grant that is kept must be checked with `checkPermission`, never with
  the exit code.
- **Recommendation:** option 1, with one token per client bound to a package.
  - The reply to `GET_STATUS` goes only to the bound package (`setPackage`).
  - A bad token is dropped before any engine or worker call, gets no reply, and is logged as at most
    one row a minute.
  - Turning automation off disables the receiver component.
  - Tokens live outside backup.
  - The rate limit covers every action that changes state.
  - The sender's package is logged when it shares it.
- **Option 2** is the most accurate, but it costs an edit UI, settings versions and per-host quirks.
  It goes to `BACKLOG.md`.

## Decision

Option 1, as the reviewer recommends.

- **Receiver.** One exported receiver, `AutomationReceiver`, with no permission. It is disabled with
  `PackageManager.setComponentEnabledSetting` whenever Settings › Automation API is off, so nothing
  starts the app then. Actions are `io.github.akane599.enforcedoze.action.<NAME>` for the seven SPEC
  11.1 names.
- **Clients.** Settings › Automation lists the clients. When Tasker is installed and has no token,
  it is offered with one tap; "Another app" binds a token to any installed package the owner picks. Each client has a 128-bit
  token from `SecureRandom`, shown once with a copy button (`EXTRA_IS_SENSITIVE`) and stored only as
  its SHA-256 hash in `noBackupFilesDir`. Hashes are compared with `MessageDigest.isEqual`. Removing a
  client or making a new token revokes the old one.
- **The caller's package (SPEC 11.1)** is the package the token is bound to. When the sender shares
  its identity, `getSentFromPackage()` must match it, or the call is rejected.
- **Rate limit.** At most one state-changing action per 30 s from automation, for all clients
  together. Extra calls are logged and dropped. `GET_STATUS` is not limited.
- **Log.** Every accepted, dropped or rejected call is a journal event (`AUTOMATION`). The row holds
  the bound package (or "unknown" for a bad token), the action and the result. Bad-token rows are
  merged to at most one a minute.
- **Commands.** Actions go through the same engine commands and guardrails as the UI. A Tasker call
  cannot do anything the owner's own tap could not, and calls, alarms and emergency alerts stay safe
  (SPEC 1.3).
- **Worker.** It gains no "grant to another package" command.

## Consequences

- SPEC 11.1's permission and "Allow Tasker" are replaced by the token and "Automation clients". The
  settings line for Tasker shows whether Tasker has a token.
- `docs/automation.md` shows the token as a Tasker global variable (`%ED_TOKEN`), so the task XML
  examples hold no secret.
- Anyone holding a token can run the seven actions, within the engine's guardrails. A leaked token is
  revoked by making a new one.
- The Coulomb evidence provider is a separate case. Coulomb declares the permission itself, and both
  apps share a signing key, so a signature permission works there (SPEC 11.3).
