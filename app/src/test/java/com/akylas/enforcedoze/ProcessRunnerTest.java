package com.akylas.enforcedoze;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProcessRunnerTest {
    @Test public void drainsLargeStderrWithoutPipeDeadlock() {
        CommandResult result = ProcessRunner.run(4000, "sh", "-c", "i=0; while [ $i -lt 10000 ]; do echo errorline >&2; i=$((i+1)); done; echo done");
        assertEquals(0, result.exitCode); assertTrue(result.output.contains("done"));
    }
    @Test public void exitCodeSurvivesEmptyOutput() { assertEquals(7, ProcessRunner.run(2000, "sh", "-c", "exit 7").exitCode); }
    @Test public void timesOutAndBoundsOutput() {
        long start = System.nanoTime();
        CommandResult result = ProcessRunner.run(100, "sh", "-c", "while :; do echo output; done");
        assertEquals(124, result.exitCode);
        assertTrue(result.output.length() <= 131200);
        assertTrue((System.nanoTime() - start) / 1000000 < 3000);
    }
    @Test public void waitsForExitWithoutPolling() throws Exception {
        BlockingProcess process = new BlockingProcess();
        AtomicReference<CommandResult> result = new AtomicReference<>();
        Thread caller = new Thread(() -> result.set(ProcessRunner.collect(process, 2000)));
        caller.start();
        assertTrue(process.waiting.await(1, TimeUnit.SECONDS));
        process.finished.countDown();
        caller.join(2000);
        assertFalse("Command collector did not finish", caller.isAlive());
        assertEquals(7, result.get().exitCode);
        assertEquals(0, process.exitPolls);
        assertTrue(process.destroyed);
    }
    @Test public void interruptionDestroysProcessAndPreservesInterruptFlag() throws Exception {
        BlockingProcess process = new BlockingProcess();
        AtomicReference<CommandResult> result = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            result.set(ProcessRunner.collect(process, 5000));
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        caller.start();
        assertTrue(process.waiting.await(1, TimeUnit.SECONDS));
        caller.interrupt();
        caller.join(2000);
        assertFalse("Interrupted command did not finish", caller.isAlive());
        assertEquals(130, result.get().exitCode);
        assertEquals(Boolean.TRUE, interrupted.get());
        assertTrue(process.destroyed);
    }
    @Test public void invalidTimeoutNeverStartsACommand() {
        assertEquals(-1, ProcessRunner.run(0, "sh", "-c", "exit 0").exitCode);
        assertEquals(-1, ProcessRunner.run(-1, "sh", "-c", "exit 0").exitCode);
    }
    private static final class BlockingProcess extends Process {
        final CountDownLatch waiting = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        volatile boolean destroyed;
        volatile int exitPolls;
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() throws InterruptedException { waiting.countDown(); finished.await(); return 7; }
        @Override public int exitValue() { exitPolls++; throw new IllegalThreadStateException("Still running"); }
        @Override public void destroy() { destroyed = true; finished.countDown(); }
    }
}
