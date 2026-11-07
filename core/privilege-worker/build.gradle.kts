// SPDX-License-Identifier: GPL-3.0-or-later
// The IDozeWorker implementation (SPEC 4.3). It runs in a separate process as shell or root and is the
// only module allowed to compile against hidden framework classes (ADR 0009).

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.akane599.enforcedoze.privilege.worker"
    compileSdk = 37
    defaultConfig {
        minSdk = 34
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(project(":core:privilege-api"))
    compileOnly(project(":core:hidden-api-stubs"))
    implementation(libs.libsu.service)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
