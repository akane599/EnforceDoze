// SPDX-License-Identifier: GPL-3.0-or-later
// Onboarding: what the app does, privilege, the capability check, the urgency question, the plan, quick proof (SPEC 9.1).

plugins {
    id("enforcedoze.android.feature")
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:profile"))
    implementation(project(":core:recommend"))
    implementation(libs.activity.compose)
}
