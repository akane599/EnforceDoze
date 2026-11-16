# 0034. P6 decisions

- Status: Accepted
- Date: 2026-11-16
- Phase: P6
- Spec: 11

## Context

ADR 0023: choices that are cheap to reverse are made by the agent, each with a one-line reason here.
ADR 0033 replaces SPEC 11.1's permission with a token per client. This ADR holds the smaller choices
around it.

## Decision

- **Automation calls are journal events (`AUTOMATION`), not action rows.** Action rows record what
  the engine wrote, with read-back. A call is a request: a dropped or rejected call writes nothing, and
  an accepted one leads to the engine's own action rows. The event keeps the client's package in
  `writer`, which SPEC 11.1's "caller's package" asks for.
- **`SENSORS_OFF` without `on` means on.** The action's name says what it does; `on=false` undoes it.
- **A call whose engine cannot start answers "unavailable".** From the background, Android refuses a
  foreground service start unless the app is exempt from battery optimisation (API 31+). `EngineAccess`
  now catches that refusal, puts the engine switch back, and returns the same "engine did not start"
  outcome a screen gets. A call from outside never crashes the app; any other exception is logged as
  `failed` with the client.
- **The status reply's extras are lowercase names** (`plan`, `profile`, `mode`, `tier`, `verdict`,
  `deep_share`, `drain`, `warning`), so Tasker turns them into local variables as they are. `mode` is
  added to SPEC 11.1's list, because a paused plan and a running plan otherwise look the same.
- **Tasker is offered with one tap; any other app goes through the app picker.** The token is shown
  once in a dialog with a copy button, marked sensitive on the clipboard.
- **Reset removes every client** and disables the receiver, like the rest of the app's records.
