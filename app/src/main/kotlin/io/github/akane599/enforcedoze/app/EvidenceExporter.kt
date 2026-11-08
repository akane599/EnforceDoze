// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.akylas.enforcedoze.BuildConfig
import io.github.akane599.enforcedoze.data.EvidenceExport
import io.github.akane599.enforcedoze.data.ExportContent
import io.github.akane599.enforcedoze.data.ExportManifest
import io.github.akane599.enforcedoze.data.NightReports
import io.github.akane599.enforcedoze.data.SessionRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * The evidence export (SPEC 8.7): one ZIP per session or per range, written to the cache and handed to
 * the system share sheet. Only the latest export is kept; nothing is uploaded.
 */
internal class EvidenceExporter(private val graph: EngineGraph) {
    /** One session's ZIP, with the logs from the engine's screen-off writes on; null when there is no such session. */
    suspend fun session(id: Long): File? {
        val row = graph.sessions.session(id) ?: return null
        return export(listOf(row), NightReports.from(row), row.end, "session-$id")
    }

    /** Every session that overlaps [fromMillis]..[toMillis], with the logs of that range. */
    suspend fun range(fromMillis: Long, toMillis: Long): File =
        export(graph.sessions.sessionsBetween(fromMillis, toMillis), fromMillis, toMillis, "$fromMillis-$toMillis")

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(graph.app, graph.app.packageName + AUTHORITY_SUFFIX, file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(ZIP)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null)
    }

    private suspend fun export(rows: List<SessionRow>, fromMillis: Long, toMillis: Long, name: String): File {
        val sessions = graph.sessions
        val records = rows.mapNotNull { sessions.recordOf(it.id) }
        val nights = sessions.nightsOf(rows.map { it.id })
        val actions = graph.journal.actionsSince(fromMillis).filter { it.time <= toMillis }
        val events = graph.journal.eventsSince(fromMillis).filter { it.time <= toMillis }
        val starts = rows.map { it.start.toString() }.toSet()
        val raw = graph.recorderFiles.rawFolders().filter { it.name in starts }
        val ids = rows.mapNotNull { it.profileId }.distinct()
        val profiles = ids.mapNotNull(Profiles::byId)
        val manifest = ExportManifest(
            createdAt = Instant.now().toString(),
            app = BuildConfig.VERSION_NAME,
            device = "${Build.MANUFACTURER} ${Build.MODEL} ${Build.DISPLAY}",
            sdk = Build.VERSION.SDK_INT,
            from = Instant.ofEpochMilli(fromMillis).toString(),
            to = Instant.ofEpochMilli(toMillis).toString(),
            missingProfiles = ids - profiles.map { it.id }.toSet(),
        )
        val content = ExportContent(manifest, records, nights, actions, events, raw, profiles)
        return withContext(Dispatchers.IO) {
            val dir = File(graph.app.cacheDir, EXPORTS).apply {
                deleteRecursively()
                mkdirs()
            }
            File(dir, "enforcedoze-$name.zip").also { file -> file.outputStream().buffered().use { EvidenceExport.write(it, content) } }
        }
    }

    private companion object {
        /** Matches `res/xml/export_paths.xml` and the provider in the manifest. */
        const val EXPORTS = "exports"
        const val AUTHORITY_SUFFIX = ".exports"
        const val ZIP = "application/zip"
    }
}
