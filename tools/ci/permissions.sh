#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# SPEC 1.3 and ADR 0037's permission audit: the merged debug manifest asks for exactly the permissions in
# tools/ci/permissions.txt, so neither a library nor a module can add one unnoticed (INTERNET above all).
# Run after :app:processDebugMainManifest (assembleDebug runs it).
set -euo pipefail
cd "$(dirname "$0")/../.."

manifest=app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
expected=tools/ci/permissions.txt
test -f "$manifest" || { echo "no merged manifest at $manifest: build the debug app first"; exit 1; }

found=$(python3 -I - "$manifest" <<'PY'
import sys, xml.etree.ElementTree as ET
A = '{http://schemas.android.com/apk/res/android}name'
root = ET.parse(sys.argv[1]).getroot()
names = {e.get(A) for tag in ('uses-permission', 'uses-permission-sdk-23') for e in root.iter(tag)}
print('\n'.join(sorted(n for n in names if n)))
PY
)
want=$(grep -v '^#' "$expected" | sed '/^$/d' | sort)

if grep -qx 'android.permission.INTERNET' <<<"$found"; then
  echo "android.permission.INTERNET is in the merged manifest"; exit 1
fi
if [[ $found != "$want" ]]; then
  echo "The merged manifest's permissions differ from $expected (< merged, > expected):"
  diff <(echo "$found") <(echo "$want") || true
  exit 1
fi
echo "Permissions match $expected ($(wc -l <<<"$found") entries); no INTERNET."
