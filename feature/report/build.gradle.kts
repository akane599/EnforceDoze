// SPDX-License-Identifier: GPL-3.0-or-later
// The session and night report: summary, timetable, wakers, sensors, actions and raw records (SPEC 8.4, 8.5, 9.1).

plugins {
    id("enforcedoze.android.feature")
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:evidence"))
    implementation(project(":core:doze"))
}
