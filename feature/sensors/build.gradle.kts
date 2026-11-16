// SPDX-License-Identifier: GPL-3.0-or-later
// Sensors: the sensor mode now, who held sensors at the last screen off, sensor privacy and all-sensors-off (SPEC 5.4, 9.1).

plugins {
    id("enforcedoze.android.feature")
}

dependencies {
    implementation(project(":core:ui"))
}
