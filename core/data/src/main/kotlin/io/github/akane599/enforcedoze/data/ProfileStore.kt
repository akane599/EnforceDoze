// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.GuardrailProblem
import io.github.akane599.enforcedoze.profile.Guardrails
import io.github.akane599.enforcedoze.profile.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The owner's custom profiles (SPEC 6.3), one SPEC 6.4 file each in [dir]. A file that no longer reads is
 * set aside, like the engine's files. The built-ins are not stored: they ship with the app.
 */
public class ProfileStore(private val dir: File) {
    /** Built-ins first, then the custom profiles by name. */
    public suspend fun all(): List<Profile> = BuiltInProfiles.ALL + custom()

    public suspend fun byId(id: String): Profile? = BuiltInProfiles.byId(id) ?: custom().firstOrNull { it.id == id }

    public suspend fun custom(): List<Profile> = withContext(Dispatchers.IO) {
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }.orEmpty()
        files.mapNotNull { file ->
            val read = ProfileFiles.read(file.readText())
            if (read.profile == null) setAside(file, IOException(read.problems.joinToString("; ")))
            read.profile
        }.sortedBy { it.name.lowercase() }
    }

    /**
     * Saves [profile] after the guardrails (SPEC 6.3), replacing a custom profile with the same id. [stock] is
     * `stock.json` in milliseconds. Returns the problems, empty when it was saved; a built-in id is refused.
     */
    public suspend fun save(profile: Profile, stock: Map<String, Long>?): List<GuardrailProblem> {
        if (BuiltInProfiles.byId(profile.id) != null || !Profile.ID.matches(profile.id)) {
            return listOf(GuardrailProblem.Invalid(null, "id ${profile.id} cannot be saved"))
        }
        val problems = Guardrails.check(profile.deviceIdle, stock)
        if (problems.isNotEmpty()) return problems
        withContext(Dispatchers.IO) {
            writeAtomically(dir, ProfileFiles.fileName(profile), ProfileFiles.encode(profile.copy(builtIn = false)))
        }
        return emptyList()
    }

    /** Deletes a custom profile. The caller keeps the plan from pointing at it (SPEC 6.3). */
    public suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        custom().any { it.id == id } && File(dir, ProfileFiles.fileName(id)).delete()
    }

    private companion object {
        const val SUFFIX = ".json"
    }
}
