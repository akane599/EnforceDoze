#!/usr/bin/env bash
set -uo pipefail

# The emulator runner shuts down its device when its script ends.
# Collect evidence inside the runner, including after a test failure.
./gradlew --no-daemon connectedDebugAndroidTest
test_status=$?
adb pull /sdcard/Android/data/com.akylas.enforcedoze/files/screenshots device-screenshots
screenshot_status=$?

if (( test_status != 0 )); then
  exit "$test_status"
fi
exit "$screenshot_status"
