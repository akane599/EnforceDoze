# 0008. Phone fixtures: redaction shared with Coulomb

- Status: Accepted
- Date: 2026-11-08
- Phase: P1
- Spec: 0.4, 8, 12.4, 13; `fixtures/README.md`

## Context

On 2026-11-08 the owner put read-only adb captures from the Galaxy S25 Ultra (SM-S938B, One UI 9,
Android 17, build `CP2A.260605.016`) in `oneui_dumps/` with the instruction: never commit them raw,
anonymise package names and turn them into fixtures. The raw files name every installed app, carry
the serial and device name in `getprop`, and hold global settings written by other apps.

The owner's other project, Coulomb, already has a redactor (`tools/fixtures/redact.py`) and a
pseudonym mapping kept outside its repository, so one app gets one pseudonym in both projects.

## Decision

- `oneui_dumps/` is ignored by git. Raw phone dumps never enter the repository or CI.
- `tools/fixtures/redact.py` is Coulomb's redactor copied unchanged (provenance in its header):
  same-length masks for e-mail addresses, account names, SSIDs, phone numbers, MAC addresses and
  serials, and consistent pseudonyms for every package that is not a system package of the
  firmware. UIDs stay as they are.
- `tools/fixtures/import_phone_dumps.py` writes `fixtures/<model>/api<SDK>_<build id>/`:
  - `getprop` is reduced to `meta.txt` (model, build and One UI properties only). The serial, IMEI,
    device name and Bluetooth name are read from it and masked wherever else they appear.
  - Global settings are cut down to platform power and Doze keys. Dotted keys, which third-party
    modules write, are dropped, because their names identify the module.
  - Packages are found from Coulomb's third-party list and also in context (`pkg=`, `package:`,
    component names, allowlist rows, action strings, a reverse-DNS scan). The context search
    catches apps installed after Coulomb's list was taken. Platform and library namespaces
    (`android.`, `androidx.`, `com.google.android.gms.` and others listed in the script) and
    prefixes shared with system packages are left alone.
  - The owner's own `io.github.akane599.*` apps keep their names.
  - Every output file is scanned again. The run fails on any unmapped package-like token or
    anything that looks personal, and the fixture README records the counts.
- The system package list, Coulomb's third-party list and the mapping live in
  `~/.config/coulomb-fixtures/` (mode 600) on build-server, never in the repository.
- Parsers and tests must not depend on third-party package names, only on structure, system
  packages and UIDs.

## Consequences

- First import: 247 packages pseudonymised (3876 occurrences), nothing else needed masking, and an
  independent leak check found nothing.
- A new capture (another firmware or a night) is imported with the same command into a new
  directory. Losing the mapping only changes future pseudonyms; committed fixtures stay valid.
- The phone's event log is in `threadtime` format while the emulator fixtures use `epoch`, so the
  event parser (SPEC 8.1) reads both.
- What the dumps show about One UI 9 is recorded in `docs/upstream-audit.md` §4.4 and feeds P2.
