# EnforceDoze

EnforceDoze requests Android Deep Doze during configured screen-off intervals. It is a GPL v3 fork of [ForceDoze](https://github.com/theblixguy/ForceDoze). This development branch targets Android 16/API 36, with Shizuku as the primary access mode and Android 6/API 23 as the minimum installation version.

A successful command or a long monitoring interval is not proof of Deep Doze. **Diagnostics** saves individual observations of Android's deep-idle state, screen state, applied controls and restoration. It keeps up to 200 local observations; history keeps up to 100 intervals. Battery changes are coarse percentage points, not measured savings attributable to this app.

## Getting started

1. Install an APK with a compatible signing certificate; see [build and signing instructions](docs/BUILDING.md).
2. Open **Set up access**, start [Shizuku](https://shizuku.rikka.app/guide/setup/), authorize EnforceDoze and verify access. Root remains an explicit alternative. ADB-granted app access is a limited legacy mode and cannot force Deep Doze on modern Android.
3. Allow notifications and Phone permission for visible recovery and call protection. Permit unrestricted battery use. Exact-alarm access improves schedule and delay timing; without it Android can delay alarms.
4. Review optional controls in **Settings**, then start monitoring. Screen-on releases forced Doze and sensor controls. The unlock option can keep other temporary changes until unlock. Charging, detected calls, schedule boundaries and Stop restore changes.
5. After a screen cycle, open **Diagnostics** to inspect actual observations and **Monitoring history** for intervals. Observations do not establish continuous idle residence.

Temporary changes are written to a recovery journal before commands run. Records are removed only after the original state is read back. If access disappears, pending changes stay visible and recovery retries when it returns. Android force-stop prevents recovery until the app is reopened. Do not uninstall or clear app data with restoration pending. Older versions did not save every original setting; those missing originals cannot be reconstructed. Upgrading pauses older monitoring options for review.

## Features and limits

| Control | Behavior and limits |
|---|---|
| Deep Doze | Shizuku/root force-idle request, followed by a separate state observation. Android controls maintenance and can resume natural idle after forced mode is released. |
| Delay and daily schedule | Local-time periods, including overnight. Manual Stop takes priority. Exact alarms are optional; inexact timing can be delayed. |
| App sensor access | SensorService restricted mode, with an optional package substring exemption. Restores NORMAL only when EnforceDoze owned the change. This does **not** prove physical sensor power-off. |
| Developer sensor privacy | Separate Sensors off control, read and changed through named platform interfaces. Requires compatible privileged access; camera/microphone and OEM behavior vary. |
| Wi-Fi, mobile data, airplane mode, Bluetooth, location, battery saver | Independent optional changes with saved originals and readback. Some controls/interfaces are unavailable on older Android or particular OEMs. Mobile data captures the default data subscription. Disabling connectivity intentionally interrupts delivery. |
| Hotspot protection | Preserves affected connectivity when hotspot is active **or unreadable**. |
| Media protection | Requires notification access. Preserves connectivity and app access when playback is active or cannot be identified reliably. |
| Doze exemptions | Persistent system exemptions, edited through the selected access mode with verification. Cannot compensate for a disabled radio. |
| App suspension | Temporarily suspends selected non-system apps. Protects EnforceDoze, Shizuku and selected media/foreground apps. Unknown foreground state conservatively skips suspension. |
| Notification filtering | With notification access, dismisses newly posted selected notifications during monitoring. Protects ongoing, call, alarm and media notifications. Dismissed notifications cannot be recreated. Replaces obsolete notification-manager transaction-number calls. |
| Biometric keyguard | Experimental: changes only an existing platform setting. Does not establish that fingerprint hardware is disabled; Samsung may ignore it. Restored on screen-on. |
| Doze tunables | Changes one device-exposed constant, validates syntax and retains originals separately. Stored-value verification does not prove the OEM uses that value. Restore before editing the same value again. No namespace-wide reset. |
| Tasker | Explicit opt-in and a locally generated token required for exported broadcasts. See the in-app Tasker screen for actions, extras and supported options. Existing unauthenticated tasks need updating. |
| Quick Settings | Monitoring and airplane-in-Doze option tiles reflect stored intent. The airplane tile selects a future screen-off option, not an immediate radio toggle. |

The old automatic rotation/brightness toggle is retired: directly restoring SensorService avoids overwriting unrelated display preferences. Legacy history remains readable as unverified raw records. The new interface supports light/dark/system themes and scalable text; new explanatory copy currently uses English, while older translation resources are retained for migration.

## Samsung One UI

Review EnforceDoze and Shizuku in **Battery → Background usage limits** and remove sleeping/deep-sleeping restrictions; use Never sleeping apps where available. Menu names vary. Samsung Auto Blocker can restrict debugging or installation. Shizuku's process does not survive reboot; start it again or configure its supported automatic startup.

**No physical Samsung device is available for this work.** Emulator results do not establish Samsung sensor-HAL, modem, incoming-call delivery, biometric or background-management behavior. In-app guidance and diagnostics explain these limits without claiming hardware validation. See [Samsung's application-management documentation](https://developer.samsung.com/mobile/app-management.html) and [Android Sensors off documentation](https://source.android.com/docs/core/interaction/sensors/sensors-off).

## Development and verification

Use JDK 21, SDK platform/build tools 36 and the checked-in Gradle wrapper:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest --max-workers=2
```

Release is unsigned unless explicit signing configuration is supplied. Device tests are separate from JVM tests and require a disposable emulator; the Shizuku suite fails when prerequisites are missing. See [test instructions](docs/TESTING.md), [audit results](AUDIT_REPORT.md) and the current [handoff record](PROGRESS.md) for actual results and remaining verification. A build completing does not imply device tests ran.

The manual GitHub build workflow builds APKs and test reports without publishing releases or pushing changes. See the build instructions for default-branch availability and phone download steps.

## Privacy, source and license

No ads, analytics or automatic diagnostic uploads. Options, observations, history and recovery records stay local; copying/sharing is deliberate. Source and support links open the browser. Backup of application data is disabled to avoid replaying another device's recovery commands.

[Source and issues](https://github.com/Akylas/EnforceDoze) · [Sponsor](https://github.com/sponsors/farfromrefug) · [Translations](https://hosted.weblate.org/engage/enforcedoze/) · [GPL v3](LICENSE.txt)
