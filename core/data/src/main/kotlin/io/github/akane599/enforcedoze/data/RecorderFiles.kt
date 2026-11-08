// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** A sensor snapshot taken before the session's row exists: at screen off and at each copy (ADR 0020). */
@Serializable
public data class PendingSnapshot(val time: Long, val moment: String, val mode: String?, val privacy: Boolean?, val connections: String) {
    public fun toRow(): SensorSnapshotRow =
        SensorSnapshotRow(time = time, moment = moment, mode = mode, privacy = privacy, connections = connections)

    public companion object {
        public fun of(row: SensorSnapshotRow): PendingSnapshot =
            PendingSnapshot(row.time, row.moment, row.mode, row.privacy, row.connections)
    }
}

/** The session the screen opened when it went off (ADR 0018); it outlives the process. */
@Serializable
public data class OpenSession(
    /** Screen off, epoch milliseconds. */
    val start: Long,
    val profileId: String?,
    val holdOffs: List<String>,
    val constantsApplied: Boolean,
    val batteryLevel: Int? = null,
    val chargeUah: Long? = null,
    /** `mInactiveTimeout` from `dumpsys deviceidle` at screen off: the walk's first timer as latched (ADR 0016). */
    val latchedInactiveMillis: Long? = null,
    /** The alarm dump at screen off was stored with the raw dumps (ADR 0019). */
    val alarmsRead: Boolean = false,
    val snapshots: List<PendingSnapshot> = emptyList(),
    /** Wall time of the last copy at a maintenance window (ADR 0020). */
    val copiedUntil: Long? = null,
    /** Set when a restarted process took the session over with the screen still off (ADR 0020). */
    val resumedAt: Long? = null,
)

/**
 * The session recorder's files in [dir] (ADR 0018):
 * - `open.json`, the open session, written atomically like the engine's files;
 * - `copied/<read time>.txt`, the event-log lines copied at maintenance windows (ADR 0020);
 * - `cursor`, the battery history's `NEXT:`;
 * - `raw/<session start>/<name>.gz`, the raw dumps, kept 7 days for bug reports (SPEC 8.3).
 */
public class RecorderFiles(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }

    public suspend fun open(): OpenSession? = io {
        val file = File(dir, OPEN)
        if (!file.isFile) return@io null
        try {
            json.decodeFromString(OpenSession.serializer(), file.readText())
        } catch (e: SerializationException) {
            aside(file, e)
        } catch (e: IllegalArgumentException) {
            aside(file, e)
        }
    }

    public suspend fun saveOpen(session: OpenSession): Unit = io {
        atomic(File(dir, OPEN), json.encodeToString(OpenSession.serializer(), session))
    }

    /** Ends the open session: its file and its copies go; its raw dumps stay. */
    public suspend fun clearOpen(): Unit = io {
        File(dir, OPEN).delete()
        File(dir, COPIED).deleteRecursively()
    }

    public suspend fun addCopied(readAt: Long, text: String): Unit = io { atomic(File(File(dir, COPIED), "$readAt.txt"), text) }

    /** The copies, oldest first, as read time and text. */
    public suspend fun copied(): List<Pair<Long, String>> = io {
        File(dir, COPIED).listFiles().orEmpty()
            .mapNotNull { file -> file.name.removeSuffix(".txt").toLongOrNull()?.let { it to file.readText() } }
            .sortedBy { it.first }
    }

    public suspend fun cursor(): Long? = io { File(dir, CURSOR).takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() }

    public suspend fun saveCursor(next: Long): Unit = io { atomic(File(dir, CURSOR), next.toString()) }

    public suspend fun saveRaw(sessionStart: Long, name: String, text: String): Unit = io {
        val folder = File(File(dir, RAW), sessionStart.toString())
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("cannot create $folder")
        GZIPOutputStream(FileOutputStream(File(folder, "$name.gz"))).use { it.write(text.toByteArray()) }
    }

    public suspend fun readRaw(sessionStart: Long, name: String): String? = io {
        val file = File(File(File(dir, RAW), sessionStart.toString()), "$name.gz")
        if (!file.isFile) null else GZIPInputStream(file.inputStream()).use { it.readBytes().decodeToString() }
    }

    /** Deletes one session's raw dumps, such as those of a session too short to judge (ADR 0020). */
    public suspend fun deleteRaw(sessionStart: Long): Unit = io { File(File(dir, RAW), sessionStart.toString()).deleteRecursively() }

    /** The raw dump folders, by session start, for export (SPEC 8.7). */
    public suspend fun rawFolders(): List<File> = io { File(dir, RAW).listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name } }

    /** Deletes the raw dumps of sessions that started before [beforeMillis]; returns how many folders went. */
    public suspend fun pruneRaw(beforeMillis: Long): Int = io {
        File(dir, RAW).listFiles().orEmpty()
            .filter { folder -> folder.name.toLongOrNull()?.let { it < beforeMillis } == true }
            .count { it.deleteRecursively() }
    }

    private fun atomic(target: File, text: String) {
        val parent = target.parentFile ?: dir
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("cannot create $parent")
        val temporary = File(parent, "${target.name}.tmp")
        FileOutputStream(temporary).use { out ->
            out.write(text.toByteArray())
            out.fd.sync()
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun <T> aside(file: File, cause: Exception): T? {
        val moved = File(dir, "${file.name}.unreadable-${System.currentTimeMillis()}")
        if (!file.renameTo(moved)) throw IOException("${file.name} cannot be read and cannot be moved aside", cause)
        return null
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val OPEN = "open.json"
        const val COPIED = "copied"
        const val CURSOR = "cursor"
        const val RAW = "raw"
    }
}
