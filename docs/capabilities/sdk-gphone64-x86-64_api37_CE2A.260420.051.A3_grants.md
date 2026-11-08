# Capabilities: Google sdk_gphone64_x86_64, API 37 (CE2A.260420.051.A3)

Captured 2026-11-08T00:04:49Z by the app's capability probes (SPEC 4.4).

- Android 17, fingerprint `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.051.A3/16571990:userdebug/dev-keys`
- Tier T1
- Probes that change state (force idle, sensor restriction) were skipped

| Probe | Result | Detail |
| --- | --- | --- |
| dumpsys deviceidle (`deviceidle.read`) | OK | Read with the DUMP grant. deep=ACTIVE light=ACTIVE force=false |
| device_idle keys (`deviceidle.keys`) | OK | 36 AOSP keys present. |
| Vendor fields (`deviceidle.vendor`) | OK | None beyond AOSP; no motion sensor. |
| Lever A write (global string) (`constants.write`) | SKIPPED | WRITE_SECURE_SETTINGS held; no worker to read the string back first, so no test write. |
| Forced deep idle (`deviceidle.force`) | SKIPPED | Needs the worker. |
| Sensor restriction (`sensorservice.restrict`) | SKIPPED | Needs the worker. |
| All-sensors privacy (hidden API) (`sensorprivacy.hidden`) | SKIPPED | Needs the worker. |
| Event log (`eventlog.read`) | OK | The app reads the events buffer with READ_LOGS; tags: device_idle, device_idle_light. |
| Battery history (`batterystats.history`) | MISSING | Needs DUMP and the usage-stats app op, or the worker. |
| One UI version (`oneui.version`) | SKIPPED | Not a Samsung One UI build. |
| Lever A persistence (`constants.persistence`) | SKIPPED | Needs the global string to be checked 10 minutes after a write and after a reboot; it runs with the engine (P2). |
