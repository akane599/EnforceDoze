# Capabilities: Google sdk_gphone64_x86_64, API 37 (CE2A.260420.051.A3)

Captured 2026-11-08T00:04:45Z by the app's capability probes (SPEC 4.4).

- Android 17, fingerprint `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.051.A3/16571990:userdebug/dev-keys`
- Tier T0
- Probes that change state (force idle, sensor restriction) were skipped

| Probe | Result | Detail |
| --- | --- | --- |
| dumpsys deviceidle (`deviceidle.read`) | MISSING | Needs DUMP or the worker. Permission Denial: can't dump DeviceIdleController from from pid=2350, uid=10234 due to missing android.permission.DUMP permission |
| device_idle keys (`deviceidle.keys`) | SKIPPED | No deviceidle dump. |
| Vendor fields (`deviceidle.vendor`) | SKIPPED | No deviceidle dump. |
| Lever A write (global string) (`constants.write`) | MISSING | Needs WRITE_SECURE_SETTINGS (granted through the worker at setup, or over ADB). |
| Forced deep idle (`deviceidle.force`) | SKIPPED | Needs the worker. |
| Sensor restriction (`sensorservice.restrict`) | SKIPPED | Needs the worker. |
| All-sensors privacy (hidden API) (`sensorprivacy.hidden`) | SKIPPED | Needs the worker. |
| Event log (`eventlog.read`) | MISSING | Needs READ_LOGS (and the system's log-access consent) or the worker. |
| Battery history (`batterystats.history`) | MISSING | Needs DUMP and the usage-stats app op, or the worker. |
| One UI version (`oneui.version`) | SKIPPED | Not a Samsung One UI build. |
| Lever A persistence (`constants.persistence`) | SKIPPED | Needs the global string to be checked 10 minutes after a write and after a reboot; it runs with the engine (P2). |
