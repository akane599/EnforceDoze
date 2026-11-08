// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import io.github.akane599.enforcedoze.data.ProfileStore
import io.github.akane599.enforcedoze.doze.ProfileCatalog
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.Profile

/**
 * Every profile the app knows: the built-ins, the debug build's test profiles, then the owner's custom
 * profiles (SPEC 6.3). The engine looks the plan's profiles up here at switch time (ADR 0024).
 */
internal class Profiles(val store: ProfileStore) : ProfileCatalog {
    override suspend fun profile(id: String): Profile? =
        BuiltInProfiles.byId(id) ?: DebugHooks.profiles().firstOrNull { it.id == id } ?: store.byId(id)
}
