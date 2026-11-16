// SPDX-License-Identifier: GPL-3.0-or-later
// A stand-in for Coulomb (ADR 0035): its own package and uid, signed with the same debug key, asking for the
// evidence permission. It instruments itself, so it reads the provider across apps the way Coulomb does.
// Run by tools/emulator/p6-integrations.sh; never shipped.

plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "io.github.akane599.enforcedoze.evidenceclient"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "debug" }
}

dependencies {
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.truth)
}
