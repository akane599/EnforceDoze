// SPDX-License-Identifier: GPL-3.0-or-later
// The recommendation policy (SPEC 7, ADR 0026): screen-off sessions in, signals and a plan out. Pure JVM;
// the adapters that build sessions from the phone's history and the app's records live outside.

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
    api(project(":core:profile"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

// CI runs `testDebugUnitTest` across all modules; this pure-JVM module answers to the same name.
tasks.register("testDebugUnitTest") {
    group = "verification"
    dependsOn(tasks.test)
}
