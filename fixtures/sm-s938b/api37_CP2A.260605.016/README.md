# Fixtures: sm-s938b / api37_CP2A.260605.016

Real Galaxy S25 Ultra dumps captured read-only by the owner over adb on 2026-11-07T21:59:10Z, converted with
`tools/fixtures/import_phone_dumps.py` (ADR 0008). Firmware `S938BXXSDDZJ1`, One UI
`90000`, Android 17 (API 37).

| File | Command | Raw size |
| --- | --- | --- |
| `deviceidle.txt` | `dumpsys deviceidle` | 21322 bytes |
| `sensorservice.txt` | `dumpsys sensorservice` | 234960 bytes |
| `events.txt` | `logcat -b events -d (threadtime format, device_idle* tags)` | 3280 bytes |
| `batterystats-history.txt` | `dumpsys batterystats --history` | 3345143 bytes |
| `alarm.txt` | `dumpsys alarm` | 235599 bytes |
| `power.txt` | `dumpsys power` | 60052 bytes |
| `deviceidle-whitelist.txt` | `cmd deviceidle whitelist` | 8615 bytes |
| `settings-device_idle_constants.txt` | `settings get global device_idle_constants` | 148 bytes |
| `device_config-device_idle.txt` | `device_config list device_idle` | 0 bytes (empty output) |
| `services.txt` | `service list` | 33682 bytes |
| `settings-global-power.txt` | `settings list global, power and Doze keys only` | 1804 bytes |
| `meta.txt` | build and model properties from `getprop` | |

## Redaction

Same-length masks (Coulomb's `redact.py`): none.
Packages that are not system packages of this firmware are replaced by consistent pseudonyms shared
with Coulomb's fixtures: 247 packages, 3876 occurrences; UIDs unchanged. The
owner's own `io.github.akane599.*` apps keep their names. `getprop` is reduced to `meta.txt`, and the
global settings to platform power and Doze keys (keys written by third-party modules are dropped). Parsers must not depend on third-party package names.
