# Fixtures

Raw system dumps for parser tests (SPEC 0.1, 8, 12.4). Layout: `<model>/api<SDK>_<build id>/`.
Move to `:testing:fixtures` when that module exists (SPEC 12.3); keep the files byte-for-byte.

Captured with `tools/fixtures/capture.sh` on the machine that owns the adb server:

```sh
ADB=~/Android/Sdk/platform-tools/adb tools/fixtures/capture.sh --walk fixtures   # emulators only
tools/fixtures/capture.sh -s <serial> fixtures                                  # read-only, safe on a phone
```

| File | Content |
| --- | --- |
| `meta.txt` | Capture time, mode, fingerprint, SDK, build id |
| `help-*.txt` | The device's own help for `cmd deviceidle`, `cmd device_config`, `dumpsys batterystats` |
| `deviceidle.txt`, `deviceidle-get.txt` | `dumpsys deviceidle` and `cmd deviceidle get …` at rest |
| `device_config-device_idle.txt`, `settings-device_idle_constants.txt` | Constants overrides (empty / `null` on stock emulators) |
| `deviceidle-whitelist.txt` | `cmd deviceidle whitelist` |
| `sensorservice.txt` | `dumpsys sensorservice` at rest |
| `walk-*.txt` | Command outputs during the walk: screen off, light and deep steps, force-idle, sensor restriction |
| `deviceidle-stepped-*.txt`, `deviceidle-forced.txt` | `dumpsys deviceidle` in light idle, deep IDLE, IDLE_MAINTENANCE and forced idle |
| `sensorservice-restricted*.txt`, `sensorservice-enabled.txt` | Sensor service after a bare `restrict` (rejected), `restrict com.android.shell`, and `enable` |
| `events.txt` | `logcat -b events -d -v epoch` after the walk (all tags) |
| `batterystats-history.txt` | `dumpsys batterystats --history` after the walk |

| Directory | Build | Notes |
| --- | --- | --- |
| `sdk_gphone64_x86_64/api35_AE3A.240806.043` | Android 15 emulator | Walks LOCATING |
| `sdk_gphone64_x86_64/api36_BE2A.250530.026.F3` | Android 16 emulator | `remove_idle_location=true` |
| `sdk_gphone64_x86_64/api37_CE2A.260420.051.A3` | Android 17 emulator | Same flag; its battery history also covers an upstream 1.10.2 run |
| `sm-s938b/api37_CP2A.260605.016` | One UI 9, Android 17 (owner's phone) | Read-only capture, redacted; its own README lists files and commands |

Emulator dumps contain no personal data. Phone captures are always read-only (SPEC 0.4) and are
never committed raw: the owner's dumps stay in the git-ignored `oneui_dumps/` and are converted with
the shared Coulomb redactor (ADR 0008):

```sh
python3 -I tools/fixtures/import_phone_dumps.py oneui_dumps   # writes fixtures/<model>/api<SDK>_<build>/
```

The script fails if any unmapped package name or personal value survives. Tests must not depend on
third-party package names in phone fixtures; they are pseudonyms.
