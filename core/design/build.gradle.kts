// SPDX-License-Identifier: GPL-3.0-or-later
// Tokens generated from DESIGN.md and the shared components, the timeline strip among them (SPEC 10.2, 12.3).
// Screenshot tests run on the JVM with Roborazzi and Robolectric's native graphics (SPEC 10.6, ADR 0028).

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.akane599.enforcedoze.design"
    compileSdk = 37
    defaultConfig {
        minSdk = 34
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.material.icons.extended)
    testImplementation(libs.androidx.test.core)
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    // Robolectric's FileDescriptor interceptor (SDK 36) reaches jdk.internal.access.SharedSecrets on JDK 17+.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
    systemProperty("robolectric.graphicsMode", "NATIVE")
    systemProperty("enforcedoze.root", rootProject.layout.projectDirectory.asFile.absolutePath)
}
