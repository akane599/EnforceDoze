#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P3 gate (SPEC 14): a compressed night with Test Everyday and Test Classic, each producing a complete night
# report with the app's own wakeups at 0, the quick proof, the export ZIP, the worker contract and the Room
# tests, then Restore stock (SPEC 8, 13.2).
# Runs on build-server: builds the APKs, copies them to akane-linux and runs
# tools/emulator/p3-evidence-remote.sh there. Results land in docs/evidence/p3/.
#
#   tools/emulator/p3-evidence.sh [--avd ed-api37] [--scenarios "setup night-everyday ..."]
#
# `--scenarios alarm-fixture` saves only `dumpsys alarm` (read-only) and copies it into the matching API 37
# fixtures/ capture, if that capture exists (ADR 0019). Only the project's own AVD is used (ADR 0021).
set -euo pipefail
cd "$(dirname "$0")/../.."

avd=ed-api37
scenarios="setup night-everyday night-classic quick-proof export restore contract dao"
while (($#)); do
  case $1 in
    --avd) avd=$2; shift 2 ;;
    --scenarios) scenarios=$2; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
remote_dir=.cache/enforcedoze-p3
ssh akane-linux "rm -rf $remote_dir && mkdir -p $remote_dir" </dev/null
if [[ $scenarios != alarm-fixture ]]; then
  ./gradlew -q :app:assembleDebug :core:privilege:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest
  scp -q app/build/outputs/apk/debug/app-debug.apk akane-linux:$remote_dir/app.apk
  scp -q core/privilege/build/outputs/apk/androidTest/debug/privilege-debug-androidTest.apk akane-linux:$remote_dir/privilege-test.apk
  scp -q core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk akane-linux:$remote_dir/data-test.apk
fi
scp -q tools/emulator/p3-evidence-remote.sh akane-linux:$remote_dir/run.sh

set +e
ssh akane-linux "bash $remote_dir/run.sh '$avd' '$scenarios'" </dev/null
status=$?
set -e

evidence=docs/evidence/p3
mkdir -p "$evidence"
tmp=$(mktemp -d)
scp -q "akane-linux:$remote_dir/out/*.txt" "$tmp/" 2>/dev/null || true
for alarm in "$tmp"/alarm-api*.txt; do
  [[ -f $alarm ]] || continue
  capture=fixtures/sdk_gphone64_x86_64/$(basename "$alarm" .txt | sed 's/^alarm-//')
  if [[ -d $capture ]]; then mv "$alarm" "$capture/alarm.txt"; echo "added $capture/alarm.txt"
  else echo "no capture $capture for $(basename "$alarm"); left out"; rm "$alarm"; fi
done
if [[ $scenarios == alarm-fixture ]]; then
  cat "$tmp/summary.txt" >> "$evidence/alarm-fixtures.txt"
else
  rm -f "$evidence"/*.txt
  mv "$tmp"/*.txt "$evidence/"
fi
rm -rf "$tmp"
echo "Remote run exited with $status; logs in $evidence/"
exit $status
