// SPDX-License-Identifier: GPL-3.0-or-later
// Profiles: the list, a profile's detail with its levers and timings, and the custom-profile editor (SPEC 6, 9.1).

plugins {
    id("enforcedoze.android.feature")
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:profile"))
    implementation(project(":core:doze"))
    implementation(libs.activity.compose)
}
