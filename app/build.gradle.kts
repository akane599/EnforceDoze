// SPDX-License-Identifier: GPL-3.0-or-later
// Converted from upstream's app/build.gradle (EnforceDoze 1.10.2) for AGP 9 and the SDK levels of
// SPEC 3.1 (ADR 0007).

plugins {
    alias(libs.plugins.android.application)
}

android {
    // The upstream Java package (and R class) stays until its classes are replaced; the application
    // id is the fork's own so it installs beside upstream (SPEC 3.1).
    namespace = "com.akylas.enforcedoze"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.akane599.enforcedoze"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Release signing with Coulomb's key is wired in P7 (ADR 0006); until then every build is
        // debug-signed.
        signingConfig = signingConfigs.getByName("debug")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        release {
            vcsInfo { include = false }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        // Upstream 1.10.2 shipped with 2 errors and 157 warnings (docs/upstream-audit.md). The baseline
        // freezes them so CI fails only on new issues; it shrinks as upstream classes are replaced.
        baseline = file("lint-baseline.xml")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

configurations.configureEach {
    exclude(group = "com.android.support", module = "support-v13")
}

dependencies {
    implementation(libs.upstream.material.dialogs)
    implementation(libs.upstream.appcompat)
    implementation(libs.upstream.material)
    implementation(libs.upstream.browser)
    implementation(libs.upstream.media2.session)
    implementation(libs.upstream.preference.ktx)
    implementation(libs.upstream.libsuperuser)
    implementation(libs.upstream.nanotasks)
    implementation(libs.upstream.process.phoenix)
    implementation(libs.upstream.localbroadcastmanager)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit)
}
