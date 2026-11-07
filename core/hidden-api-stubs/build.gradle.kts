// SPDX-License-Identifier: GPL-3.0-or-later
// Compile-time stubs of hidden framework classes. Only :core:privilege-worker may depend on this module,
// and only as compileOnly, so hidden calls cannot compile anywhere in the app process (ADR 0009). At run
// time the framework's own classes on the boot class path are used.

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.akane599.enforcedoze.hiddenapi"
    compileSdk = 37
    defaultConfig { minSdk = 34 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
