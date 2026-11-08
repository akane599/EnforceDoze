// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecorderFilesTest {
    @get:Rule val folder = TemporaryFolder()

    private val dir: File get() = File(folder.root, "sessions")

    private val open = OpenSession(
        start = 1_000,
        profileId = "everyday",
        holdOffs = listOf("AUDIO"),
        constantsApplied = true,
        batteryLevel = 80,
        latchedInactiveMillis = 30_000,
        snapshots = listOf(PendingSnapshot(1_001, SessionAssembly.SCREEN_OFF, "NORMAL", false, "")),
    )

    @Test fun theOpenSessionSurvivesAndEnds() = runTest {
        val files = RecorderFiles(dir)
        assertThat(files.open()).isNull()
        files.saveOpen(open)
        assertThat(RecorderFiles(dir).open()).isEqualTo(open)
        files.addCopied(3_000, "b")
        files.addCopied(2_000, "a")
        assertThat(files.copied()).containsExactly(2_000L to "a", 3_000L to "b").inOrder()
        files.clearOpen()
        assertThat(files.open()).isNull()
        assertThat(files.copied()).isEmpty()
    }

    @Test fun anUnreadableOpenSessionIsMovedAside() = runTest {
        dir.mkdirs()
        File(dir, "open.json").writeText("{ not json")
        assertThat(RecorderFiles(dir).open()).isNull()
        assertThat(dir.list()!!.single()).startsWith("open.json.unreadable-")
    }

    @Test fun theCursorIsKept() = runTest {
        val files = RecorderFiles(dir)
        assertThat(files.cursor()).isNull()
        files.saveCursor(987_654)
        assertThat(RecorderFiles(dir).cursor()).isEqualTo(987_654)
    }

    @Test fun rawDumpsAreCompressedAndPruned() = runTest {
        val files = RecorderFiles(dir)
        val big = "line\n".repeat(10_000)
        files.saveRaw(1_000, "history", big)
        files.saveRaw(5_000, "alarm-off", "x")
        files.saveRaw(9_000, "alarm-off", "y")
        assertThat(files.readRaw(1_000, "history")).isEqualTo(big)
        assertThat(File(dir, "raw/1000/history.gz").length()).isLessThan(big.length.toLong() / 10)
        assertThat(files.readRaw(1_000, "missing")).isNull()
        files.deleteRaw(9_000)
        assertThat(files.pruneRaw(beforeMillis = 5_000)).isEqualTo(1)
        assertThat(files.rawFolders().map { it.name }).containsExactly("5000")
    }
}
