#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# ADR 0038 on the API 37 emulator: the root-tier extras (Wi-Fi off, mobile data off, app suspension) with Shizuku
# started as root, so the worker runs at T3; a reboot with an extra held ends the run.
# Builds here, runs on akane-linux, and copies the results to docs/evidence/p6-extras/.
set -euo pipefail
cd "$(dirname "$0")/../.."

avd=ed-api37
while (($#)); do
  case $1 in
    --avd) avd=$2; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
./gradlew -q :app:assembleDebug :testing:evidence-client:assembleDebug

remote_dir=.cache/enforcedoze-p6-extras
ssh akane-linux "rm -rf $remote_dir && mkdir -p $remote_dir" </dev/null
scp -q app/build/outputs/apk/debug/app-debug.apk akane-linux:$remote_dir/app.apk
scp -q testing/evidence-client/build/outputs/apk/debug/evidence-client-debug.apk akane-linux:$remote_dir/client.apk
scp -q tools/emulator/p6-extras-remote.sh akane-linux:$remote_dir/run.sh
scp -q tools/emulator/lib.sh akane-linux:$remote_dir/lib.sh

set +e
ssh akane-linux "SUITE_SERIAL='${SUITE_SERIAL:-}' bash $remote_dir/run.sh '$avd'" </dev/null
status=$?
set -e

if ((status == 75)); then
  # Busy: nothing ran, so the evidence of the last real run stays as it is.
  ssh akane-linux "cat $remote_dir/out/summary.txt" </dev/null
  echo "akane-linux busy; nothing ran (exit 75)"
  exit 75
fi
rm -rf docs/evidence/p6-extras
mkdir -p docs/evidence/p6-extras
scp -q "akane-linux:$remote_dir/out/*" docs/evidence/p6-extras/ 2>/dev/null || true
rm -f docs/evidence/p6-extras/emulator.log
echo "Remote run exited with $status; results in docs/evidence/p6-extras/"
exit $status
