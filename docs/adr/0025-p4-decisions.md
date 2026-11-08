# 0025. P4 decisions

- Status: Accepted
- Date: 2026-11-08
- Phase: P4
- Spec: 6, 7

## Context

ADR 0023: choices that are cheap to reverse are made by the agent, each with a one-line reason here.

## Decision

- **Import collects every problem.** A wrong field reads as a placeholder and is listed; any
  problem refuses the file, so no placeholder is kept. The owner sees every broken field at once.
- **An import never replaces a profile.** An id already in use gets `-2`, `-3`, … and the name gets
  " (2)", " (3)". Overwriting on import would lose an edited profile without a word.
- **Custom profiles are one file each** in the app's files directory, written atomically (`tmp`
  plus `fsync` plus atomic move, as the engine's files). They are SPEC 6.4 files already, and a
  directory is easy to export.
- **A store file that no longer reads is set aside** as `.unreadable-<millis>`, not deleted, as the
  engine's own files are (ADR 0015).
- **One UI keys (`skip_sensing`, `skip_locating`, `dna_request_version`) are kept on import** with
  "effect unknown on this phone" (SPEC 6.3). They are never refused.
- **`DozeEngine` keeps one constructor argument per port** and suppresses `LongParameterList`. A
  holder object would only hide the ports.
- **The plan's window is stored as local `HH:mm` times.** The owner thinks in wall-clock hours,
  so a time-zone change keeps "23:00" at 23:00.
- **The receivers that take `ACTION_USER_PRESENT` are exported.** SystemUI sends it, not the
  system uid, and an unexported receiver drops it (`BroadcastSkipPolicy`, `android-17.0.0_r1`). Only
  protected broadcasts are in those filters, so no other app can reach them.
- **The engine finishes each command before it answers.** It saves and publishes the status first,
  so a caller that reads the status right after a command sees that command's result.
- **The P4 emulator gate turns on a Swipe lock screen** (`locksettings set-disabled false`) and
  puts the AVD's setting back afterwards. With None, SystemUI unlocks at every wake, so "the lock
  screen keeps the night" could not be shown.

## Consequences

More lines are added through P4.
