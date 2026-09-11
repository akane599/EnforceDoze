package com.akylas.enforcedoze;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Drain output while waiting, cap Binder payloads, and kill timed out processes. */
public final class ProcessRunner {
    private static final int OUTPUT_LIMIT = 128 * 1024;
    private ProcessRunner() {}
    public static CommandResult run(long timeoutMs, String... command) {
        Process process = null;
        Thread reader = null;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            final InputStream input = process.getInputStream();
            reader = new Thread(() -> {
                byte[] buffer = new byte[4096];
                try {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        synchronized (output) {
                            int remaining = OUTPUT_LIMIT - output.size();
                            if (remaining > 0) output.write(buffer, 0, Math.min(count, remaining));
                        }
                    }
                } catch (java.io.IOException ignored) { /* stream closed during timeout */ }
            }, "command-output");
            reader.setDaemon(true);
            reader.start();
            // Polling also works on API 23, before Process.waitFor(timeout) existed.
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            Integer code = null;
            while (code == null && System.nanoTime() < deadline) {
                try { code = process.exitValue(); }
                catch (IllegalThreadStateException running) { Thread.sleep(20); }
            }
            if (code == null) process.destroy();
            reader.join(500);
            synchronized (output) {
                return new CommandResult(code == null ? 124 : code,
                        output.toString(StandardCharsets.UTF_8.name()) + (code == null ? "\nCommand timed out" : ""));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(130, "Command interrupted");
        } catch (Exception e) {
            return new CommandResult(-1, e.toString());
        } finally {
            if (process != null) {
                process.destroy();
                try { process.getInputStream().close(); } catch (Exception ignored) { }
            }
        }
    }
}
