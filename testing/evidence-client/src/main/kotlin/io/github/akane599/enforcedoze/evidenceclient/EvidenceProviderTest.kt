// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.evidenceclient

import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The evidence provider as Coulomb sees it (ADR 0035), from another app: signed with the same key, asking for the
 * signature permission, and accepted by the debug build's caller check. The shell, which lacks the permission, is
 * checked by the emulator script with `content query`. The column lists are written out: this is the contract.
 */
@RunWith(AndroidJUnit4::class)
class EvidenceProviderTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val resolver = context.contentResolver
    private val status = Uri.parse("content://io.github.akane599.enforcedoze.evidence/status")
    private val sessions = Uri.parse("content://io.github.akane599.enforcedoze.evidence/sessions")

    @Test
    fun theSignaturePermissionIsGranted() {
        val granted = context.checkSelfPermission("io.github.akane599.enforcedoze.permission.READ_EVIDENCE")
        assertThat(granted).isEqualTo(PackageManager.PERMISSION_GRANTED)
    }

    @Test
    fun statusIsOneRowOfVersionOne() {
        resolver.query(status, null, null, null)!!.use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            assertThat(cursor.columnNames.toList()).containsExactly(
                "api_version",
                "state",
                "profile",
                "profile_name",
                "report_session_id",
                "start_ms",
                "end_ms",
                "verdict",
                "deep_share",
                "drain",
            ).inOrder()
            cursor.moveToFirst()
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("api_version"))).isEqualTo(1)
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("state"))).isIn(listOf("MANAGED", "PAUSED", "HANDS_OFF", "OFF"))
        }
    }

    @Test
    fun sessionsHonourTheLimitAndTheProjection() {
        val args = Bundle().apply { putInt(ContentResolver.QUERY_ARG_LIMIT, 1) }
        resolver.query(sessions, arrayOf("_id", "verdict"), args, null)!!.use { cursor ->
            assertThat(cursor.columnNames.toList()).containsExactly("_id", "verdict").inOrder()
            assertThat(cursor.count).isAtMost(1)
            assertThat(
                cursor.extras.getStringArray(ContentResolver.EXTRA_HONORED_ARGS)?.toList(),
            ).containsExactly(ContentResolver.QUERY_ARG_LIMIT)
        }
    }

    @Test
    fun selectionSortAndWritesAreRefused() {
        assertThrows(IllegalArgumentException::class.java) { resolver.query(sessions, null, "verdict = ?", arrayOf("HELD"), null) }
        assertThrows(IllegalArgumentException::class.java) { resolver.query(sessions, arrayOf("note"), null, null) }
        assertThrows(UnsupportedOperationException::class.java) { resolver.delete(sessions, null, null) }
    }
}
