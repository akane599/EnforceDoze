# EnforceDoze A17 fork

A Doze controller for Android 17. It proves from its own logs that Doze worked, ships tuned profiles,
and recommends one from how the phone is used. It is built for One UI 9 on the Galaxy S25 Ultra and
checked on the API 37 emulator. Android 14 to 16 (API 34–36) are best effort: anything that does not
work there shows up as a capability with a reason (ADR 0022).

This is a GPL-3.0 fork of [EnforceDoze](https://github.com/Akylas/EnforceDoze) by Martin Guillon
(Akylas, farfromrefug). EnforceDoze is itself a fork of [ForceDoze](https://github.com/theblixguy/ForceDoze)
by Suyash Srijan (theblixguy). Both are credited in [`NOTICE`](NOTICE) and on the app's About screen.
The fork keeps upstream's history up to the tag `upstream-baseline` (1.10.2), then rewrites the app in
Kotlin, module by module.

## What it does

- **Profiles.** Each profile sets some of the levers:
  - **A:** the Doze timings;
  - **B:** forced deep idle at screen off;
  - **C:** motion-sensor restriction;
  - **D:** a trimmed allowlist.

  | Profile | Levers | For |
  | --- | --- | --- |
  | Stock | none | The baseline every report compares with |
  | Everyday | A | Deep idle within about 10 minutes of a still, screen-off phone. The safe default |
  | Pocket | A + C | A phone carried while moving: motion no longer resets the walk to deep idle |
  | Night | A + C | The long unplugged stretch at night, with the longest gaps between maintenance windows |
  | Classic | B + C | Upstream EnforceDoze's behaviour: deep idle the moment the screen turns off |
  | Strict | A + B + C + D | Travel or a long day away from a charger. Never recommended automatically |

  Custom profiles stay inside guardrails, and import and export as JSON.
- **Plan and Recommended.** A day profile, plus an optional night profile in a window. Recommended
  reads the phone's own history, from day zero, and suggests a plan. It shows its confidence, its
  reasons with numbers, and the cost. It never switches plans without a tap unless automatic
  following is turned on.
- **Evidence.** Each screen-off session gets a verdict computed from the device's own records:
  Held, Partly or Did not doze. Each night gets a report with the timeline, maintenance windows,
  wakers and sensors. Missing data is drawn as a labelled gap, never as if it was seen. The quick
  proof checks in a few seconds that deep idle can be reached, then undoes itself.
- **Safety first.** Calls, audio, car mode, the hotspot, charging and chosen keep-awake apps lift
  forced idle and the sensor restriction until they end. Every change can be undone:
  - **Restore stock** puts back the phone's own values;
  - **Return to my previous setup** puts back what was there before, for example a Tasker setup;
  - drift by another app is detected, logged with the likely writer, and re-applied only within a
    loop guard.
- **Root-tier extras** (ADR 0038). With root, Wi-Fi off, mobile data off and app suspension can go on
  at screen off. Each has its own safety rules: data goes off only while a working Wi-Fi network
  carries the connection, and apps that ring for calls or keep alarms are never suspended.
- **Automation.** Tasker and similar apps can apply profiles and plans, pause, resume, restore stock
  and read the status. Each client sends its own token, and calls are rate-limited
  ([`docs/automation.md`](docs/automation.md)).
- **Coulomb.** The battery app Coulomb reads the evidence through a provider guarded by a signature
  permission. Nothing else can read it.

## Privilege

| Tier | Source | What it adds |
| --- | --- | --- |
| T0 | Nothing | Read-only: the screens, verdicts from what normal permissions show |
| T1 | Grants given once through Shizuku or ADB | `DUMP`, `WRITE_SECURE_SETTINGS`, usage access: lever A and the full evidence |
| T2 | The Shizuku or Sui worker as shell | Forced idle, sensor restriction, the allowlist, the quick proof |
| T3 | Shizuku as root, or Sui | The root-tier extras |

The onboarding walks through it and explains each step. On Android 17 the app works with any Shizuku
server that keeps Shizuku's protocol, and with Sui on rooted phones. ADB grants are the fallback when no
worker is wanted (ADR 0011).

## Install

Download `EnforceDoze.apk` from the fork's
[Releases](https://github.com/akane599/EnforceDoze/releases/latest). The asset name stays the same
from release to release, so Obtainium can track it. Release APKs are signed with the same key as
Coulomb (ADR 0006). The app id is `io.github.akane599.enforcedoze`, so the fork installs next to
upstream.

The app has no network permission. These are all the permissions it asks for, and CI checks the
list (`tools/ci/permissions.txt`):

| Permission | Why |
| --- | --- |
| `RECEIVE_BOOT_COMPLETED` | Start the engine at boot and after its own update |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | The engine watches the screen to apply the profile; it does no work in between |
| `POST_NOTIFICATIONS` | The engine's notification, the night report, and the notice when an extra cannot be put back |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | The onboarding asks for the exemption through the system dialog |
| `QUERY_ALL_PACKAGES` | The allowlist, keep-awake and suspension pickers list every app |
| `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE` | The hotspot hold-off, and the extras' Wi-Fi and data reads |
| `DUMP`, `WRITE_SECURE_SETTINGS`, `READ_LOGS`, `PACKAGE_USAGE_STATS` | The T1 grants; never granted at install |
| `moe.shizuku.manager.permission.API_V23` | Talking to Shizuku |

## Build

JDK 21, the Android SDK with platform 37, Gradle 9.9 (wrapper) and AGP 9.5.

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew assembleDebug testDebugUnitTest lintDebug detekt ktlintCheck :app:licensee
tools/ci/forbidden-calls.sh && tools/ci/module-graph.sh && tools/ci/permissions.sh
tools/emulator/suite.sh        # the emulator suite on an API 37 AVD, under 20 minutes
```

[`SPEC.md`](SPEC.md) is the specification, and the decisions are in [`docs/adr/`](docs/adr/). For
people coming from upstream, [`docs/parity.md`](docs/parity.md) says what happened to each feature.
[`docs/acceptance.md`](docs/acceptance.md) links the evidence for the release checklist.

## Licence

GPL-3.0-or-later ([`LICENSE.txt`](LICENSE.txt)). See [`NOTICE`](NOTICE) for the upstream copyrights
and credits.
