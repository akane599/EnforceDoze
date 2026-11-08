#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P1 gate (SPEC 14): worker contract and tier detection on the API 37 emulator, in four scenarios:
# none (T0), grants over ADB (T1), Shizuku as shell (T2) and Shizuku after `adb root` (T3).
# Runs on build-server: builds the test APK, copies it with a Shizuku server APK to akane-linux and
# runs tools/emulator/p1-privilege-remote.sh there. Results land in docs/evidence/p1/ and the
# capability reports in docs/capabilities/.
#
#   tools/emulator/p1-privilege.sh [--shizuku thedjchi|official] [--avd ed-api37] [--scenarios "none grants shell root"]
#
# The Shizuku APKs are downloaded once from the projects' GitHub releases into
# ~/.cache/enforcedoze-shizuku on akane-linux (ADR 0011).
set -euo pipefail
cd "$(dirname "$0")/../.."

shizuku=thedjchi
avd=ed-api37
scenarios="none grants shell root"
while (($#)); do
  case $1 in
    --shizuku) shizuku=$2; shift 2 ;;
    --avd) avd=$2; shift 2 ;;
    --scenarios) scenarios=$2; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
./gradlew -q :core:privilege:assembleDebugAndroidTest
apk=core/privilege/build/outputs/apk/androidTest/debug/privilege-debug-androidTest.apk

remote_dir=.cache/enforcedoze-p1
ssh akane-linux "rm -rf $remote_dir && mkdir -p $remote_dir" </dev/null
scp -q "$apk" akane-linux:$remote_dir/test.apk
scp -q tools/emulator/p1-privilege-remote.sh akane-linux:$remote_dir/run.sh

set +e
ssh akane-linux "bash $remote_dir/run.sh '$avd' '$shizuku' '$scenarios'" </dev/null
status=$?
set -e

mkdir -p docs/evidence/p1 docs/capabilities
scp -q "akane-linux:$remote_dir/out/*.txt" docs/evidence/p1/ 2>/dev/null || true
scp -q "akane-linux:$remote_dir/out/capabilities/*.md" docs/capabilities/ 2>/dev/null || true
echo "Remote run exited with $status; logs in docs/evidence/p1/"
exit $status
