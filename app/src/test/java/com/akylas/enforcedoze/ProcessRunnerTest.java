package com.akylas.enforcedoze;

import static org.junit.Assert.*;

import org.junit.Test;

public class ProcessRunnerTest {
    @Test
    public void drainsBothStreamsAndReturnsFailure() {
        CommandResult r =
                ProcessRunner.run(
                        new String[] {"sh", "-c", "printf out; printf err >&2; exit 7"}, 2000);
        assertEquals(7, r.code);
        assertTrue(r.output.contains("out"));
        assertTrue(r.output.contains("err"));
    }

    @Test
    public void timeoutIsBoundedAndUncertain() {
        long start = System.nanoTime();
        CommandResult r = ProcessRunner.run(new String[] {"sh", "-c", "exec sleep 10"}, 150);
        assertEquals(-2, r.code);
        assertTrue((System.nanoTime() - start) / 1000000 < 2500);
    }

    @Test
    public void outputIsBoundedWithoutDeadlock() {
        CommandResult r =
                ProcessRunner.run(
                        new String[] {"sh", "-c", "head -c 1000000 /dev/zero | tr '\\000' x"},
                        2000);
        assertEquals(0, r.code);
        assertEquals(65536, r.output.length());
    }
}
