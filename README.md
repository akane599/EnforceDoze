# EnforceDoze

EnforceDoze is an open-source Android app for screen-off Doze automation. This repository builds on [farfromrefug/EnforceDoze](https://github.com/farfromrefug/EnforceDoze) and the original [ForceDoze](https://github.com/theblixguy/ForceDoze) by @theblixguy.

It can request Doze after screen-off, respect schedules and charging, manage app exemptions, and apply optional device controls during a session. Battery results depend on the phone, firmware and workload; forcing idle can delay background work and notifications.

## Android 16 development revision

The current development branch adds a Material 3 dashboard, searchable app lists, explicit access and recovery states, an updated Shizuku UserService backend, durable restoration of changed settings, and automated build checks. See the [review and validation record](docs/ANDROID16_REVIEW.md) for findings, compatibility limits and the phone acceptance checklist.

This revision targets Android 16 (API 36) and retains Android 6.0 (API 23) as its minimum. Samsung/One UI privileged behavior still requires physical-device validation. This branch is not a tested stable release.

## Setup

1. Install and start [Shizuku](https://shizuku.rikka.app/guide/setup/).
2. Open EnforceDoze, choose **Shizuku**, and tap **Connect access** to authorize it.
3. Allow background battery use for both apps. On Samsung, check Sleeping/Deep sleeping apps and each app's battery settings.
4. Enable EnforceDoze from the dashboard. Optional radio, biometric, sensor and app-blocking controls are in Settings.
5. Restart an ADB-started Shizuku after each phone reboot. If a change remains pending, reconnect the original access mode and use **Retry / restore changes**.

| Mode | Requirements | Scope |
| --- | --- | --- |
| Shizuku | Running Shizuku with EnforceDoze authorized | Recommended without root. Available controls depend on the privileges granted by Android/One UI. |
| Root | Working `su` and explicit authorization | Uses the same ordered commands and restoration journal. |
| Legacy ADB grants | `DUMP` and `WRITE_SECURE_SETTINGS`, Android 6–13 | Limited legacy compatibility. Choose Shizuku or root on Android 14+. |

Tasker users must enable **Settings → Allow Tasker broadcasts**. Existing action names are listed in the app. Enabling this option allows other installed apps to use those broadcasts as well. Notification buttons remain functional with external automation disabled.

If **Keep hotspot connected** is enabled, unknown hotspot state leaves connectivity unchanged. When music protection is enabled, unavailable notification-listener access also preserves connectivity. Optional features can be denied by firmware; check Diagnostics for command results. Wi-Fi or airplane changes can interrupt wireless-debugging access on some devices.

## Build and verify

### Build an APK on GitHub, including from a phone

The [Build APK workflow](https://github.com/akane599/EnforceDoze/actions/workflows/build-apk.yml) builds an installable **debug APK** after app, Gradle or APK-workflow changes are pushed to any branch. It runs JVM tests and lint, verifies the APK signature, and provides a download named with the app version and commit. The APK is a direct download; no ZIP extraction is needed. Checksums and build information are separate artifacts, and APK downloads are retained for 30 days.

1. Sign in to GitHub in your phone's browser and open **Actions → Build APK**.
2. Open a successful run and tap its APK under **Artifacts**, or use the download link in its summary.
3. To build again, open a previous run and choose **Re-run all jobs**. After this workflow is merged into `master`, you can also use **Run workflow**, select a branch, and start a new build. GitHub requires the workflow on the default branch for that manual button ([GitHub instructions](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)).

The separate **Android checks** workflow runs Android 16 UI and Shizuku integration tests. The APK workflow does not publish a release or change the app's version.

#### Reusable development signing (optional)

No secrets are needed for a first build. By default, each fresh runner generates a temporary debug key, so its APK cannot update an installation signed with another key, including the APK shared in chat. For repeatable update signing, add the Actions repository secret `ENFORCEDOZE_DEBUG_KEYSTORE_BASE64` containing the base64 encoding of a development `debug.keystore` with alias `androiddebugkey` and store/key password `android`. The workflow restores that key for signing and removes it from the runner afterward; it uploads only the APK, public certificate information and reports.

Keep that development key private and use the same one for future builds. Do not commit it or use an upstream/production signing key for this debug workflow. Without matching signing keys, Android requires uninstalling the old build, which clears app data; disable EnforceDoze and complete pending restoration before doing that. See [Android's signing documentation](https://developer.android.com/studio/publish/app-signing).

### Build locally

Install JDK 17, the Android SDK Platform 36 and Build Tools 36.0.0, then set `ANDROID_HOME` or an appropriate local `sdk.dir`.

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
```

APK outputs are under `app/build/outputs/apk/`. Debug APKs use a development key. Release APKs are unsigned unless signing is explicitly configured; an APK signed with a different key cannot update an existing upstream/F-Droid installation.

Run device tests with an Android emulator or test phone:

```sh
./gradlew connectedDebugAndroidTest
```

The CI workflow defines JVM tests, lint, debug/release builds and an Android 16 emulator job. New UI strings currently fall back to English until translated. No battery-saving percentage is claimed without device measurements.

## Permissions and privacy

- Package visibility supports app selection and exemptions.
- Phone-state access supports cellular call detection and optional mobile-data checks.
- Notifications show monitor state and restoration guidance.
- Battery-optimization exemption and a foreground service support screen-off monitoring.
- Optional notification-listener access identifies active media playback.
- Privileged device operations use the selected Shizuku/root/legacy backend.

The app has no account or ads. Diagnostics stay on the phone unless the user copies or shares them.

## Project links

- [Issues for this repository](https://github.com/akane599/EnforceDoze/issues)
- [Upstream releases](https://github.com/farfromrefug/EnforceDoze/releases)
- [Upstream translations](https://hosted.weblate.org/engage/enforcedoze/)
- [Support the upstream maintainer](https://github.com/sponsors/farfromrefug)

Licensed under GPL v3; see [LICENSE.txt](LICENSE.txt).
