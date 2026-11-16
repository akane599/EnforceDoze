#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# The whole emulator suite (SPEC 13: under 20 minutes; SPEC 15) on one boot of ed-api37. Each phase's own
# script runs against the suite's emulator (SUITE_SERIAL, tools/emulator/lib.sh), and overlapping parts run
# once: one compressed night (Test Classic, with its report) in P3, and P2's hold-offs, drift, privilege and
# Restore stock without its nights. The owner's load rule is checked once, before the boot; exit 75 means
# akane-linux was busy and nothing ran. Phase evidence lands in docs/evidence/<phase>/, the timing in
# docs/evidence/suite/summary.txt.
#
#   tools/emulator/suite.sh [--avd ed-api37]
set -euo pipefail
cd "$(dirname "$0")/../.."

avd=ed-api37
while (($#)); do
  case $1 in
    --avd) avd=$2; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done
limit=1200

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
# Everything once, so each phase's own build is already up to date.
./gradlew -q :app:assembleDebug :app:assembleBenchmark :testing:benchmark:assembleBenchmark \
  :testing:silent-audio:assembleDebug :testing:evidence-client:assembleDebug \
  :core:privilege:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest

remote_dir=.cache/enforcedoze-suite
ssh akane-linux "mkdir -p $remote_dir" </dev/null
scp -q tools/emulator/lib.sh akane-linux:$remote_dir/lib.sh
scp -q tools/emulator/suite-remote.sh akane-linux:$remote_dir/suite-remote.sh

set +e
serial=$(ssh akane-linux "bash $remote_dir/suite-remote.sh start '$avd'" </dev/null)
status=$?
set -e
if ((status == 75)); then echo "akane-linux busy; nothing ran (exit 75)"; exit 75; fi
((status == 0)) || { echo "the suite's emulator did not start ($status)"; exit 1; }
trap 'ssh akane-linux "bash $remote_dir/suite-remote.sh stop $serial" </dev/null' EXIT
export SUITE_SERIAL=$serial

summary=$(mktemp)
started=$(date +%s)
failed=0
phase() { # <name> <script> [args...]
  local name=$1 t0 status
  shift
  t0=$(date +%s)
  set +e
  "$@"
  status=$?
  set -e
  ((status == 0)) || failed=$((failed + 1))
  printf '%-10s exit %-3s %4s s\n' "$name" "$status" "$(($(date +%s) - t0))" | tee -a "$summary"
}

phase p1 tools/emulator/p1-privilege.sh --avd "$avd"
phase p2 tools/emulator/p2-engine.sh --avd "$avd" --scenarios "setup holdoffs drift privilege restore"
phase p3 tools/emulator/p3-evidence.sh --avd "$avd" --scenarios "setup night-classic quick-proof export restore contract dao"
phase p4 tools/emulator/p4-plan.sh --avd "$avd"
phase p5 tools/emulator/p5-ui.sh --avd "$avd"
phase p6 tools/emulator/p6-integrations.sh --avd "$avd"
phase p6-extras tools/emulator/p6-extras.sh --avd "$avd"

total=$(($(date +%s) - started))
mkdir -p docs/evidence/suite
{
  echo "Emulator suite on $avd ($serial), $(date -u +%FT%TZ)"
  cat "$summary"
  echo "total $total s (limit $limit s); $failed phase(s) failed"
} > docs/evidence/suite/summary.txt
rm -f "$summary"
cat docs/evidence/suite/summary.txt
((failed == 0 && total < limit))
