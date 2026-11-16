// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.evidence

import io.github.akane599.enforcedoze.data.SessionRow
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import io.github.akane599.enforcedoze.app.DebugHooks
import io.github.akane599.enforcedoze.app.EngineGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.runBlocking

/**
 * The read-only evidence provider for Coulomb (SPEC 11.3, ADR 0035). The manifest guards it with the signature
 * permission `READ_EVIDENCE`; on top of that only Coulomb (and, in debug builds, the app's own instrumentation) may
 * call it, since other apps signed with the same key would hold the permission too.
 */
class EvidenceProvider : ContentProvider() {
    // Nothing here: the provider is created on the main thread before Application.onCreate.
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<String>?, queryArgs: Bundle?, cancellationSignal: CancellationSignal?): Cursor {
        checkCaller()
        val refused = queryArgs?.keySet().orEmpty().filter { it in REFUSED_ARGS }
        require(refused.isEmpty()) { "not supported: ${refused.joinToString()}; the order is fixed, newest first" }
        val limit = queryArgs?.getInt(ContentResolver.QUERY_ARG_LIMIT, -1)?.takeIf { it >= 0 }
        val graph = EngineGraph.get(requireContext())
        // A binder thread: blocking reads of small tables, never the report loader (ADR 0035).
        val (columns, rows) = runBlocking {
            when (MATCHER.match(uri)) {
                STATUS -> EvidenceContract.STATUS_COLUMNS to listOf(status(graph))
                SESSIONS -> EvidenceContract.SESSION_COLUMNS to sessions(graph, limit)
                else -> throw IllegalArgumentException("unknown uri $uri")
            }
        }
        val picked = EvidenceContract.project(columns, projection)
        val cursor = MatrixCursor(picked.map { columns[it] }.toTypedArray(), rows.size)
        rows.forEach { row -> cursor.addRow(picked.map { row[it] }) }
        cursor.setNotificationUri(requireContext().contentResolver, uri)
        if (limit != null) {
            cursor.extras = Bundle().apply { putStringArray(ContentResolver.EXTRA_HONORED_ARGS, arrayOf(ContentResolver.QUERY_ARG_LIMIT)) }
        }
        return cursor
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        checkCaller()
        require(selection == null && sortOrder == null) { "selection and sort order are not supported" }
        return query(uri, projection, null as Bundle?, null)
    }

    override fun getType(uri: Uri): String? = when (MATCHER.match(uri)) {
        STATUS -> "vnd.android.cursor.item/vnd.${EvidenceContract.AUTHORITY}.status"
        SESSIONS -> "vnd.android.cursor.dir/vnd.${EvidenceContract.AUTHORITY}.session"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException(READ_ONLY)

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException(READ_ONLY)

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = throw UnsupportedOperationException(READ_ONLY)

    private fun checkCaller() {
        val caller = callingPackage
        if (caller == null || caller !in EvidenceContract.CLIENTS + DebugHooks.evidenceClients()) {
            throw SecurityException("Permission Denial: ${caller ?: "unknown"} may not read EnforceDoze's evidence (ADR 0035)")
        }
    }

    private suspend fun status(graph: EngineGraph): Array<Any?> {
        val saved = graph.store.load()
        val profileId = graph.hosts.value?.status?.value?.profileId ?: saved.profileId
        val state = if (graph.switch.enabled) (graph.hosts.value?.status?.value?.mode ?: saved.mode).name else EvidenceContract.OFF
        val name = profileId?.let { graph.profiles.profile(it)?.name }
        return EvidenceContract.statusRow(state, profileId, name, LastResults.latest(graph.sessions))
    }

    private suspend fun sessions(graph: EngineGraph, limit: Int?): List<Array<Any?>> {
        val now = System.currentTimeMillis()
        val rows = graph.sessions.sessionsBetween(0, now)
            .sortedWith(compareByDescending<SessionRow> { it.start }.thenByDescending { it.id })
        return (if (limit != null) rows.take(limit) else rows).map(EvidenceContract::sessionRow)
    }

    internal companion object {
        private const val STATUS = 1
        private const val SESSIONS = 2
        private const val READ_ONLY = "the evidence provider is read-only"

        private val REFUSED_ARGS = setOf(
            ContentResolver.QUERY_ARG_SQL_SELECTION,
            ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
            ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
            ContentResolver.QUERY_ARG_SORT_COLUMNS,
            ContentResolver.QUERY_ARG_OFFSET,
            ContentResolver.QUERY_ARG_SQL_LIMIT,
            ContentResolver.QUERY_ARG_SQL_GROUP_BY,
            ContentResolver.QUERY_ARG_SQL_HAVING,
        )

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(EvidenceContract.AUTHORITY, EvidenceContract.STATUS_PATH, STATUS)
            addURI(EvidenceContract.AUTHORITY, EvidenceContract.SESSIONS_PATH, SESSIONS)
        }

        private fun uri(path: String): Uri =
            Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(EvidenceContract.AUTHORITY).path(path).build()

        /** Tells observers (Coulomb, while its screen shows) that both paths changed; a spare notice costs one query. */
        suspend fun notifyChanges(context: Context, vararg changes: Flow<Any?>) {
            merge(*changes).collect { notifyNow(context) }
        }

        fun notifyNow(context: Context) {
            context.contentResolver.notifyChange(uri(EvidenceContract.STATUS_PATH), null)
            context.contentResolver.notifyChange(uri(EvidenceContract.SESSIONS_PATH), null)
        }
    }
}
