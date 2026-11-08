// SPDX-License-Identifier: GPL-3.0-or-later
// Session evidence (SPEC 8): verdicts computed from the records, never from the profile settings.
// Pure JVM; the collectors that feed it live in the app side.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    explicitApi()
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
}

dependencies {
    api(project(":core:parse"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

// CI runs `testDebugUnitTest` across all modules; this pure-JVM module answers to the same name.
tasks.register("testDebugUnitTest") {
    group = "verification"
    dependsOn(tasks.test)
}
