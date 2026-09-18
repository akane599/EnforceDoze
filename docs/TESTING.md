# Verification guide

See AUDIT_REPORT.md and PROGRESS.md for **actual results**, not this list of intended checks. Only run device suites on a disposable emulator: they change simulated battery state, sensor access, permissions and the test installation's records.

## Local checks

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest --max-workers=2
ruby tools/check-fastlane.rb
```

The Ruby check exercises eight isolated build/publication guards with stubbed actions; it never builds or publishes. It does not establish that an actual Fastlane release succeeds.

JVM tests cover write-ahead persistence, access loss after mutation, readback failures, restore priority, maintenance ownership, malformed command inputs, output bounds/timeouts, schedules, battery-history policy and tunable syntax. Tests use simulated device state where indicated; these do not prove Android commands work.

Install debug and androidTest APKs onto an API 36 emulator, then run the general suite:

```sh
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r \
  -e class com.akylas.enforcedoze.ApplicationTest \
  com.akylas.enforcedoze.test/androidx.test.runner.AndroidJUnitRunner
```

The general suite checks real SharedPreferences recovery/history behavior, bounded evidence, rejected automation, screen launches, light/dark portrait/landscape navigation and large-font navigation. Screenshots are saved under the target app's external files `screenshots` directory. Inspect images, including loading/empty/error states, before calling visual review complete.

## Real Shizuku integration

Download the official [Shizuku v13.6.0 APK](https://github.com/RikkaApps/Shizuku/releases/tag/v13.6.0); expected SHA-256:

```text
6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f
```

```sh
tools/prepare-shizuku.sh emulator-5554 /path/to/shizuku.apk
adb -s emulator-5554 shell am instrument -w -r \
  -e class com.akylas.enforcedoze.ShizukuIntegrationTest \
  com.akylas.enforcedoze.test/androidx.test.runner.AndroidJUnitRunner
```

The script refuses non-emulator serials. It starts the official native starter as shell, not root. Tests assert UID 2000, exercise actual Binder calls, observe Deep Doze with screen off, compare foreground-notification post times across screen cycles, verify sensor events stop and resume, test developer sensor privacy separately, and kill/restart the Shizuku server to exercise retained undo. Missing access or Android compatibility failures are failures, not skipped assertions.

Collect `adb logcat -d`, instrumentation output, screenshots and Gradle XML/HTML reports. A failed test needs investigation and a documented rerun; do not count APK compilation as test execution.

Additional manual emulator checks: light/dark themes, compact portrait/landscape, font scale 2.0, delayed entry interrupted by screen-on, lock-screen and unlock transitions, charging, schedule boundaries, denied permissions, package picker search, invalid inputs, diagnostics copying/clearing and restoring after Stop.

Emulators cannot establish physical sensor power consumption, battery-saving percentages, Samsung One UI process management, real SIM/modem/hotspot interactions or biometric hardware behavior. Root/Magisk and older Android require separate environments; do not infer their runtime validation from Shizuku tests.
