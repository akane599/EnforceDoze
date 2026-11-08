// SPDX-License-Identifier: GPL-3.0-or-later
// The screenshot matrix every feature's tests render through (SPEC 10.6, ADR 0030).

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.akane599.enforcedoze.testing.screenshots"
    compileSdk = 37
    defaultConfig { minSdk = 34 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:design"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.roborazzi)
    implementation(libs.roborazzi.compose)
    implementation(libs.robolectric)
}
