// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import android.content.Intent
import android.net.Uri
import io.github.akane599.enforcedoze.app.EngineGraph
import io.github.akane599.enforcedoze.app.EvidenceExporter
import io.github.akane599.enforcedoze.data.ProfileFiles
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

/** Evidence and profile exports (SPEC 8.7, 6.3): to the share sheet or a file the owner picked; nothing is uploaded. */
internal class Exports(private val graph: EngineGraph) {
    private val evidence = EvidenceExporter(graph)

    /** The share sheet with a report's ZIP: its night's when the session is part of one (ADR 0031). */
    suspend fun shareReport(sessionId: Long): Intent? {
        val session = graph.sessions.session(sessionId) ?: return null
        val night = graph.sessions.nightAround(session.start, session.end)
        val file = night?.let { evidence.night(it.id) } ?: evidence.session(sessionId) ?: return null
        return evidence.shareIntent(file)
    }

    /** Whole days [from] to [to], both included, in the phone's zone, written to [uri]. */
    suspend fun writeDays(uri: Uri, from: LocalDate, to: LocalDate): ActionOutcome {
        val zone = ZoneId.systemDefault()
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return copy(evidence.range(start, end), uri)
    }

    /** Everything kept (Settings, "Export everything"). */
    suspend fun writeAll(uri: Uri): ActionOutcome = copy(evidence.range(0, System.currentTimeMillis()), uri)

    suspend fun writeProfile(uri: Uri, profileId: String): ActionOutcome {
        val profile = graph.profiles.profile(profileId) ?: return ActionOutcome.Unavailable("profile $profileId is gone")
        return write(uri) { it.write(ProfileFiles.encode(profile).toByteArray()) }
    }

    fun profileFileName(profileId: String): String = ProfileFiles.fileName(profileId)

    private suspend fun copy(file: File, uri: Uri): ActionOutcome = write(uri) { out -> file.inputStream().use { it.copyTo(out) } }

    private suspend fun write(uri: Uri, block: (java.io.OutputStream) -> Unit): ActionOutcome = withContext(Dispatchers.IO) {
        try {
            val out = graph.app.contentResolver.openOutputStream(uri) ?: return@withContext ActionOutcome.Failed("no stream for $uri")
            out.use(block)
            ActionOutcome.Done
        } catch (e: IOException) {
            ActionOutcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
