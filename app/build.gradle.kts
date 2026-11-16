// SPDX-License-Identifier: GPL-3.0-or-later
// Converted from upstream's app/build.gradle (EnforceDoze 1.10.2) for AGP 9 and the SDK levels of
// SPEC 3.1 (ADR 0007).

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.licensee)
}

android {
    // The fork's own package for R and BuildConfig since upstream's classes went (ADR 0037); the
    // application id is the fork's own so it installs beside upstream (SPEC 3.1).
    namespace = "io.github.akane599.enforcedoze"
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
        compose = true
    }

    // Robolectric tests read the app's strings (the notifications' channels and texts).
    testOptions { unitTests.isIncludeAndroidResources = true }

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
        // The release build as shipped, debug-signed and profileable, for the macrobenchmark (SPEC 10.5 optimize).
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // The benchmark build is the release build: it takes release's no-op debug hooks.
    sourceSets.getByName("benchmark").kotlin.directories += "src/release/kotlin"

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
    implementation(project(":core:privilege"))
    implementation(project(":core:doze-android"))
    implementation(project(":core:data"))
    implementation(project(":core:evidence"))
    implementation(project(":core:profile"))
    implementation(project(":core:recommend"))
    implementation(project(":core:design"))
    implementation(project(":core:ui"))
    listOf("home", "report", "profiles", "plan", "log", "settings", "allowlist", "sensors", "onboarding").forEach {
        implementation(project(":feature:$it"))
    }
    // The Compose shell and Navigation 3 with list-detail scenes (ADR 0029).
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.adaptive.navigation3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

tasks.withType<Test>().configureEach {
    // Robolectric's FileDescriptor interceptor (SDK 36) reaches jdk.internal.access on JDK 17+ (ADR 0028).
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
}

// Dependency licence check (SPEC 12.5): only licences compatible with GPL-3.0 may ship in the APK.
// Runs as part of `check` and on its own as `licenseeDebug`.
licensee {
    allow("Apache-2.0")
    allow("MIT")
    allow("BSD-2-Clause")
    allow("BSD-3-Clause")
    allow("GPL-3.0-or-later")
    allow("GPL-3.0-only")
    allow("LGPL-2.1-or-later")
    allow("LGPL-3.0-or-later")
    allow("MPL-2.0")
    // Shizuku-API names only its LICENSE file, which is MIT (checked 2026-11-08 at
    // github.com/RikkaApps/Shizuku-API/blob/master/LICENSE).
    allowUrl("https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE") {
        because("MIT, per the linked LICENSE file")
    }
    // libsu's JitPack POMs declare no licence; the project is Apache-2.0 (github.com/topjohnwu/libsu).
    listOf("core", "service").forEach { module ->
        allowDependency("com.github.topjohnwu.libsu", module, libs.versions.libsu.get()) {
            because("Apache-2.0, per the repository LICENSE")
        }
    }
}
