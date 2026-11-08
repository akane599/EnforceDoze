// SPDX-License-Identifier: GPL-3.0-or-later
// The engine's Android side (ADR 0015): the device port over the privilege controller and the constants
// writer, the hold-off sources, and the host that turns broadcasts into engine commands.

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.akane599.enforcedoze.doze.android"
    compileSdk = 37
    defaultConfig {
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    api(project(":core:doze"))
    api(project(":core:privilege"))
    implementation(project(":core:parse"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":testing:fixtures"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    val fixtures = rootProject.layout.projectDirectory.dir("fixtures")
    inputs.dir(fixtures).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("fixtures")
    systemProperty("enforcedoze.fixtures", fixtures.asFile.absolutePath)
}
