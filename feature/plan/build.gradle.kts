// SPDX-License-Identifier: GPL-3.0-or-later
// Plan and Recommended: the day and night slots with the night window, and the recommendation with its why (SPEC 7, 9.1).

plugins {
    id("enforcedoze.android.feature")
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:profile"))
    implementation(project(":core:recommend"))
    implementation(libs.activity.compose)
}
