// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.Profile

/** Profiles the engine can be started with: the built-ins and, in debug builds only, the test profiles. Custom profiles join in P4. */
internal object Profiles {
    fun byId(id: String): Profile? = BuiltInProfiles.byId(id) ?: DebugHooks.profiles().firstOrNull { it.id == id }
}
