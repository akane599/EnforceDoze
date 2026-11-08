// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    `kotlin-dsl`
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.roborazzi.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("feature") {
            id = "enforcedoze.android.feature"
            implementationClass = "FeatureConventionPlugin"
        }
    }
}
