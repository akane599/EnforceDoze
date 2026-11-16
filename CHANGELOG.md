# Changelog

All notable changes to the EnforceDoze A17 fork. Versions follow semantic versioning. Upstream
EnforceDoze's own history, up to 1.10.2, is in the git history before `upstream-baseline`.

## 1.0.0

The first release of the fork: upstream EnforceDoze 1.10.2 rewritten in Kotlin for Android 17
(One UI 9; API 34–36 best effort).

### Added

- **Profiles.** Six built-ins: Stock, Everyday, Pocket, Night, Classic and Strict. Each Doze key is
  checked against AOSP `android-17.0.0_r1` (ADR 0013). Custom profiles stay inside guardrails and
  import and export as JSON.
- **Levers A–D.** The Doze timings, forced idle, sensor restriction and a trimmed allowlist. Each is
  read back after it is written; a mismatch shows as "partly applied" with the key.
- **Hold-offs.** Calls, audio, car mode, the hotspot, charging and keep-awake apps lift forced idle
  and the sensor restriction until they end.
- **Undo.** Restore stock reproduces `stock.json`; Return to my previous setup reproduces
  `previous.json`.
- **Drift.** A change by another app is detected and logged with the likely writer. It is re-applied
  within a loop guard of two corrections an hour.
- **Plans and Recommended.** A day profile, an optional night window, and a recommendation from the
  phone's own history from day zero. It comes with confidence, reasons with numbers and cost, and
  never switches without a tap unless automatic following is on.
- **Evidence.** Sessions, verdicts computed from the device's records, night reports with the
  timeline, maintenance windows, wakers and sensors. Missing data is shown as labelled gaps. The
  quick proof always undoes itself. Exports are a ZIP with the raw dumps.
- **Privilege tiers T0–T3.** A typed Shizuku or Sui worker with no `service call` transaction numbers
  and no reflection. Durable grants are given through the worker, and read-only mode works with no
  privilege.
- **Root-tier extras** (ADR 0038). Wi-Fi off, mobile data off and app suspension during Doze, each with
  its own safety rules.
- **Integrations.**
  - An automation API for Tasker and similar apps: a token per client and a rate limit
    (`docs/automation.md`).
  - An evidence provider for Coulomb, guarded by a signature permission.
  - The Tasker switch-over: detect, import, verify, and a way back.
- **Tiles.** "Doze plan" and "Sensors off".
- **Onboarding.** Capability probes with visible reasons, and a keep-alive step for One UI's "Never
  sleeping apps".
- **Design.** A new design system (`DESIGN.md`) in dark and light, English and Turkish, readable at 200%
  font scale.

### Removed

- Upstream's Java app: every screen, service, receiver and library. Each feature is either kept in a
  new form or dropped for a stated reason (`docs/parity.md`).
- Biometrics, airplane mode, Bluetooth, GPS and battery saver in Doze. The notification blocklist,
  the allowlist broadcasts, upstream's translations, the website, the store metadata and the release
  workflow.
- Upstream's `WAKE_LOCK`, `CHANGE_WIFI_STATE`, `READ_PHONE_STATE` and `WRITE_SETTINGS` permissions.
  The app has never had a network permission, and CI keeps it that way.
