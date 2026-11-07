#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Turn a folder of raw phone dumps into a committed, redacted fixture directory (ADR 0008).

    python3 -I tools/fixtures/import_phone_dumps.py <raw-dir> [--repo .]
        [--system-packages FILE] [--third-party-packages FILE] [--map FILE]

<raw-dir> holds the owner's adb captures (getprop.txt, deviceidle.txt, sensorservice.txt, events.txt,
batterystats_history.txt, alarm.txt, power.txt, allowlist.txt, legacy_constants.txt, device_config.txt,
settings_global.txt, services.txt). It is personal data and stays outside git (.gitignore).

Redaction is Coulomb's tools/fixtures/redact.py (copied unchanged): same-length masks for e-mail
addresses, account names, SSIDs, phone numbers, MAC addresses and serials, and consistent pseudonyms for
every package that is not a system package of the device (the owner's own io.github.akane599.* and com.akane.* apps keep
their names). The system package list and the pseudonym mapping are shared with Coulomb and live
outside the repository (~/.config/coulomb-fixtures/), so one app has one pseudonym in both projects.

getprop.txt is not copied: meta.txt keeps only build and model properties. settings_global.txt is cut
down to platform power and Doze keys; dotted keys written by third-party modules are dropped. Every output file is scanned again; the script fails if anything that looks
personal or like an unmapped third-party package survives.
"""
from __future__ import annotations

import argparse
import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import redact  # noqa: E402  (Coulomb's redactor, next to this file)

CONFIG = Path.home() / ".config/coulomb-fixtures"

# raw name -> (fixture name, command the owner ran, as recorded in the README)
FILES = {
    "deviceidle.txt": ("deviceidle.txt", "dumpsys deviceidle"),
    "sensorservice.txt": ("sensorservice.txt", "dumpsys sensorservice"),
    "events.txt": ("events.txt", "logcat -b events -d (threadtime format, device_idle* tags)"),
    "batterystats_history.txt": ("batterystats-history.txt", "dumpsys batterystats --history"),
    "alarm.txt": ("alarm.txt", "dumpsys alarm"),
    "power.txt": ("power.txt", "dumpsys power"),
    "allowlist.txt": ("deviceidle-whitelist.txt", "cmd deviceidle whitelist"),
    "legacy_constants.txt": ("settings-device_idle_constants.txt", "settings get global device_idle_constants"),
    "device_config.txt": ("device_config-device_idle.txt", "device_config list device_idle"),
    "services.txt": ("services.txt", "service list"),
    "settings_global.txt": ("settings-global-power.txt", "settings list global, power and Doze keys only"),
}
META_PROPS = (
    "ro.product.manufacturer", "ro.product.model", "ro.product.device", "ro.build.fingerprint",
    "ro.build.version.sdk", "ro.build.version.release", "ro.build.id", "ro.build.version.incremental",
    "ro.build.PDA", "ro.build.version.oneui", "ro.build.version.security_patch", "ro.build.type",
)
# Properties whose values identify this phone; they are masked wherever they appear in other files.
SECRET_PROP = re.compile(r"serial|imei|meid|iccid|uniqueno|\.did$|device_name|bt_name|bluetooth_name|wifimac|btmac", re.I)
SETTINGS_KEEP = re.compile(r"idle|doze|low_power|power_mode|power_saving|standby|saver|psm|battery|stay_on|app_restrict", re.I)
# Reverse-DNS tokens left after redaction must be system packages, kept prefixes or pseudonyms; anything
# else is reported. Framework namespaces that are not packages are allowed explicitly.
DOTTED = re.compile(rb"(?<![A-Za-z0-9_.])((?:com|net|org|io|de|me|app|dev|sh|tr|co|uk|ru|jp|cn|kr|in|eu|fr|nl|br|tv|us|moe|xyz|ai|it|es|pl|ch|se|at|be|cz)"
                    rb"(?:\.[A-Za-z0-9_]+){1,})")
NOT_PACKAGES = (b"com.android.", b"com.google.android.", b"com.samsung.", b"com.sec.", b"io.github.akane599.",
                b"com.qualcomm.", b"com.qti.", b"org.codeaurora.", b"vendor.")


def parse_getprop(path: Path) -> dict[str, str]:
    props = {}
    for line in path.read_text(errors="replace").splitlines():
        m = re.match(r"\[([^\]]+)\]: \[(.*)\]$", line)
        if m:
            props[m.group(1)] = m.group(2)
    return props


# Names that are packages by their context: pkg=/package=/packageName=, u0aNN:"pkg", component names
# (pkg/.Class), intent actions (act=, action=) and the allowlist's "kind,pkg,uid" lines. redact.py only
# looks at batterystats patterns and the package lists, which miss apps installed after the list was
# taken and apps that appear only in alarm, power or sensor dumps.
PKG = rb"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+"
CONTEXT = [
    re.compile(rb"(?:pkg|package|packageName|callingPackage|opPackageName|act|action)[=:] ?\"?(" + PKG + rb")"),
    re.compile(rb"\bu\d+a\d+:\"?(" + PKG + rb")"),
    re.compile(rb"(?<![A-Za-z0-9_./])(" + PKG + rb")/[A-Za-z0-9_.$]*[A-Z.]"),
    re.compile(rb"^[a-z-]+,(" + PKG + rb"),\d+$", re.M),
]
# Vendor and framework namespaces are left to the system list: their class and flag names are not apps.
# Library namespaces (wake lock and worker tags) are not apps either.
FRAMEWORK = (b"android.", b"androidx.", b"java.", b"javax.", b"dalvik.", b"kotlin.", b"com.android.",
             b"com.google.android.gms.", b"vendor.", b"org.codeaurora.", b"com.qualcomm.", b"com.qti.",
             b"com.google.firebase.", b"com.google.apps.tiktok.", b"com.google.ccc.", b"com.google.common.",
             b"com.google.protobuf.", b"com.google.android.libraries.", b"com.google.android.datatransport.")


def extra_packages(files: list[Path], system: set[str], keep: tuple[str, ...]) -> set[str]:
    sysb = {p.encode() for p in system}
    # Every dotted prefix of a system package (com.google, com.samsung.android) is shared namespace, not
    # an app; replacing it would rename the system packages under it.
    shared = {s[:i] for s in sysb for i in range(len(s)) if s[i:i + 1] == b"."}
    found: set[str] = set()
    for f in files:
        data = f.read_bytes()
        for rx in CONTEXT:
            for m in rx.finditer(data):
                tok = m.group(1).rstrip(b".")
                if tok in sysb or tok in shared or tok.startswith(FRAMEWORK) \
                        or any(tok.startswith(s + b".") for s in sysb):
                    continue
                name = tok.decode()
                if not any(name.startswith(k) for k in keep):
                    found.add(name)
        for m in DOTTED.finditer(data):  # any reverse-DNS name outside vendor namespaces
            tok = m.group(1).rstrip(b".")
            if tok.startswith(NOT_PACKAGES) or tok.startswith(FRAMEWORK) or tok in sysb or tok in shared \
                    or any(tok.startswith(s + b".") for s in sysb) or any(tok.decode().startswith(k) for k in keep):
                continue
            found.add(tok.decode())
    return found


def leftover_packages(data: bytes, known: set[bytes], keep: tuple[str, ...]) -> set[bytes]:
    left = set()
    for m in DOTTED.finditer(data):
        tok = m.group(1).rstrip(b".")
        if tok.startswith(NOT_PACKAGES) or tok.startswith(FRAMEWORK) or any(tok.startswith(k.encode()) for k in keep):
            continue
        if any(k.startswith(tok + b".") for k in known):  # shared namespace such as com.google
            continue
        if any(tok == k or tok.startswith(k + b".") for k in known):
            continue
        left.add(tok)
    return left


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("raw", type=Path)
    ap.add_argument("--repo", type=Path, default=Path("."))
    ap.add_argument("--system-packages", type=Path, default=CONFIG / "sm-s938b-system.txt")
    ap.add_argument("--third-party-packages", type=Path, default=CONFIG / "sm-s938b-third-party.txt")
    ap.add_argument("--map", type=Path, default=CONFIG / "pseudonyms.json")
    ap.add_argument("--keep-prefix", action="append")
    args = ap.parse_args()

    raw: Path = args.raw
    props = parse_getprop(raw / "getprop.txt")
    device = props["ro.product.model"].lower()
    build = f"api{props['ro.build.version.sdk']}_{props['ro.build.id']}"
    out = args.repo / "fixtures" / device / build

    secrets = sorted({v.encode() for k, v in props.items() if SECRET_PROP.search(k) and len(v) >= 6})
    for line in (raw / "settings_global.txt").read_text(errors="replace").splitlines():
        if line.startswith(("device_name=", "bluetooth_name=")) and len(line.split("=", 1)[1]) >= 6:
            secrets.append(line.split("=", 1)[1].encode())

    pseudo = redact.make_pseudonymiser(args, args.system_packages, args.third_party_packages)
    sources = [raw / name for name in FILES if (raw / name).exists()]
    third = redact.read_package_list(args.third_party_packages)
    extra = extra_packages(sources, pseudo.system, pseudo.keep_prefixes) - third
    pseudo.discover(sources, third | extra)
    red = redact.Redactor(secrets, pseudo)

    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    rows = []
    for name, (dest, command) in FILES.items():
        src = raw / name
        if not src.exists():
            rows.append(f"| `{dest}` | `{command}` | not captured |")
            continue
        data = src.read_bytes()
        if name == "settings_global.txt":
            # Keys with a dot are written by third-party modules, not the platform; they would name them.
            data = b"".join(l for l in data.splitlines(keepends=True)
                            if SETTINGS_KEEP.search(k := l.split(b"=", 1)[0].decode(errors="replace")) and "." not in k)
        (out / dest).write_bytes(red.text(data))
        rows.append(f"| `{dest}` | `{command}` | {len(data)} bytes{'' if data else ' (empty output)'} |")

    meta = [f"captured_at={datetime.fromtimestamp((raw / 'getprop.txt').stat().st_mtime, timezone.utc):%Y-%m-%dT%H:%M:%SZ}",
            "mode=read-only (owner's adb capture)"]
    meta += [f"{k}={props[k]}" for k in META_PROPS if k in props]
    (out / "meta.txt").write_text("\n".join(meta) + "\n")

    # Only real pseudonyms count as known: unchanged prefixes such as "com" would hide everything.
    known = {p.encode() for p in pseudo.system} | set(pseudo.targets.values()) \
        | {v.encode() for k, v in pseudo.prefixes.items() if k != v and "." in v}
    problems = []
    for f in sorted(out.iterdir()):
        problems += redact.scan(f)
        problems += [f"{f.name}: unmapped package-like token {t[:60]!r}" for t in sorted(leftover_packages(f.read_bytes(), known, pseudo.keep_prefixes))]

    counts = ", ".join(f"{k}: {v}" for k, v in sorted(red.counts.items())) or "none"
    (out / "README.md").write_text(f"""# Fixtures: {device} / {build}

