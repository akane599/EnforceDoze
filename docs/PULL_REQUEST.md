# Proposed title

Make Android 16 Doze control recoverable, observable and easier to use

# Proposed description

The original service could report Doze before observing it and lose temporary-setting originals when access or the process stopped. This change gives Shizuku/root/legacy operations a serialized execution path and durable undo journal. Entries remain pending until restoration is read back; access failures are visible, and screen, charging, call, schedule and stop events reconcile device state.

- Replace cached Shizuku/reflected-process handling with a bounded user service, authorization/death/reconnection handling and protection against late timed-out writes.
- Verify Deep Doze using saved screen-off/state observations. Separate sensor app-access restriction from developer sensor privacy; use named privileged interfaces, retain originals, and disclose OEM/biometric limits.
- Repair connectivity/hotspot/media guards, app restrictions, schedules, tunables, token-authenticated automation and interrupted/charging history. Use event callbacks and a quiet foreground notification instead of per-query executors/polling.
- Redesign dashboard, access setup, settings, app lists, history and diagnostics with shared Material surfaces, themes, scalable text and edge-to-edge insets. Keep diagnostic data local; exclude device-specific restoration/token data from backup/transfer.
- Repair build/test dependencies and add JVM plus Android UI/real shell-Shizuku regression suites. Add a manual-only, read-only-permission APK/test-artifact workflow, explicit signing, and guarded legacy Fastlane paths. Retire the workflow that exported all secrets and mixed builds with publication.

## Validation

See [DELIVERY.md](DELIVERY.md) for final exact results, APK hashes/certificates and logs. All 24 local JVM tests pass (zero failures/errors/skips), lint has zero errors/342 warnings, debug/minified release builds and all 13 instrumentation tests compile; eight isolated release-script checks pass. Lint warnings remain and are documented. Website light/dark/large-text/desktop checks and screenshot inspection pass.

**Android device/UI tests remain unexecuted.** The host lacks KVM/CPU virtualization; API36 software-emulator attempts failed before APK installation (ATD system_server crash and older-emulator SIGSEGV). No Android screenshots, real Doze/sensor results, or Shizuku runtime passes are claimed. The instrumentation suites are prepared for an accelerated disposable API36 emulator and deliberately fail missing prerequisites. No physical Samsung, modem, biometric, sensor-HAL, root/Magisk or old-Android behavior is validated.

## Compatibility and rollout

Development version 1.11.0-dev/87, application ID unchanged, min23/target36. The supplied 1.10.2/86 publisher signing key is unavailable; local test-signed builds cannot update it. Restore changes before uninstalling or clearing data. Old versions did not preserve every original value, so upgrades pause monitoring for review; missing originals cannot be reconstructed. New explanatory UI is currently English. Notification filtering cannot recreate dismissed notifications, and optional radio/app restrictions can intentionally interrupt delivery.

The new workflow triggers only through workflow_dispatch, never from a push/PR. It must reach the default branch before the phone Run workflow interface is available. APKs use an explicitly configured signing key or a per-run temporary test key. The user authorized pushing the development branch to akane599/EnforceDoze after local validation. No PR, merge, release or Actions run has been performed. A draft PR is appropriate while runtime verification is outstanding.
