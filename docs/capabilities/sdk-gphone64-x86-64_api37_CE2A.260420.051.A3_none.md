# Capabilities: Google sdk_gphone64_x86_64, API 37 (CE2A.260420.051.A3)

Captured 2026-11-16T15:41:45Z by the app's capability probes (SPEC 4.4).

- Android 17, fingerprint `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.051.A3/16571990:userdebug/dev-keys`
- Tier T0
- Probes that change state (lever A test write, forced idle, sensor restriction, sensor privacy setter) were skipped

| Probe | Result | Detail |
| --- | --- | --- |
| dumpsys deviceidle (`deviceidle.read`) | MISSING | Needs DUMP or the worker. Permission Denial: can't dump DeviceIdleController from from pid=3458, uid=10236 due to missing android.permission.DUMP permission |
| device_idle keys (`deviceidle.keys`) | SKIPPED | No deviceidle dump. |
| Vendor fields (`deviceidle.vendor`) | SKIPPED | No deviceidle dump. |
| Lever A write (global string) (`constants.write`) | MISSING | Needs WRITE_SECURE_SETTINGS (granted through the worker at setup, or over ADB). |
| Forced deep idle (`deviceidle.force`) | SKIPPED | Needs the worker. |
| Sensor restriction (`sensorservice.restrict`) | SKIPPED | Needs the worker. |
| All-sensors privacy (hidden API) (`sensorprivacy.hidden`) | SKIPPED | Needs the worker. |
| Event log (`eventlog.read`) | MISSING | Needs READ_LOGS (and the system's log-access consent) or the worker. |
| Battery history (`batterystats.history`) | MISSING | Needs DUMP and the usage-stats app op, or the worker. |
| One UI version (`oneui.version`) | SKIPPED | Not a Samsung One UI build. |
| Lever A persistence (`constants.persistence`) | SKIPPED | Not a one-off probe: the engine reads the global string back at start and at every screen off and logs a lost write as drift (SPEC 5.6). |
