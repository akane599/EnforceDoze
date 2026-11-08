// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class EvidenceExportTest {
    @get:Rule val folder = TemporaryFolder()

    private val session = SessionRow(
        id = 3,
        start = 1_794_108_000_000,
        end = 1_794_108_300_000,
        profileId = "everyday",
        pluggedAtStart = false,
        verdict = "HELD",
        cause = null,
        expectedToDeepMillis = 60_000,
        timeToDeepMillis = 69_000,
        lateByMillis = null,
        deepShare = 1.0,
        maintenanceWindows = 2,
        motionResets = 0,
        breaks = "",
        note = "shorter than 600 s, \"compressed\"",
    )

    private fun entries(content: ExportContent): Map<String, ByteArray> {
        val bytes = ByteArrayOutputStream().also { EvidenceExport.write(it, content) }.toByteArray()
        return buildMap {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                generateSequence { zip.nextEntry }.forEach { put(it.name, zip.readBytes()) }
            }
        }
    }

    @Test fun `the zip has the manifest, sessions with their nights, the logs, the profiles and the raw dumps`() {
        val raw = folder.newFolder("raw", session.start.toString())
        raw.resolve("eventlog.gz").writeBytes(byteArrayOf(31, -117, 8))
        raw.resolve("alarm-off.gz").writeBytes(byteArrayOf(1))
        val night = NightRow(
            id = 1, sessionId = 3, unlockedAt = session.end + 1, recentNights = 0, recentDrainPerHour = null,
            recentDeepShare = null, stockNights = 0, stockDrainPerHour = null, stockDeepShare = null,
        )
        val content = ExportContent(
            manifest = ExportManifest("2026-11-08T22:00:00Z", "2.0.0-debug", "Google sdk_gphone64 x86_64", 37, "a", "b"),
            sessions = listOf(
                SessionRecord(session, emptyList(), listOf(WakerRow(uid = 1000, name = "android", alarmWakeups = 4)), emptyList()),
            ),
            nights = listOf(night),
            actions = listOf(
                ActionRow(
                    time = 5,
                    lever = "CONSTANTS",
                    command = "put",
                    expected = "a=1,b=2",
                    readBack = null,
                    outcome = "OK",
                    cause = "START",
                    detail = "line one\nline two",
                ),
            ),
            events = listOf(EventRow(time = 6, kind = JournalRows.BATTERY, expected = "reset", found = "", reason = "OK")),
            rawFolders = listOf(raw),
            profiles = listOf(BuiltInProfiles.EVERYDAY),
        )

        val files = entries(content)

        assertThat(files.keys).containsExactly(
            "manifest.json",
            "sessions.json",
            "actions.csv",
            "events.csv",
            "profiles/everyday.json",
            "raw/${session.start}/alarm-off.gz",
            "raw/${session.start}/eventlog.gz",
        ).inOrder()
        val sessions = Json.parseToJsonElement(files.getValue("sessions.json").decodeToString()).jsonArray
        val first = sessions.single().jsonObject
        assertThat(first.getValue("session").jsonObject.getValue("verdict").jsonPrimitive.content).isEqualTo("HELD")
        assertThat(first.getValue("night").jsonObject.getValue("unlockedAt").jsonPrimitive.content).isEqualTo("${session.end + 1}")
        assertThat(first.getValue("wakers").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content).isEqualTo("android")
        assertThat(files.getValue("raw/${session.start}/eventlog.gz")).isEqualTo(byteArrayOf(31, -117, 8))
        val actions = files.getValue("actions.csv").decodeToString().split("\r\n")
        assertThat(actions[0]).isEqualTo("time,timeUtc,lever,command,expected,readBack,outcome,cause,detail")
        assertThat(actions[1]).isEqualTo("5,1970-01-01T00:00:00.005Z,CONSTANTS,put,\"a=1,b=2\",,OK,START,\"line one\nline two\"")
        assertThat(files.getValue("events.csv").decodeToString()).contains(",BATTERY,,,reset,,,,,,OK")
        assertThat(files.getValue("profiles/everyday.json").decodeToString()).isEqualTo(ProfileFiles.encode(BuiltInProfiles.EVERYDAY))
    }

    @Test fun `quotes in a field are doubled`() {
        assertThat(EvidenceExport.csv(listOf("a"), listOf(listOf("say \"hi\"")))).isEqualTo("a\r\n\"say \"\"hi\"\"\"\r\n")
    }
}
