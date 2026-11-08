# 0017. Wait for unlock as a lever B option

- Status: Accepted
- Date: 2026-11-08
- Phase: P2
- Spec: 5.3, 6.4; settles question Q4's open item and row 4 of the parity table

## Context

Upstream EnforceDoze has a "wait for unlock" preference (`waitForUnlock`, off by default). With it on,
upstream ignores a screen on while the keyguard is showing and leaves Doze only at
`ACTION_USER_PRESENT` (`ForceDozeService.java` lines 1532–1541; `Utils.isDeviceLocked` uses
`KeyguardManager.isKeyguardLocked`). So a glance at the lock screen, or a notification that lights the
screen, does not end forced idle.

SPEC 5.3 releases forced idle at every screen on. The parity rule (SPEC 2) asks to port upstream
features or say why not. The audit kept this one as an option and left the decision to P2.

## Decision

1. Lever B gets an option `untilUnlock`, false by default. In the profile file:
   `"forceIdle": { "enabled": true, "delaySec": 0, "untilUnlock": false }`. No built-in profile turns
   it on.
2. With it on, a screen on with the keyguard showing keeps every "on" state the engine set for lever B,
   lever C and sensor privacy. Unlock (`ACTION_USER_PRESENT`) releases them, as a screen on does today.
   The journal records the cause `UNLOCK`.
3. It only keeps. At the lock screen it never sets a lever that was not already on. A hold-off still
   releases everything at once, and nothing comes back on until the next screen off. Pause and
   Hands-off also release, as before.
4. The host reads `KeyguardManager.isKeyguardLocked` at screen on and passes it to the engine. It reads
   it once more after the screen-on command. An unlock whose broadcast was queued before that command
   is replayed, so the option can never outlast the lock screen.

### Safety

- **Calls:** an incoming or outgoing call is a hold-off (SPEC 5.5), so it releases forced idle and
  sensors before the call screen is needed. The proximity sensor comes back with lever C's release.
- **Alarms:** user alarms use `setAlarmClock` and fire in deep idle; the option does not touch them. An
  alarm that lights the screen at the lock screen leaves forced idle on, as upstream does.
- **Emergency:** an emergency call from the lock screen is a call, so the call hold-off releases
  everything.
- **Undo:** unlock, a hold-off, Pause and Restore stock each release. Nothing new is written while the
  screen is on.

## Consequences

- SPEC 5.3 and 6.4 gain the option, marked "(ADR 0017)".
- The cost is stated where the option is shown (P5): notifications that are not high priority wait
  for unlock or the next maintenance window while the lock screen is lit.
- The engine has one more input (`locked`) and one more command (`unlocked`). Both are covered by
  target and engine tests.
- Parity table row 4 points at this option. Its status stays `upstream` until the upstream service is
  deleted (SPEC 12.1); the profile editor shows the option in P5.
