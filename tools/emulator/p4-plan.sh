#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P4 gate (SPEC 14), emulator part: the plan's day-to-night and night-to-day switches on a real system
# (SPEC 6.5, ADR 0024). The window-start alarm with the screen on, the night that waits for a maintenance
# window with the screen off, and the day that waits for the first unlock after the window end. The policy
# fixtures and the profile round trip are JVM tests.
# Runs on build-server: builds the APK, copies it to akane-linux and runs tools/emulator/p4-plan-remote.sh
# there. Results land in docs/evidence/p4/.
#
#   tools/emulator/p4-plan.sh [--avd ed-api37] [--scenarios "setup alarm unlock maintenance restore"]
#
# Only the project's own AVD is used (ADR 0021).
set -euo pipefail
cd "$(dirname "$0")/../.."

avd=ed-api37
scenarios="setup alarm unlock maintenance restore"
while (($#)); do
  case $1 in
    --avd) avd=$2; shift 2 ;;
    --scenarios) scenarios=$2; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
remote_dir=.cache/enforcedoze-p4
ssh akane-linux "rm -rf $remote_dir && mkdir -p $remote_dir" </dev/null
./gradlew -q :app:assembleDebug
scp -q app/build/outputs/apk/debug/app-debug.apk akane-linux:$remote_dir/app.apk
scp -q tools/emulator/p4-plan-remote.sh akane-linux:$remote_dir/run.sh

set +e
ssh akane-linux "bash $remote_dir/run.sh '$avd' '$scenarios'" </dev/null
status=$?
set -e

evidence=docs/evidence/p4
mkdir -p "$evidence"
rm -f "$evidence"/*.txt
scp -q "akane-linux:$remote_dir/out/*.txt" "$evidence/" 2>/dev/null || true
echo "Remote run exited with $status; logs in $evidence/"
exit $status
