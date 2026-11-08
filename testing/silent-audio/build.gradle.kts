// SPDX-License-Identifier: GPL-3.0-or-later
// A test-only APK that plays a silent media stream on request, for the audio hold-off test on the
// emulator (SPEC 13.3). It is installed by tools/emulator/p2-engine.sh and never shipped.

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.akane599.enforcedoze.silentaudio"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.akane599.enforcedoze.silentaudio"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}
