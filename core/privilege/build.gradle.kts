// SPDX-License-Identifier: GPL-3.0-or-later
// Privilege tiers, the Shizuku, Sui and root clients, the worker session and the capability probes
// (SPEC 4). The worker implementation is a run-time dependency only, so nothing in the app process can
// compile against it or the hidden-API stubs (ADR 0009).

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.akane599.enforcedoze.privilege"
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
    api(project(":core:privilege-api"))
    implementation(project(":core:parse"))
    runtimeOnly(project(":core:privilege-worker"))
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":testing:fixtures"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    val fixtures = rootProject.layout.projectDirectory.dir("fixtures")
    inputs.dir(fixtures).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("fixtures")
    systemProperty("enforcedoze.fixtures", fixtures.asFile.absolutePath)
}
