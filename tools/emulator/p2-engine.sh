#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P2 gate (SPEC 14): the engine on the API 37 emulator. Compressed nights with Test Everyday and Test
# Classic (SPEC 13.2), the hold-offs and drift with the loop guard (SPEC 13.3), privilege lost while
# idle is forced (ADR 0015), and Restore stock checked against stock.json.
# Runs on build-server: builds the debug app and the silent-audio test APK, copies them with
# tools/emulator/p2-engine-remote.sh to akane-linux and runs it there. Logs land in docs/evidence/p2/.
# The default run takes about 12 minutes. `persistence` (SPEC 4.4: 10 minutes and a reboot) adds 11 more,
# so it is left out of the default to keep the emulator suite under 20 minutes (SPEC 13); add it before
# `restore` when the lever A write path changes.
#
#   tools/emulator/p2-engine.sh [--avd pixel-api37] [--scenarios "setup night-everyday night-classic holdoffs drift privilege restore"]
set -euo pipefail
cd "$(dirname "$0")/../.."

avd=pixel-api37
scenarios="setup night-everyday night-classic holdoffs drift privilege restore"
while (($#)); do
  case $1 in
    --avd) avd=$2; shift 2 ;;
    --scenarios) scenarios=$2; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
./gradlew -q :app:assembleDebug :testing:silent-audio:assembleDebug

remote_dir=.cache/enforcedoze-p2
ssh akane-linux "rm -rf $remote_dir/out && mkdir -p $remote_dir" </dev/null
scp -q app/build/outputs/apk/debug/app-debug.apk akane-linux:$remote_dir/app.apk
scp -q testing/silent-audio/build/outputs/apk/debug/silent-audio-debug.apk akane-linux:$remote_dir/silent.apk
scp -q tools/emulator/p2-engine-remote.sh akane-linux:$remote_dir/run.sh

set +e
ssh akane-linux "bash $remote_dir/run.sh '$avd' '$scenarios'" </dev/null
status=$?
set -e

mkdir -p docs/evidence/p2
scp -q "akane-linux:$remote_dir/out/*" docs/evidence/p2/ 2>/dev/null || true
rm -f docs/evidence/p2/emulator.log
echo "Remote run exited with $status; logs in docs/evidence/p2/"
exit $status
