// SPDX-License-Identifier: GPL-3.0-or-later
// Profile and plan types, the six built-ins and the custom-profile guardrails (SPEC 6, ADR 0015).
// Pure JVM, so the engine and the recommender share them without Android or the engine.

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
    implementation(project(":core:parse"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

// CI runs `testDebugUnitTest` across all modules; this pure-JVM module answers to the same name.
tasks.register("testDebugUnitTest") {
    group = "verification"
    dependsOn(tasks.test)
}
