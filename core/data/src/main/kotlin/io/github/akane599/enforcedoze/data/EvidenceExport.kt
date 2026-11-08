// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import io.github.akane599.enforcedoze.profile.Profile
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** What one export is and where it came from (SPEC 8.7); the first file in the ZIP. */
@Serializable
public data class ExportManifest(
    val createdAt: String,
    /** The app's version name. */
    val app: String,
    /** Manufacturer, model and build, as `Build` reports them. */
    val device: String,
    val sdk: Int,
    val from: String,
    val to: String,
    /** Profile ids the sessions used that are no longer known, so their files are missing. */
    val missingProfiles: List<String> = emptyList(),
)

/** One session in `sessions.json`: the record, and its night report row when it was a night. */
@Serializable
public data class SessionExport(
    val session: SessionRow,
    val transitions: List<TransitionRow>,
    val wakers: List<WakerRow>,
    val snapshots: List<SensorSnapshotRow>,
    val night: NightRow? = null,
)

public data class ExportContent(
    val manifest: ExportManifest,
    val sessions: List<SessionRecord>,
    val nights: List<NightRow>,
    val actions: List<ActionRow>,
    val events: List<EventRow>,
    /** `raw/<session start>` folders, copied as they are stored (gzip). */
    val rawFolders: List<File>,
    val profiles: List<Profile>,
)

/**
 * The evidence export (SPEC 8.7): one ZIP per session or per range with the session JSON, the action
 * log and the engine's events as CSV, the raw dumps and the profile files in use. Nothing leaves the
 * phone but through the share sheet.
 */
public object EvidenceExport {
    public const val MANIFEST: String = "manifest.json"
    public const val SESSIONS: String = "sessions.json"
    public const val ACTIONS: String = "actions.csv"
    public const val EVENTS: String = "events.csv"

    /** Characters that make a CSV field need quotes (RFC 4180). */
    private const val QUOTED = ",\"\r\n"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    public fun write(out: OutputStream, content: ExportContent) {
        val nights = content.nights.associateBy { it.sessionId }
        val sessions = content.sessions.map {
            SessionExport(it.session, it.transitions, it.wakers, it.snapshots, nights[it.session.id])
        }
        ZipOutputStream(out).use { zip ->
            zip.text(MANIFEST, json.encodeToString(ExportManifest.serializer(), content.manifest))
            zip.text(SESSIONS, json.encodeToString(ListSerializer(SessionExport.serializer()), sessions))
            zip.text(ACTIONS, actionsCsv(content.actions))
            zip.text(EVENTS, eventsCsv(content.events))
            content.profiles.forEach { zip.text("profiles/${ProfileFiles.fileName(it)}", ProfileFiles.encode(it)) }
            content.rawFolders.sortedBy { it.name }.forEach { zip.folder(it) }
        }
    }

    public fun actionsCsv(actions: List<ActionRow>): String = csv(
        listOf("time", "timeUtc", "lever", "command", "expected", "readBack", "outcome", "cause", "detail"),
        actions.map {
            listOf(it.time.toString(), utc(it.time), it.lever, it.command, it.expected, it.readBack, it.outcome, it.cause, it.detail)
        },
    )

    public fun eventsCsv(events: List<EventRow>): String = csv(
        listOf(
            "time", "timeUtc", "kind", "lever", "holdOff", "expected", "found", "writer", "whileStopped", "mode", "profileId", "reason",
        ),
        events.map {
            listOf(
                it.time.toString(), utc(it.time), it.kind, it.lever, it.holdOff, it.expected, it.found, it.writer,
                it.whileStopped?.toString(), it.mode, it.profileId, it.reason,
            )
        },
    )

    /** RFC 4180: a field with a comma, quote or line break is quoted, quotes doubled; null is an empty field. */
    public fun csv(header: List<String>, rows: List<List<String?>>): String = buildString {
        (listOf(header) + rows).forEach { row -> append(row.joinToString(",") { field(it.orEmpty()) }).append("\r\n") }
    }

    private fun field(value: String): String = if (value.any { it in QUOTED }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    private fun utc(millis: Long): String = Instant.ofEpochMilli(millis).toString()

    /** A raw dump folder, its files copied as stored. */
    private fun ZipOutputStream.folder(folder: File) {
        folder.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.forEach { file ->
            putNextEntry(ZipEntry("raw/${folder.name}/${file.name}"))
            file.inputStream().use { it.copyTo(this) }
            closeEntry()
        }
    }

    private fun ZipOutputStream.text(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }
}
