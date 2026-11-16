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
- **`SENSORS_OFF` needs `on`, as a boolean or the text `true`/`false`.** The tile asks before it
  turns sensors off, because the camera and microphone stop too; a call without `on` is rejected
  rather than taken as "on". Tasker sends extras as text, so the text form is accepted.
- **Calls are checked in this order: token, sender, extras, rate limit.** A call with a bad extra
  (an unknown profile id) is rejected without using up the 30 s window, and a call without a token
  never has its extras read. The window runs on `SystemClock.elapsedRealtime`, so a wall clock set
  back cannot hold it shut.
- **Dropped calls are logged once a minute per client and reason**, not only bad tokens, so a loop in
  a client's task cannot fill the journal (from the security review below).
- **The token file is read once per process** and cached; the cache changes only after a write
  succeeds.
- **The receiver finishes its broadcast after at most 8 s** and lets a slow call finish in the app's
  scope, inside Android's 10 s limit for the foreground broadcast queue.
- **The receiver follows its switch at process start**, off the main thread. A backup restore brings
  the switch back but not the component state or the tokens.
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

## Review

A fresh read-only security reviewer read commit 6ffe761. They found no way for an app without a
token to change state or learn anything. They raised three points, all fixed:
- `SENSORS_OFF` had a default of on;
- the limit ran on the wall clock;
- a flood of calls parsed the token file each time, and an exception outside the call's `try` could
  end the process.

They also caught that the receiver's state was not restored after a backup. One question stays open:
whether a slow Shizuku bind can reach the broadcast time limit. It is answered by the 8 s finish
above and checked on the emulator.
