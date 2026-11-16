# Coming from EnforceDoze 1.10.2

What happened to each feature of upstream EnforceDoze 1.10.2 in this fork. The full audit is
`docs/upstream-audit.md` §6, with the tests behind each row. Upstream's code stays at the
`upstream-baseline` tag.

**Kept, in a new form**

| Upstream | In this fork |
| --- | --- |
| Force deep Doze at screen off | Lever B, used by Classic and Strict. Classic is upstream's behaviour |
| Delay before Doze | Lever A: Doze's own timings reach deep idle in minutes, with no app timer |
| Wait for unlock before leaving Doze | `untilUnlock` on lever B, off by default: a locked screen on keeps forced idle, unlock releases it |
| Disable when charging | The charging hold-off, always on |
| Execution mode (root or Shizuku) | Tiers T0–T3: read-only, durable grants, the Shizuku or Sui worker, root |
| Doze allowlist screen | The Allowlist screen; Strict trims the list (lever D) and puts it back |
| Motion sensing off, with an allowed app | Lever C's restricted mode, in Pocket, Night, Classic and Strict |
| All sensors off in Doze | Strict's "all sensors off" and the "Sensors off" tile, both behind a confirmation |
| Wi-Fi off in Doze | A root-tier extra: off only when on and not connected; back on at screen on and every hold-off |
| Mobile data off in Doze | A root-tier extra: off only while a working Wi-Fi network carries the connection; back on when it goes |
| App suspension during Doze | A root-tier extra: apps that ring for calls, keep alarms or type are never suspended |
| Hotspot and music exceptions | The hotspot and audio hold-offs, with calls, car mode and charging |
| Keep the current app awake | Keep-awake apps: the app on screen at screen off holds Doze off |
| Custom Doze periods | Plans: a day profile and a night profile in a window, switched at the window and the unlock |
| Persistent notification | The engine's notification, on a quiet channel that can be turned off |
| Doze and battery stats screens | Sessions with verdicts, the night report and its timeline |
| Log screen | The Log screen: every action with its read-back, and every event |
| Reset EnforceDoze | Restore stock, Return to my previous setup, and Reset in Settings |
| Tasker broadcasts and the re-enter broadcast | The automation API with a token per client and a rate limit (`docs/automation.md`) |
| Doze tile | The "Doze plan" and "Sensors off" tiles |
| Doze tunables screen | Custom profiles inside guardrails, with import and export |
| Battery-optimisation request | The onboarding's keep-alive step, with One UI's "Never sleeping apps" |
| Start at boot and after update | The engine starts at boot and after its own update |
| Re-enable Doze when disabled | The capability probes and the onboarding say what is missing and why |
| About | Settings: the credits, the GPL notice and the licences |

**Dropped**

| Upstream | Why |
| --- | --- |
| Biometrics off in Doze | On Android 17 the setting it wrote does nothing, so the app could not prove it works (ADR 0038) |
| Airplane mode in Doze | It blocks calls and emergency alerts |
| Bluetooth off in Doze | It cuts the watch link and Android Auto |
| GPS off in Doze | Deep idle already stops app location; the location setting also serves emergency location |
| Battery saver on in Doze | One UI's power saving runs its own Doze; the report labels it instead |
| Notification blocklist | It needed binder transaction numbers; Doze already defers normal-priority notifications |
| Ignore lock-screen timeout | No app timer is left to tie to the lock screen |
| Auto-rotate and brightness fix | A workaround for upstream's removed sensor hack |
| Battery consumption list | Coulomb covers per-app attribution |
| Allowlist broadcasts | Any app could send them, and a change outlived Restore stock (ADR 0036) |
| Airplane-mode tile | The system tile covers it |
| App shortcuts | Home and the tiles cover the entry points |
| Translations | All copy is rewritten, in English and Turkish; the translators are credited in `NOTICE` |
| Website, store metadata, release workflow | The fork ships through GitHub Releases |
