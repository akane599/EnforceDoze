// SPDX-License-Identifier: GPL-3.0-or-later
// Wording that more than one screen shows: verdicts, causes and the SPEC 9.3 notices (ADR 0029).

plugins {
    id("enforcedoze.android.feature")
}

android {
    namespace = "io.github.akane599.enforcedoze.ui"
}

dependencies {
    api(project(":core:design"))
    api(project(":core:evidence"))
    api(project(":core:doze"))
}
