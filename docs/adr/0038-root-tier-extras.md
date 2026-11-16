# 0038. Root-tier extras: Wi-Fi, mobile data and app suspension as engine levers; biometrics dropped

- Status: Accepted
- Date: 2026-11-16
- Phase: P6 (open spec work before P7's gate, ADR 0037)
- Spec: 1.2, 4.1, 5, 2.3 (parity rows 11–14)

## Context

SPEC 1.2 keeps four upstream features behind the root tier (T3), each switched on as the phone goes
into Doze and off as it comes out:
- Wi-Fi off;
- mobile data off;
- biometrics off;
- suspending chosen apps.

Upstream ran each of them from its service with `svc wifi`, `svc data`, `settings put secure
biometric_keyguard_enabled` and `pm suspend` or `pm unsuspend`.

Each one can break something SPEC 0.3 puts first:
- Wi-Fi off cuts Wi-Fi calling, which may be the only way a call gets through where cellular
  coverage is poor.
- Mobile data off delays messages and app calls.
- A suspended clock or messaging app can miss the owner's alarm or a call.
- A release that fails at screen on must never leave a radio off or an app suspended. All three
  outlast a reboot.

Checked against AOSP `android-17.0.0_r1`:
- **Wi-Fi:** `cmd wifi set-wifi-enabled enabled|disabled` (`WifiShellCommand`). It calls
  `WifiService.setWifiEnabled` and is among the commands shell may run.
- **Mobile data:** `cmd phone data enable|disable` (`TelephonyShellCommand.handleDataTestModeCommand`).
  It calls `enableDataConnectivity` or `disableDataConnectivity`.
  - User data off is a soft reason in `DataNetworkController` (`DATA_DISABLED`).
  - Restricted requests stay allowed (`DataAllowedReason.RESTRICTED_REQUEST`), and IMS requests
    are restricted, so VoLTE keeps its network.
  - Unmetered requests stay allowed too (`UNMETERED_USAGE`).
  - One UI and the owner's carrier are not checked yet (Q15).
- **App suspension:** `pm suspend|unsuspend [--user N] PACKAGE...` (`PackageManagerShellCommand.runSuspend`).
  - It prints `Package X new suspended state: true|false`.
  - It records the suspender as `root` or `com.android.shell`, not EnforceDoze, so uninstalling
    EnforceDoze does not lift it. Restore stock and the uninstall request must.
- **Biometrics:** `BiometricService` observes `biometric_keyguard_enabled` only when the flag
  `biometricsOnboardingEducation` is off. With it on, only `fingerprint_keyguard_enabled` and
  `face_keyguard_enabled` count. One UI may use its own settings.

## Decision

This decision touches the engine's command model, so a fresh read-only reviewer gave a second opinion
first (ADR 0023). Both views are below.

### Placement

The extras are three new engine levers: `WIFI`, `MOBILE_DATA` and `APP_SUSPENSION`. They are written
only at T3, through three new worker methods that take fixed argument lists.
- **The owner's choices live in `PersistedState`**, beside `reapplyOnDrift` and `allowlistKeep`.
  They are off by default and set from Settings, shown at T3 only, with a warning. They are not
  part of profiles.
- **They get their own rules in `Targets`**, not the rules of levers B and C:
  - **On:** only in managed mode with a profile active, the screen off and no hold-off of any kind
    (call, audio, car mode, hotspot, keep-awake app, charging).
  - **Off:** at every screen on, "until unlock" included. Also off on pause, hands off, Restore
    stock, Return to previous and the uninstall request.
  - **Read before engaging:** a lever goes on only when the read just before shows the feature
    on. Nothing is booked for a feature that is already off, so screen on never turns on a radio
    the owner had off.
  - **Drift is logged and never re-applied.** If the owner or the system turns the feature back
    on during the night, the engine lets go of the lever until the next screen off.
- **Wi-Fi goes off only when it is on but not connected**, and not in airplane mode. Without a Wi-Fi
  connection there is no Wi-Fi calling, so no privileged read is needed to protect it. What is
  saved is the scanning.
- **Mobile data goes off only while a validated Wi-Fi network is the connection.** It comes back the
  moment that ends, through a `NetworkCallback`: an event, not polling (SPEC 1.3). With the Wi-Fi
  rule, both radios are never off at once.
- **Apps are suspended only if they are neither on the never-suspend list nor already suspended.**
  The list is read through public calls, both when the owner picks apps and again at every screen
  off. The screen shows why an app cannot be picked. The list:
  - system apps, EnforceDoze, and the Shizuku manager;
  - the default dialer and SMS app (`TelecomManager`, `Telephony.Sms`);
  - home apps;
  - enabled input methods and accessibility services;
  - apps that handle `AlarmClock.ACTION_SET_ALARM`, and the app behind the next alarm clock
    (`AlarmManager.getNextAlarmClock`);
  - apps with a `ConnectionService` (VoIP calls);
  - the keep-awake apps.

  Only what the engine suspended is unsuspended.
- **A booked extra the tier cannot release posts a notification.** This happens after a reboot
  without the worker, or after losing root. The notification names the system switch to use.
- **The commands stay at T3, as SPEC 1.2 and 4.1 say.** AOSP shows shell may run them too. Opening
  them to T2 is a scope change for the owner (Q15).

### Biometrics: dropped (SPEC 2.3)

On API 37, upstream's key does nothing while the flag is on, and nothing measures a power saving
from it, so the app could not prove it works (SPEC 0.3, honesty). The keys that do count are the
owner's security choices, writable with `WRITE_SECURE_SETTINGS` alone, so "root tier" would mean
nothing. A slow restore would make the first fingerprint unlock of the morning fail. SPEC 1.2 changes
accordingly. The owner can ask for it back (Q15).

### Second opinion (fresh read-only reviewer)

Recommended P1 narrowed, the decision above:
- engine levers, with the owner's choices in `PersistedState`;
- a target function of their own;
- engage only on a read showing the feature on;
- never re-apply on drift;
- release at every screen on.

Reasons:
- the executor's booking already makes doubt end in a release;
- one owner of the screen and hold-off events;
- one action log and one Restore stock.

Against the alternatives:
- **Profile lever E:** a data-format change. Shared profiles would carry device- and
  carrier-specific hazards, and the recommender would credit radios to a profile.
- **A separate component:** a second copy of the safety machinery, racing the engine on the same
  events, with two restore paths.

Other recommendations:
- Wi-Fi off only when not connected.
- Data off only on validated Wi-Fi, back on at once when Wi-Fi drops.
- The never-suspend list, including role holders, input methods, accessibility, the alarm app,
  Shizuku, EnforceDoze and wearables.
- Drop biometrics.
- Restricted standby as a softer alternative to suspension.

### This agent's view

I agree with P1 narrowed. Reading the engine, the alternatives do not survive contact with
`Executor`: it books engages before writing and releases only after a read-back. P3 would have to copy
that.

I differ on two points:
- **No restricted standby instead of `pm suspend`.** It is parity's feature. It is not what the
  owner of upstream chose, and AOSP keeps alarm clocks working for restricted apps anyway. The
  uninstall request and Restore stock cover the "uninstall does not unsuspend" gap.
- **No exact wearable package names in the never-suspend list.** They are not public and differ by
  maker. The `ConnectionService` and alarm rules cover the calls and alarms a watch relays.
  Choosing the app is the owner's act, behind the warning.

Wireless Android Auto is covered by the car-mode hold-off, which releases every extra.

## Consequences

- **Data:** `Lever` gains three values, read as before by `EngineFiles`; old `state.json` files load
  unchanged.
- **Worker:** `WorkerContract.VERSION` rises, adding `setWifiEnabled`, `setMobileData` and
  `setPackagesSuspended`. Each refuses below uid 0.
- **Manifest:** uses `ACCESS_WIFI_STATE` and `ACCESS_NETWORK_STATE`, which upstream already declared.
  Both are normal permissions and give no network access. CI's INTERNET check stays.
- **The night report** lists the extras' actions like any lever's. The recommender does not yet tell
  nights with extras apart: every profile gets the same help from them, but the predicted numbers
  can read better than the profile alone. Q15 asks whether that matters to the owner.
- **Tests** (from the second opinion's minimum set):
  - targets for every engage and release condition;
  - conditional engage;
  - both radios never off together;
  - crash safety (booked before the write, released at the next start);
  - a failed release retried;
  - drift never re-applied;
  - the never-suspend list at pick time and screen off;
  - data back on when Wi-Fi drops;
  - the worker's argument checks;
  - an emulator run at T3 (Shizuku as root on `ed-api37`), including a reboot with an extra on.
- **One phone check before 1.0.0:** a VoLTE call with mobile data off on the owner's carrier, and One
  UI's behaviour for each command (Q15). Until then the Settings warning says so.

## Addendum: the engine review (2026-11-16)

A fresh read-only reviewer read the engine change (dcbd23a) before the gate, as AGENTS.md asks for the
engine. Its findings and what changed:
- **A suspension drift forgot apps the engine still held.** The lever now reads as the held apps that
  are suspended. A chosen app someone else suspended is theirs and is never drift. On drift the held
  apps that still read as suspended stay booked, so they are lifted.
- **A suspension call that timed out could land after its read-back.** Suspension reads now wait behind
  a suspension call still running. A plan with a timed-out call keeps everything it asked for booked.
- **Both radios could end up off.** Wi-Fi no longer goes off while mobile data is off, whoever turned it
  off. Once Wi-Fi was connected with the screen off, it stays on until the next screen event, so it can
  reconnect after a router restart.
- **The Wi-Fi release was judged before Wi-Fi had started.** The emulator run showed it: a release
  read back off, logged as a mismatch. Radio writes are now re-read for up to 6 s until they show.
- **The let-go set lived only in memory.** It is kept in `state.json`, with the Wi-Fi rule above, as
  `extrasNight`, cleared at every screen event. Old files load unchanged.
- **Wi-Fi came back inside airplane mode.** A held Wi-Fi now waits for airplane mode to end. The network
  watch also listens for `ACTION_AIRPLANE_MODE_CHANGED`. Restore stock still puts it back at once,
  because the owner asked for it.

Each finding has a test in `ExtrasEngineTest`.

The first emulator run found one more: mobile data never went off. `ConnectivityManager.activeNetwork`
is null for this app, because without `INTERNET` its uid counts as blocked, even while the system's
default is a validated Wi-Fi network (`docs/evidence/p6-extras/radios-screen-off.txt`). The reader
now looks through the list of networks for a validated, unrestricted Wi-Fi network with internet that
is not behind a VPN.
