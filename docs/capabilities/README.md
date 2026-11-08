# Capability reports

Output of the app's capability probes (SPEC 4.4), one file per device, build and privilege
scenario: `<model>_api<sdk>_<build id>_<scenario>.md`. Emulator reports come from
`tools/emulator/p1-privilege.sh` (scenarios `none`, `grants`, `shell`, `root`); phone reports are
exported from the Privileges screen.

Probes that change state (the lever A test write, forced idle, sensor restriction, the all-sensors
setter) run only in the worker scenarios and undo what they changed; each report says whether they
ran. The lever A write needs WRITE_SECURE_SETTINGS, which the worker scenarios grant through the
worker as setup does (ADR 0012).
