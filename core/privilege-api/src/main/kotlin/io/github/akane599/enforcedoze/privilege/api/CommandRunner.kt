// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.api

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Result of a streamed command; stdout went to the sink. */
public data class StreamResult(val exitCode: Int, val stderr: String, val timedOut: Boolean, val error: String?) {
    val isSuccess: Boolean get() = error == null && !timedOut && exitCode == 0

    public fun describe(argv: List<String>): String = when {
        error != null -> "${argv.joinToString(" ")}: $error"
        timedOut -> "${argv.joinToString(" ")}: timed out"
        else -> "${argv.joinToString(" ")}: exit $exitCode ${stderr.trim()}".trim()
    }
}

/**
 * Runs fixed argument lists, never a shell string. The worker uses it for privileged commands and the
 * app process for the few reads its durable grants allow (tier T1).
 */
public interface CommandRunner {
    public fun run(argv: List<String>, timeoutMillis: Long): CommandOutcome

    public fun stream(argv: List<String>, timeoutMillis: Long, sink: OutputStream): StreamResult
}

public class ProcessCommandRunner(private val outputCap: Int = OUTPUT_CAP) : CommandRunner {
    override fun run(argv: List<String>, timeoutMillis: Long): CommandOutcome {
        val started = System.nanoTime()
        val process = start(argv) ?: return CommandOutcome.rejected(argv, "could not start ${argv.first()}")
        val stdout = Drain(process.inputStream, outputCap)
        val stderr = Drain(process.errorStream, outputCap)
        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        stdout.join()
        stderr.join()
        return CommandOutcome(
            argv = argv,
            exitCode = if (finished) process.exitValue() else CommandOutcome.NOT_RUN,
            stdout = stdout.text(),
            stderr = stderr.text(),
            timedOut = !finished,
            durationMillis = (System.nanoTime() - started) / NANOS_PER_MILLI,
            error = null,
        )
    }

    override fun stream(argv: List<String>, timeoutMillis: Long, sink: OutputStream): StreamResult {
        val process = start(argv) ?: return StreamResult(CommandOutcome.NOT_RUN, "", false, "could not start ${argv.first()}")
        val stderr = Drain(process.errorStream, STDERR_CAP)
        val timedOut = AtomicBoolean(false)
        val watchdog = WATCHDOG.schedule({
            timedOut.set(true)
            process.destroyForcibly()
        }, timeoutMillis, TimeUnit.MILLISECONDS)
        try {
            process.inputStream.copyTo(sink)
            sink.flush()
        } catch (e: IOException) {
            process.destroyForcibly()
            watchdog.cancel(false)
            return StreamResult(CommandOutcome.NOT_RUN, "", false, "pipe closed: ${e.message}")
        }
        process.waitFor()
        watchdog.cancel(false)
        stderr.join()
        return StreamResult(process.exitValue(), stderr.text(), timedOut.get(), null)
    }

    private fun start(argv: List<String>): Process? = try {
        ProcessBuilder(argv).start().also { it.outputStream.close() }
    } catch (_: IOException) {
        null
    }

    /** Reads a stream on its own thread so stdout and stderr cannot block each other. */
    private class Drain(input: InputStream, private val cap: Int) {
        private val buffer = ByteArrayOutputStream()
        private var truncated = false
        private val thread = Thread {
            val chunk = ByteArray(CHUNK)
            try {
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    val room = cap - buffer.size()
                    if (room > 0) buffer.write(chunk, 0, minOf(n, room))
                    if (n > room) truncated = true
                }
            } catch (_: IOException) {
                // The process was killed; keep what was read.
            }
        }.apply {
            isDaemon = true
            start()
        }

        fun join() = thread.join(JOIN_MILLIS)

        fun text(): String = buffer.toString(Charsets.UTF_8) + if (truncated) "\n[truncated]" else ""
    }

    private companion object {
        const val OUTPUT_CAP = 64 * 1024
        const val STDERR_CAP = 4 * 1024
        const val CHUNK = 8 * 1024
        const val JOIN_MILLIS = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
        val WATCHDOG: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "worker-watchdog").apply { isDaemon = true }
        }
    }
}
