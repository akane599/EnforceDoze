#!/usr/bin/env bash
set -euo pipefail

# Tests use a fresh emulator. Keep APK data until evidence is collected.
gradle_args=(--no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true)
integration_class=com.akylas.enforcedoze.ShizukuIntegrationTest
integration_classes="$integration_class,com.akylas.enforcedoze.SensorShizukuIntegrationTest"
mkdir -p device-test-results
collect_evidence() {
  adb pull /sdcard/Android/data/com.akylas.enforcedoze/files/screenshots device-screenshots
}
trap 'collect_evidence || true' EXIT

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

./gradlew "${gradle_args[@]}" \
  "-Pandroid.testInstrumentationRunnerArguments.class=$integration_classes" \
  -Pandroid.testInstrumentationRunnerArguments.shizukuIntegration=true \
  -Pandroid.testInstrumentationRunnerArguments.shizukuStarter=/data/local/tmp/enforcedoze-shizuku-starter \
  connectedDebugAndroidTest
cp -R app/build/reports/androidTests device-test-results/shizuku

# A successful test run must also leave usable screenshots.
collect_evidence
trap - EXIT
