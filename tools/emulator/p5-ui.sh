#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P5 on the API 37 emulator (SPEC 10.5, 14): the cold-start macrobenchmark of the release build, then a smoke
# run of the debug build under Shizuku (onboarding opens, the durable grants arrive, nothing crashes).
# Builds here, runs on akane-linux, and copies the results to docs/evidence/p5/.
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
./gradlew -q :app:assembleBenchmark :testing:benchmark:assembleBenchmark :app:assembleDebug

remote_dir=.cache/enforcedoze-p5
ssh akane-linux "rm -rf $remote_dir && mkdir -p $remote_dir" </dev/null
scp -q app/build/outputs/apk/benchmark/app-benchmark.apk akane-linux:$remote_dir/app-benchmark.apk
scp -q testing/benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk akane-linux:$remote_dir/benchmark.apk
scp -q app/build/outputs/apk/debug/app-debug.apk akane-linux:$remote_dir/app.apk
scp -q tools/emulator/p5-ui-remote.sh akane-linux:$remote_dir/run.sh
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
rm -rf docs/evidence/p5
mkdir -p docs/evidence/p5
scp -q "akane-linux:$remote_dir/out/*" docs/evidence/p5/ 2>/dev/null || true
rm -f docs/evidence/p5/emulator.log
echo "Remote run exited with $status; results in docs/evidence/p5/"
exit $status
