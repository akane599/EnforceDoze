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
- **The switch-over is a screen of the profiles feature** (`SwitchOverScreen`), not a new module: its
  import makes a profile and its table is the profile screens' timings table. Its logic is pure JVM in
  `:core:doze` (`SwitchOver`), next to the snapshots it reads.
- **"What was set" is `previous.json` against `stock.json`.** The engine captures `previous.json`
  before its first write, so it holds the owner's Tasker values whatever the screen state was then.
  Values are shown as `dumpsys deviceidle` printed them, read like the profile screens' timings.
- **The import keeps the string's keys and any other value lever A can write**, converted from
  `dumpsys` syntax to milliseconds; a value that does not pass `DeviceIdleSettings.problems` (such as
  `location_accuracy=50.0m`) is left out. It goes through the same guardrails as any custom profile, and
  their problems are shown if it fails. Its id starts at `imported-setup`; the app remembers the last one.
- **The check needs a watching engine.** A foreign write is logged only while EnforceDoze holds the
  string (a profile with timings, managed), so without one the check says so instead of passing on
  nothing. A drift with an unknown writer counts as foreign: the check fails rather than passes falsely.
- **Other managers are found by app id**: upstream EnforceDoze (`com.akylas.enforcedoze`), ForceDoze
  (`com.suyashsrijan.forcedoze`) and Battery Guru (`com.paget96.batteryguru`). `QUERY_ALL_PACKAGES` is
  already held, for the app pickers. The "Two Doze managers" notice and the "another app wrote" notice
  both open the switch-over (SPEC 5.7's guided switch-over), not the Log.

- **The provider's emulator test runs from a stand-in for Coulomb**, `:testing:evidence-client`: its own
  package and uid, signed with the debug key, self-instrumenting. An `androidTest` of `:app` would run
  inside the app's own process and uid, where neither the permission nor the caller check applies. Debug
  builds accept that package as a caller; release builds accept Coulomb alone.
- **Upstream's empty `ApplicationTest` is gone.** It was the Android Studio template (no assertions) and
  used `android.test`, which no longer compiles; nothing else lived in `app/src/androidTest`.
- **P6 debug commands** (`automation`, `automation-token`, `import-setup`, `switch-check`,
  `switch-state`) drive `tools/emulator/p6-integrations.sh`. They exist only in debug builds, like the
  other gate commands.
- **Keeping the app running (SPEC 3.3, parity row 35) is a block in the capabilities step**, not a
  seventh step, because SPEC 9.1 counts six.
  - "Allow" opens the system's own dialog (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), and
    "App settings" opens the app's system page.
  - The state is read back on return with public calls, shown as raw values: the exemption, the
    standby bucket, background restriction.
  - Background restriction counts as missing even with the exemption.
  - One UI's "Never sleeping apps" has no public reading, so on Samsung the text names its path
    (Q14).
  - The worker could add the app to the allowlist silently. It does not: the owner should see and
    grant this exemption.

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
