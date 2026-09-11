#!/usr/bin/env bash
set -euo pipefail

# Tests use a fresh emulator. Keep APK data until evidence is collected.
gradle_args=(--no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true)
integration_class=com.akylas.enforcedoze.ShizukuIntegrationTest
integration_classes="$integration_class,com.akylas.enforcedoze.SensorShizukuIntegrationTest"
mkdir -p device-test-results
test_package=com.akylas.enforcedoze
notification_permission=android.permission.POST_NOTIFICATIONS
notification_permission_original=
read_notification_permission() {
  adb shell dumpsys package "$test_package" | sed -n \
    's/.*android.permission.POST_NOTIFICATIONS: granted=\(true\|false\).*/\1/p'
}
restore_notification_permission() {
  [[ -z "$notification_permission_original" ]] && return 0
  local action=revoke
  [[ "$notification_permission_original" == true ]] && action=grant
  adb shell pm "$action" "$test_package" "$notification_permission" || return 1
  [[ "$(read_notification_permission)" == "$notification_permission_original" ]] || return 1
  notification_permission_original=
}
collect_evidence() {
  adb pull /sdcard/Android/data/com.akylas.enforcedoze/files/screenshots device-screenshots
}
finish() {
  local result=$?
  # Revoking permission can kill the app, so restore only after instrumentation exits.
  restore_notification_permission || result=1
  collect_evidence || true
  exit "$result"
}
trap finish EXIT

./gradlew "${gradle_args[@]}" \
  "-Pandroid.testInstrumentationRunnerArguments.notClass=$integration_classes" connectedDebugAndroidTest
cp -R app/build/reports/androidTests device-test-results/ui

# Pin the upstream manager and its bytes; it is installed only in this test emulator.
shizuku_apk=shizuku-13.6.0.apk
curl --fail --location --retry 3 \
  'https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk' \
  --output "$shizuku_apk"
echo '6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f  shizuku-13.6.0.apk' | sha256sum --check
unzip -p "$shizuku_apk" lib/x86_64/libshizuku.so > shizuku-starter
adb install -r "$shizuku_apk"
adb push shizuku-starter /data/local/tmp/enforcedoze-shizuku-starter
adb shell chmod 755 /data/local/tmp/enforcedoze-shizuku-starter
adb shell pm grant com.akylas.enforcedoze moe.shizuku.manager.permission.API_V23
adb shell /data/local/tmp/enforcedoze-shizuku-starter

# The earlier UI suite keeps the fresh-install permission state. Only privileged
# notification assertions require the real posting permission, granted outside the
# instrumented process and restored by the EXIT trap even when a test fails.
notification_permission_original="$(read_notification_permission)"
case "$notification_permission_original" in
  true|false) ;;
  *) echo "Unable to determine the test app notification permission" >&2; exit 1 ;;
esac
adb shell pm grant "$test_package" "$notification_permission"
[[ "$(read_notification_permission)" == true ]]

./gradlew "${gradle_args[@]}" \
  "-Pandroid.testInstrumentationRunnerArguments.class=$integration_classes" \
  -Pandroid.testInstrumentationRunnerArguments.shizukuIntegration=true \
  -Pandroid.testInstrumentationRunnerArguments.shizukuStarter=/data/local/tmp/enforcedoze-shizuku-starter \
  connectedDebugAndroidTest
cp -R app/build/reports/androidTests device-test-results/shizuku

# A successful test run must also leave usable screenshots.
collect_evidence
restore_notification_permission
trap - EXIT
