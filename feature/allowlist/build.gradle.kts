// SPDX-License-Identifier: GPL-3.0-or-later
// Allowlist: the Doze allowlist read from the phone, the owner's entries with their "keep" marks, suggestions (SPEC 9.1).

plugins {
    id("enforcedoze.android.feature")
}

dependencies {
    implementation(project(":core:ui"))
    implementation(libs.activity.compose)
}
