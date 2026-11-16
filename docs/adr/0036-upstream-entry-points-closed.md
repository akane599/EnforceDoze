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
  - Row 8 is dropped; see below. Q13 lets the owner overrule this.
- A phone that ran an earlier build of this fork keeps upstream's preferences file, but nothing reads
  it any more.

## Row 8: allowlist calls, with a second opinion

Porting row 8 would add allowlist actions to the automation API, a public API, so a fresh read-only
reviewer weighed three options without a preferred answer:
- A: two actions, `ALLOWLIST_ADD` and `ALLOWLIST_REMOVE`;
- B: one action with `on`;
- C: drop the row.

**My first default was A**: the audit had marked the row "port", and SPEC 2.3 ports everything in 2.1.

**The reviewer recommended C:**
- SPEC 11.1 lists no allowlist action, so A departs from the spec, while a drop with a reason is what
  SPEC 2.3 allows.
- An action can be added after 1.0, but it can never be taken back.
- It would be a new kind of power for a token. Every current action stays inside the engine's
  guardrails, and Restore stock or Resume undoes it. An allowlist change does neither: Restore stock
  puts back only what a trim captured.
- It collides with lever D. While a trimmed profile holds the list, a call reads as drift, and the
  put-back undoes it or brings back an app that was removed.
- A leaked token would become lasting exemptions for any installed app, including one the client
  controls. Exempt apps may also start foreground services from the background.

The reviewer argued against B as well: `on` fits a switch, not a list. If the owner wants A anyway,
the conditions are:
- refuse EnforceDoze itself and the client's own package;
- refuse system entries;
- answer "unavailable" while lever D holds the list;
- report from the read-back;
- give each client its own switch, off by default.

**Decision: C.** I agree. Row 8 is dropped, and the owner edits the allowlist by hand: the Allowlist
screen, and "Add to allowlist" in the night report. The port, under the conditions above, is a
`BACKLOG.md` item.

