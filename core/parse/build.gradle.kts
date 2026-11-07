// SPDX-License-Identifier: GPL-3.0-or-later
// Pure text parsers for system dumps (ADR 0009): text in, values out, no Android dependency.

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
    testImplementation(project(":testing:fixtures"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

tasks.test {
    val fixtures = rootProject.layout.projectDirectory.dir("fixtures")
    inputs.dir(fixtures).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("fixtures")
    systemProperty("enforcedoze.fixtures", fixtures.asFile.absolutePath)
}

// CI runs `testDebugUnitTest` across all modules; this pure-JVM module answers to the same name.
tasks.register("testDebugUnitTest") {
    group = "verification"
    dependsOn(tasks.test)
}