Real Galaxy S25 Ultra dumps captured read-only by the owner over adb on {meta[0].split('=', 1)[1]}, converted with
`tools/fixtures/import_phone_dumps.py` (ADR 0008). Firmware `{props.get('ro.build.PDA', '?')}`, One UI
`{props.get('ro.build.version.oneui', '?')}`, Android {props['ro.build.version.release']} (API {props['ro.build.version.sdk']}).

| File | Command | Raw size |
| --- | --- | --- |
{chr(10).join(rows)}
| `meta.txt` | build and model properties from `getprop` | |

## Redaction

Same-length masks (Coulomb's `redact.py`): {counts}.
Packages that are not system packages of this firmware are replaced by consistent pseudonyms shared
with Coulomb's fixtures: {len(pseudo.targets)} packages, {pseudo.count} occurrences; UIDs unchanged. The
owner's own apps (`io.github.akane599.*`, `com.akane.*`, Coulomb's default keep prefixes) keep their names. `getprop` is reduced to `meta.txt`, and the
global settings to platform power and Doze keys (keys written by third-party modules are dropped). Parsers must not depend on third-party package names.
""")
    pseudo.save()
    print(f"fixtures written: {out}")
    print(f"replacements: {counts}; pseudonymised packages: {len(pseudo.targets)}, occurrences: {pseudo.count}")
    for p in problems[:80]:
        print("  REVIEW " + p)
    print(f"review items: {len(problems)}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
