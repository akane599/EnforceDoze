// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.feature.profiles.OtherManager

/**
 * Apps that write Doze settings themselves (SPEC 5.7, 11.5): two managers undo each other's settings. Each id is the
 * app's published application id. Coulomb is not here: it reads EnforceDoze's evidence and writes nothing while
 * EnforceDoze is installed (SPEC 11.3); if it ever writes, the drift log names it like any other writer.
 */
internal object OtherManagers {
    val PACKAGES: List<String> = listOf(
        // Upstream EnforceDoze keeps its own app id; this fork moved to io.github.akane599.enforcedoze (AGENTS.md).
        "com.akylas.enforcedoze",
        // ForceDoze, upstream's own origin.
        "com.suyashsrijan.forcedoze",
        "com.paget96.batteryguru",
    )

    fun installed(labels: Labels): List<OtherManager> = PACKAGES.mapNotNull { pkg -> labels.label(pkg)?.let { OtherManager(pkg, it) } }
}
