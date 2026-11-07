// SPDX-License-Identifier: GPL-3.0-or-later
// The worker's binder contract (SPEC 4.3): the IDozeWorker AIDL, its parcelables and the names both
// sides share (ADR 0009).

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.akane599.enforcedoze.privilege.api"
    compileSdk = 37
    defaultConfig {
        minSdk = 34
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures { aidl = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    explicitApi()
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
