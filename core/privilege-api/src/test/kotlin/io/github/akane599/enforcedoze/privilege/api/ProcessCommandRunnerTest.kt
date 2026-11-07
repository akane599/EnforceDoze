// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/** Runs real processes on the build host (Linux): echo, sh and sleep stand in for dumpsys and cmd. */
class ProcessCommandRunnerTest {
    private val runner = ProcessCommandRunner(outputCap = 1024)

    @Test fun capturesExitCodeAndBothStreams() {
        val out = runner.run(listOf("sh", "-c", "echo out; echo err >&2; exit 3"), 5_000)
        assertThat(out.exitCode).isEqualTo(3)
        assertThat(out.stdout).isEqualTo("out\n")
        assertThat(out.stderr).isEqualTo("err\n")
        assertThat(out.timedOut).isFalse()
        assertThat(out.error).isNull()
    }

    @Test fun argumentsAreNotInterpretedByAShell() {
        val out = runner.run(listOf("echo", "a;", "\$(id)", "|", "b"), 5_000)
        assertThat(out.stdout).isEqualTo("a; \$(id) | b\n")
    }

    @Test fun timeoutKillsTheProcess() {
        val out = runner.run(listOf("sleep", "10"), 200)
        assertThat(out.timedOut).isTrue()
        assertThat(out.isSuccess).isFalse()
        assertThat(out.durationMillis).isLessThan(5_000)
    }

    @Test fun outputIsCapped() {
        val out = runner.run(listOf("sh", "-c", "head -c 5000 /dev/zero | tr '\\0' x"), 5_000)
        assertThat(out.stdout).startsWith("x".repeat(1024))
        assertThat(out.stdout).endsWith("[truncated]")
    }

    @Test fun missingBinaryIsAnError() {
        val out = runner.run(listOf("/nonexistent/dumpsys"), 1_000)
        assertThat(out.error).isNotNull()
        assertThat(out.isSuccess).isFalse()
    }

    @Test fun streamCopiesEverything() {
        val sink = ByteArrayOutputStream()
        val result = runner.stream(listOf("sh", "-c", "head -c 300000 /dev/zero | tr '\\0' y"), 5_000, sink)
        assertThat(result.isSuccess).isTrue()
        assertThat(sink.size()).isEqualTo(300_000)
    }

    @Test fun streamTimesOut() {
        val result = runner.stream(listOf("sleep", "10"), 200, ByteArrayOutputStream())
        assertThat(result.timedOut).isTrue()
        assertThat(result.isSuccess).isFalse()
    }

    @Test fun streamReportsAClosedReader() {
        val closed = object : OutputStream() {
            override fun write(b: Int) = throw IOException("EPIPE")
        }
        val result = runner.stream(listOf("sh", "-c", "head -c 100000 /dev/zero"), 5_000, closed)
        assertThat(result.error).contains("pipe closed")
    }
}
