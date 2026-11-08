# Capabilities: Google sdk_gphone64_x86_64, API 37 (CE2A.260420.051.A3)

Captured 2026-11-08T00:04:54Z by the app's capability probes (SPEC 4.4).

- Android 17, fingerprint `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.051.A3/16571990:userdebug/dev-keys`
- Tier T2 through SHIZUKU, worker uid 2000
- Probes that change state (force idle, sensor restriction) ran and were undone

| Probe | Result | Detail |
| --- | --- | --- |
| dumpsys deviceidle (`deviceidle.read`) | OK | Read through the worker. deep=ACTIVE light=ACTIVE force=false |
| device_idle keys (`deviceidle.keys`) | OK | 36 AOSP keys present. |
| Vendor fields (`deviceidle.vendor`) | OK | None beyond AOSP; no motion sensor. |
| Lever A write (global string) (`constants.write`) | OK | wrote location_accuracy=21.0 from the app: dumpsys showed it; previous string restored exactly |
| Forced deep idle (`deviceidle.force`) | OK | force-idle deep: deep=IDLE force=true; unforce: force=false |
| Sensor restriction (`sensorservice.restrict`) | OK | restrict io.github.akane599.enforcedoze.privilege.test: `Mode : RESTRICTED : io.github.akane599.enforcedoze.privilege.test`; restored: `Mode : NORMAL` |
| All-sensors privacy (hidden API) (`sensorprivacy.hidden`) | OK | isSensorPrivacyEnabled() = false; setSensorPrivacy(false): ok |
| Event log (`eventlog.read`) | OK | Read through the worker; tags: device_idle, device_idle_step, device_idle_on_start, device_idle_on_phase, device_idle_on_complete, device_idle_off_start, device_idle_off_phase, device_idle_off_complete, device_idle_light. READ_LOGS in the app: no device_idle lines. |
| Battery history (`batterystats.history`) | OK | dumpsys batterystats --history through the worker: Battery History [Format: 2] (7% used, 307KB used of 4096KB, 302 strings using 20KB): |
| One UI version (`oneui.version`) | SKIPPED | Not a Samsung One UI build. |
| Lever A persistence (`constants.persistence`) | SKIPPED | Needs the global string to be checked 10 minutes after a write and after a reboot; it runs with the engine (P2). |
