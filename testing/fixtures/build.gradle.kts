// SPDX-License-Identifier: GPL-3.0-or-later
// Access to the captured dumps in /fixtures for JVM tests (SPEC 12.3 :testing:fixtures). The files stay
// in /fixtures byte for byte; test tasks pass the directory in the `enforcedoze.fixtures` property.

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
