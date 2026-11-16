// SPDX-License-Identifier: GPL-3.0-or-later
// Macrobenchmarks of the app as shipped (SPEC 10.5 optimize): cold start to first frame under 500 ms on
// the API 37 emulator. Run by tools/emulator/p5-ui.sh; never shipped.

plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "io.github.akane599.enforcedoze.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The emulator is where SPEC 10.5 asks for the number; macrobenchmark refuses one unless told.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
