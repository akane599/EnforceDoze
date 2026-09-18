package com.akylas.enforcedoze;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bounded process lifetime and output, with simultaneous draining to prevent pipe deadlocks. */
public final class ProcessRunner {
    private static final int LIMIT = 65536;

    private ProcessRunner() {}

    public static CommandResult run(String[] argv, long timeoutMillis) {
        Process process = null;
        try {
            process = new ProcessBuilder(argv).redirectErrorStream(true).start();
            process.getOutputStream().close();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            InputStream stream = process.getInputStream();
            Thread reader =
                    new Thread(
                            () -> {
                                try (InputStream input = stream) {
                                    byte[] buffer = new byte[4096];
                                    int count;
                                    while ((count = input.read(buffer)) != -1) {
                                        synchronized (bytes) {
                                            if (bytes.size() < LIMIT)
                                                bytes.write(
                                                        buffer,
                                                        0,
                                                        Math.min(count, LIMIT - bytes.size()));
                                        }
                                    }
                                } catch (Exception ignored) {
                                    /* destroyed process closes its stream */
                                }
                            },
                            "command-output");
            reader.setDaemon(true);
            reader.start();
            long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
            int code;
            while (true) {
                try {
                    code = process.exitValue();
                    break;
                } catch (IllegalThreadStateException running) {
                    if (System.nanoTime() >= deadline) {
                        process.destroy();
                        return new CommandResult(
                                -2, "Command timed out; outcome unknown. Restoration retained.");
                    }
                    Thread.sleep(20);
                }
            }
            reader.join(1000);
            synchronized (bytes) {
                return new CommandResult(
                        code, new String(bytes.toByteArray(), StandardCharsets.UTF_8).trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(-2, "Interrupted; outcome unknown. Restoration retained.");
        } catch (Exception e) {
            return new CommandResult(-1, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }
}
