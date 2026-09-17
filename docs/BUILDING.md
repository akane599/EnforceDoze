# Build, download and signing

## From a phone, after approved GitHub publication

The prepared workflow is `.github/workflows/build-apk.yml`, **Build and test APK**. It must first reach the repository's default branch through an approved merge to appear reliably in GitHub's Run workflow interface. Preparing this file locally does not publish it or run Actions.

1. Open the repository in your phone browser, sign into GitHub, then select **Actions → Build and test APK → Run workflow**. Use the browser's desktop-site mode if the control is hidden.
2. Select the intended branch. Keep Android 16 device tests enabled for the full check. Choose **Temporary test key**, or **Configured signing key** only after the repository owner has configured the four dedicated secrets below.
3. Open the run and wait for completion. Download the `enforcedoze-RUN_NUMBER` artifact ZIP from **Artifacts**, then extract the desired APK on your phone. Check the run result, BUILD-INFO, certificates and test reports before installation. A failed run can still contain diagnostic artifacts and must not be treated as a validated build.
4. Android may require permission for the browser/file manager to install APKs. Samsung installation/debugging restrictions may also apply.

The workflow has `contents: read`, no push/PR triggers, no publishing step and no repository-write credentials. It runs JVM tests/lint, builds both APK variants and, by default, boots an API 36 emulator for UI and actual shell-Shizuku tests. Turning off device tests is recorded in BUILD-INFO; it is not a passing device-test result. Artifacts expire after 14 days. Running it consumes the repository's Actions allowance and uploads APKs/reports to that run.

The old Release Upload workflow is retired because it exported every secret and mixed building with commits, pushes and publication. Legacy Fastlane lanes remain for maintainers; publication now requires explicit `publish:true`; GitHub release publication additionally requires `create_tag:true` because GitHub may create its tag. They do not implicitly push branch commits. They are not used by the new build workflow.

## Can an APK update an existing installation?

Android normally requires the **same application ID and compatible signing certificate**, with a permitted version code. This branch uses `com.akylas.enforcedoze`, version code 87. Different keys cannot update each other merely because both APKs are named EnforceDoze.

The provided original 1.10.2/86 APK is signed by CN=MG with certificate SHA-256:

```text
50c3301e181970be7d50b4cd51e44060db6876ebf6adb02e48336db4048e5757
```

Its private key was not supplied. A locally generated key or temporary CI key **cannot update that installation**. Stop and restore the original app before uninstalling; uninstalling also removes local preferences/history/recovery records. Publisher-signed store variants can have different certificates too.

- **Debug APK:** debuggable and signed with that environment's debug key. Keys can differ between machines/runs. Intended for development.
- **Release APK:** optimized, non-debuggable. Unsigned by default until the signing step. A temporary CI release key is unique to the run and is deliberately deleted; another run cannot update it.
- **Configured signing key:** consistent APK signing across runs, provided the same keystore/alias is retained. It updates the original APK only if the certificate actually matches the original. No workflow can reconstruct the publisher's private key.

For a stable CI identity, an owner may deliberately configure only `APK_KEYSTORE_BASE64`, `APK_KEYSTORE_PASSWORD`, `APK_KEY_ALIAS`, and `APK_KEY_PASSWORD`. Never commit the keystore, passwords or a base64 encoding of the keystore. Changing GitHub secrets/settings requires separate approval during this task.

## Local build

Use JDK 21, Android SDK platform 36 and build tools 36.0.0. Set ANDROID_HOME or use an ignored local.properties. The wrapper pins Gradle 8.13 and its distribution checksum.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest --max-workers=2
```

Outputs are under `app/build/outputs/apk/`. Debug APK is installable. To sign the unsigned release APK with an existing private key outside the repository, set `RELEASE_KEYSTORE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEYSTORE_ALIAS`, and `RELEASE_KEY_PASSWORD` in the local environment, then:

```sh
tools/sign-apk.sh app/build/outputs/apk/release/app-release-unsigned.apk /path/to/enforcedoze-release.apk
```

The script uses password environment variables, verifies the resulting APK and writes its public certificate report alongside it. Do not paste secrets into shell history. Alternatively, Gradle `-PuseExternalSigning` uses the same environment variables during assembly. Release signing never implicitly uses the debug key.

See [TESTING.md](TESTING.md) for actual device-test prerequisites, and the final delivery report for this run's APK paths, hashes, certificate and verification results.
