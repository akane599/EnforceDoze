package com.akylas.enforcedoze;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Drain output while waiting, cap Binder payloads, and kill timed out processes. */
public final class ProcessRunner {
    private static final int OUTPUT_LIMIT = 128 * 1024;
    private ProcessRunner() {}
    public static CommandResult run(long timeoutMs, String... command) {
        if (Thread.currentThread().isInterrupted()) return new CommandResult(130, "Command interrupted");
        if (timeoutMs <= 0 || command == null || command.length == 0) return new CommandResult(-1, "Invalid command or timeout");
        try {
            return collect(new ProcessBuilder(command).redirectErrorStream(true).start(), timeoutMs);
        } catch (Exception e) {
            return new CommandResult(-1, e.toString());
        }
    }

    static CommandResult collect(Process process, long timeoutMs) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CountDownLatch finished = new CountDownLatch(1);
        AtomicInteger exitCode = new AtomicInteger(130);
        Thread waiter = null;
        try {
            process.getOutputStream().close();
            final InputStream input = process.getInputStream();
            Thread reader = new Thread(() -> {
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
            // Blocking waitFor works on API 23 too. Do not wake the command thread
            // 50 times a second while a shell command or root prompt is running.
            waiter = new Thread(() -> {
                try { exitCode.set(process.waitFor()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { finished.countDown(); }
            }, "command-wait");
            waiter.setDaemon(true);
            waiter.start();
            boolean completed = finished.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!completed) process.destroy();
            reader.join(500);
            synchronized (output) {
                return new CommandResult(completed ? exitCode.get() : 124,
                        output.toString(StandardCharsets.UTF_8.name()) + (!completed ? "\nCommand timed out" : ""));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(130, "Command interrupted");
        } catch (Exception e) {
            return new CommandResult(-1, e.toString());
        } finally {
            process.destroy();
            if (waiter != null) waiter.interrupt();
            try { process.getInputStream().close(); } catch (Exception ignored) { }
            try { process.getErrorStream().close(); } catch (Exception ignored) { }
            try { process.getOutputStream().close(); } catch (Exception ignored) { }
        }
    }
}
