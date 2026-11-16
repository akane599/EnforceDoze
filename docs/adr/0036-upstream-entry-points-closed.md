# 0036. No upstream component is registered

- Status: Accepted
- Date: 2026-11-16
- Phase: P6
- Spec: 0.2 rule 1, 2.3, 12.1

## Context

SPEC 12.1 deletes an upstream class only when every parity row it served has new code with a test.
Until this ADR, the upstream components stayed in the manifest beside the fork's own. While checking
the evidence provider's manifest, I found that several of them were exported with no permission,
and three were dangerous from any installed app:

- **`ADD_WHITELIST` / `REMOVE_WHITELIST`** (`AddWhiteListReceiver`, `RemoveWhiteListReceiver`). They
  ran `Shell.SH.run("dumpsys deviceidle whitelist +" + packageName)`, with the extra pasted into a
  `sh` command line. In this fork the app's uid holds `WRITE_SECURE_SETTINGS` and `DUMP` (the durable
  grants, SPEC 4.1 T1). Any app could therefore run shell commands as EnforceDoze: change global and
  secure settings, or read the app's own files.
- **`ENABLE_FORCEDOZE`** started upstream's `ForceDozeService` and saved `serviceEnabled`, so that
  `BootCompleteReceiver` started it again at every boot. That is a second Doze manager inside the app,
  beside the engine.
- **`CHANGE_SETTING`** rewrote that service's settings, including features the parity table drops for
  device safety (airplane mode in Doze, row 15).

The same was true of upstream EnforceDoze; the fork's grants made it worse. The fork's own screens,
tiles, engine and automation API reach no upstream component, so nothing of the fork depends on
them.

## Decision

- Every `com.akylas.enforcedoze.*` component leaves the manifest (24 of them: activities, receivers,
  services, the notification listener). `MyApplication` stays: it is the application class.
- The classes stay in the tree, inert, until SPEC 12.1 deletes them with the parity table.
  Unregistering them is a manifest change that one revert undoes. No class is deleted, so ADR 0023's
  second opinion for deletions does not apply.
- Upstream's launcher shortcuts go too (parity row 39 drops them for v1). They pointed at
  `com.akylas.enforcedoze` classes in a package that is not this app's id, so they never resolved.
- `ManifestSafetyTest` keeps it that way:
  - no upstream component may be registered;
  - every exported component needs a permission, with two exceptions: the launcher, and the
    automation receiver (token per call, off by default, ADR 0033).

## Consequences

- Parity rows 8 (allowlist broadcasts), 30 (Tasker broadcasts) and 31 (re-enter Doze) no longer run
  upstream code.
  - Rows 30 and 31 have their new home: `PAUSE`, `RESUME` and `APPLY_PLAN` in the automation API.
  - Row 8 has none yet. SPEC 11.1's action list has no allowlist action, so the decision is recorded
    in `docs/questions.md` with its default.
- A phone that ran an earlier build of this fork keeps upstream's preferences file, but nothing reads
  it any more.
